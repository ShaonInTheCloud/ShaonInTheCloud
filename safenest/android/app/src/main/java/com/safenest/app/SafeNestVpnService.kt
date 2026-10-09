package com.safenest.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.DnsResolver
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.CancellationSignal
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import java.io.IOException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.Proxy
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** User-consented DNS filter. Ordinary web traffic stays on Android's physical network. */
class SafeNestVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.safenest.app.START"
        const val ACTION_STOP = "com.safenest.app.STOP"
        private const val CHANNEL = "safenest_protection"
        private const val NOTIFICATION_ID = 2401
        private const val DNS_IP = "10.91.0.53"
        private val DNS_ADDRESS = byteArrayOf(10, 91, 0, 53)
        private const val TAG = "SafeNestDns"
        val isRunning = AtomicBoolean(false)
        val dnsHealth = AtomicReference("waiting")
        val privateDnsState = AtomicReference("unknown")
        val dnsTransport = AtomicReference("none")
        val lockdownEnabled = AtomicBoolean(false)
        val alwaysOnEnabled = AtomicBoolean(false)
        val lastError = AtomicReference("")
        /** Volatile on-device diagnostic, cleared on service start; never uploaded or persisted. */
        val lastBlockedHost = AtomicReference("")
        val lastBlockedAt = AtomicLong(0L)
    }

    private class Session(val tun: ParcelFileDescriptor) {
        val alive = AtomicBoolean(true)
        private val closed = AtomicBoolean(false)
        val output = FileOutputStream(tun.fileDescriptor)
        val writeLock = Any()
        private val threadNumber = AtomicInteger(0)
        private val resources = ConcurrentHashMap.newKeySet<AutoCloseable>()
        fun track(resource: AutoCloseable) {
            resources.add(resource)
            if (!alive.get()) {
                resources.remove(resource)
                try { resource.close() } catch (_: Exception) { }
                throw IOException("DNS session stopped")
            }
        }
        fun untrack(resource: AutoCloseable) { resources.remove(resource) }
        val requests = ThreadPoolExecutor(8, 8, 0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(64), { task ->
                Thread(task, "SafeNest-DNS-${threadNumber.incrementAndGet()}").apply { isDaemon = true }
            }, ThreadPoolExecutor.AbortPolicy())
        var reader: Thread? = null
        fun close() {
            alive.set(false)
            if (closed.getAndSet(true)) return
            requests.shutdownNow()
            resources.forEach { try { it.close() } catch (_: Exception) { } }
            resources.clear()
            try { tun.close() } catch (_: Exception) { }
            reader?.interrupt()
        }
    }

    @Volatile private var session: Session? = null
    @Volatile private var reportedUnderlying: Network? = null
    private val failedQueries = AtomicInteger(0)
    private val main = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val statusPoll = object : Runnable {
        override fun run() {
            ProtectionCommitment.checkpoint(this@SafeNestVpnService)
            if (!ProtectionCommitment.isActive(this@SafeNestVpnService)) {
                stopProtection("Your paid protection period ended. Verify an active subscription to start again.")
                return
            }
            // OEMs may leave a service/notification alive briefly after another VPN wins.
            // Do not report protection if Android no longer grants this app VPN ownership.
            if (session != null && VpnService.prepare(this@SafeNestVpnService) != null) {
                stopProtection("Another VPN replaced SafeNest or its VPN permission was revoked. Websites are not filtered; reopen SafeNest and restore protection.")
                return
            }
            samplePlatformStatus()
            if (session != null) {
                refreshUnderlying()
                main.postDelayed(this, 2000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ProtectionCommitment.isActive(this)) {
            ProtectionCommitment.expireAsync(this)
            stopProtection(if (LocalTestSession.enabled) "Start a new local test session in SafeNest Test."
                else "Protection requires an activated, verified paid subscription.")
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            // Recheck at execution time; hiding the UI action alone is insufficient.
            // Android revocation/fatal-error cleanup remains separate and must work.
            if (ProtectionCommitment.isActive(this) || ManagedProtection.isConfigured(this)) {
                lastError.set("Your protection commitment is active until its verified expiry.")
                startProtection()
                return START_STICKY
            }
            stopProtection()
            return START_NOT_STICKY
        }
        startProtection()
        return START_STICKY
    }

    @Synchronized private fun startProtection() {
        if (session?.alive?.get() == true) return
        if (session != null) disposeSession()
        isRunning.set(false)
        dnsHealth.set("waiting")
        dnsTransport.set("none")
        lastError.set("")
        failedQueries.set(0)
        lastBlockedHost.set("")
        lastBlockedAt.set(0L)
        try {
            startForeground(NOTIFICATION_ID, notification())
            val initialNetwork = physicalNetwork()
            val initialProperties = linkProperties(initialNetwork)
            updatePrivateDnsState(initialProperties)
            if (Build.VERSION.SDK_INT == 28 && requiresStrictPrivateDns(initialProperties)) {
                stopProtection("Android 9 encrypted Private DNS is not supported by this filter. Use Android 10 or newer, or review Private DNS in Android Settings. Internet access has been restored.")
                return
            }
            val descriptor = Builder()
                .setSession(if (LocalTestSession.enabled) "SafeNest Test local DNS filter" else "SafeNest local DNS filter")
                .setMtu(1500)
                .setBlocking(true)
                .setUnderlyingNetworks(initialNetwork?.let { arrayOf(it) })
                .addAddress("10.91.0.2", 32)
                .addDnsServer(DNS_IP)
                .allowFamily(OsConstants.AF_INET6)
                // A default route here would black-hole all internet: this is DNS only.
                .addRoute(DNS_IP, 32)
                .establish() ?: throw IllegalStateException("VPN consent is no longer available")
            val current = Session(descriptor)
            session = current
            reportedUnderlying = initialNetwork
            isRunning.set(true)
            runCatching { ProtectionAlerts.resumed(this) }
            getSharedPreferences("safenest_app", MODE_PRIVATE).edit().putBoolean("protection_on", true).apply()
            samplePlatformStatus()
            registerNetworkCallback()
            main.removeCallbacks(statusPoll)
            main.post(statusPoll)
            current.reader = Thread({ packetLoop(current) }, "SafeNest-DNS-reader").apply { isDaemon = true; start() }
            Log.i(TAG, "DNS interface established; browser traffic uses normal routes")
        } catch (error: Exception) {
            Log.e(TAG, "VPN startup failed: ${error.javaClass.simpleName}")
            stopProtection("VPN could not start. Reopen SafeNest and grant VPN permission.")
        }
    }

    private fun samplePlatformStatus() {
        val lockdown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isLockdownEnabled
        val alwaysOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAlwaysOn
        val changed = lockdownEnabled.getAndSet(lockdown) != lockdown
        alwaysOnEnabled.set(alwaysOn)
        if (lockdown) {
            dnsHealth.set("failed")
            lastError.set("Turn off Android's Block connections without VPN. This DNS filter does not carry web traffic.")
        } else if (changed) {
            dnsHealth.set("waiting")
            lastError.set("")
        }
    }

    private fun packetLoop(current: Session) {
        val input = FileInputStream(current.tun.fileDescriptor)
        val pollFd = StructPollfd().apply { fd = current.tun.fileDescriptor; events = OsConstants.POLLIN.toShort() }
        val buffer = ByteArray(65535)
        try {
            while (current.alive.get()) {
                // Poll has a finite timeout so a stopped session never spins or waits forever.
                if (Os.poll(arrayOf(pollFd), 500) == 0) continue
                if (!current.alive.get()) break
                if (pollFd.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) {
                    throw IllegalStateException("VPN interface closed")
                }
                if (pollFd.revents.toInt() and OsConstants.POLLIN == 0) continue
                val size = input.read(buffer)
                if (size < 0) throw IllegalStateException("VPN interface reached EOF")
                if (size == 0) continue
                val request = DnsPacketCodec.parseRequest(buffer.copyOf(size), DNS_ADDRESS) ?: continue
                // Blocked lookups never wait behind slow allowed-name queries.
                if (RulesStore.isBlocked(this, request.query.hostname)) {
                    noteBlocked(request.query.hostname)
                    writeReply(current, request, DnsPacketCodec.error(request.query, 3))
                    continue
                }
                try {
                    val deadline = DnsUpstreamTransport.Deadline(4500)
                    current.requests.execute {
                        if (!isCurrent(current)) return@execute
                        try { writeReply(current, request, resolveAllowed(current, request.query, deadline)) }
                        catch (error: Exception) {
                            if (isCurrent(current)) {
                                recordFailure(current, "DNS query failed; check the physical network.")
                                Log.w(TAG, "DNS request failed: ${error.javaClass.simpleName}")
                                writeReply(current, request, DnsPacketCodec.error(request.query, 2))
                            }
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    if (isCurrent(current)) {
                        recordFailure(current, "DNS requests are busy. Retry after a few seconds.")
                        writeReply(current, request, DnsPacketCodec.error(request.query, 2))
                    }
                }
            }
        } catch (error: Exception) {
            if (isCurrent(current)) {
                Log.e(TAG, "VPN packet reader stopped: ${error.javaClass.simpleName}")
                failSession(current, "DNS interface stopped. Reopen SafeNest to restart it.")
            }
        }
    }

    private fun isCurrent(current: Session) = current.alive.get() && session === current

    private fun writeReply(current: Session, request: DnsPacketCodec.Request, dns: ByteArray) {
        if (!isCurrent(current)) return
        try {
            val packet = DnsPacketCodec.ipv4Reply(request, dns)
            synchronized(current.writeLock) { if (isCurrent(current)) current.output.write(packet) }
        } catch (error: Exception) {
            if (isCurrent(current)) {
                Log.e(TAG, "VPN response write failed: ${error.javaClass.simpleName}")
                failSession(current, "DNS interface stopped. Reopen SafeNest to restart it.")
            }
        }
    }

    @Synchronized private fun failSession(current: Session, reason: String) {
        if (session !== current) return
        current.close()
        // onDestroy/stop owns disposal on the main thread; identity prevents an old
        // request from stopping a newly established tunnel.
        main.post { if (session === current) stopProtection(reason) }
        isRunning.set(false)
        dnsHealth.set("failed")
        lastError.set(reason)
    }

    private fun physicalNetwork(): Network? {
        val connectivity = requireNotNull(getSystemService(ConnectivityManager::class.java))
        return connectivity.allNetworks.filter { network ->
            val caps = connectivity.getNetworkCapabilities(network)
            caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.sortedByDescending { network ->
            val caps = connectivity.getNetworkCapabilities(network)
            (if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) 4 else 0) +
                (if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) 2 else 0)
        }.firstOrNull()
    }

    private fun refreshUnderlying(): Network? {
        val selected = physicalNetwork()
        val properties = linkProperties(selected)
        updatePrivateDnsState(properties)
        if (session != null && Build.VERSION.SDK_INT == 28 && requiresStrictPrivateDns(properties)) {
            stopProtection("Android 9 encrypted Private DNS needs Android 10 or newer for this filter. Review Private DNS in Android Settings. Internet access has been restored.")
            return selected
        }
        if (session != null && selected != reportedUnderlying) {
            try {
                setUnderlyingNetworks(selected?.let { arrayOf(it) })
                reportedUnderlying = selected
                dnsHealth.set(if (selected == null || lockdownEnabled.get()) "failed" else "waiting")
                if (selected == null) lastError.set("No Wi-Fi or mobile network is available.")
                else if (!lockdownEnabled.get()) lastError.set("")
            } catch (error: Exception) { Log.w(TAG, "Network update failed: ${error.javaClass.simpleName}") }
        }
        return selected
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { refreshUnderlying() } }
            override fun onLost(network: Network) { main.post { refreshUnderlying() } }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { main.post { refreshUnderlying() } }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) { main.post { refreshUnderlying() } }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        try {
            requireNotNull(getSystemService(ConnectivityManager::class.java)).registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (error: Exception) { Log.w(TAG, "Network listener unavailable: ${error.javaClass.simpleName}") }
    }

    private fun linkProperties(network: Network?): LinkProperties? = network?.let {
        getSystemService(ConnectivityManager::class.java)?.getLinkProperties(it)
    }

    private fun requiresStrictPrivateDns(properties: LinkProperties?): Boolean =
        Build.VERSION.SDK_INT >= 28 && properties != null &&
            !properties.privateDnsServerName.isNullOrEmpty()

    private fun updatePrivateDnsState(properties: LinkProperties?) {
        val value = when {
            properties == null -> "unknown"
            Build.VERSION.SDK_INT < 28 -> "off"
            !properties.privateDnsServerName.isNullOrEmpty() ->
                if (properties.isPrivateDnsActive) "strict-active" else "strict-unvalidated"
            properties.isPrivateDnsActive -> "automatic-active"
            else -> "off"
        }
        privateDnsState.set(value)
    }

    private fun resolveAllowed(current: Session, query: DnsPacketCodec.Query,
            deadline: DnsUpstreamTransport.Deadline): ByteArray {
        val network = physicalNetwork()
        if (network == null || !isCurrent(current)) {
            recordFailure(current, "No Wi-Fi or mobile network is available.")
            return DnsPacketCodec.error(query, 2)
        }
        val properties = linkProperties(network)
        updatePrivateDnsState(properties)
        // Honor an explicitly selected strict provider. Android's strict mode cannot
        // downgrade to plain DNS; opportunistic/automatic mode does not give that guarantee.
        if (requiresStrictPrivateDns(properties)) {
            dnsTransport.set("strict-private-dns")
            if (Build.VERSION.SDK_INT >= 29 && properties?.isPrivateDnsActive == true) {
                val answer = platformResolve(current, query, network, deadline)
                if (answer != null && requiresStrictPrivateDns(linkProperties(network))) {
                    return checkedReply(current, query, answer)
                }
            }
            recordFailure(current, "Your strict Private DNS provider did not respond. No plain DNS or alternate provider was used.")
            return DnsPacketCodec.error(query, 2)
        }
        dnsTransport.set("cloudflare-https")
        val hooks = object : DnsHttpsTransport.ConnectionHooks {
            override fun open(endpoint: URL) = network.openConnection(endpoint, Proxy.NO_PROXY)
            override fun prepare(cancellation: AutoCloseable) { current.track(cancellation) }
            override fun release(cancellation: AutoCloseable) { current.untrack(cancellation) }
        }
        val ipv6First = properties != null && properties.linkAddresses.none { it.address is Inet4Address }
        // A shared monotonic budget bounds queued work, connection, reading and all HTTPS attempts.
        for (endpoint in DnsHttpsTransport.endpoints(ipv6First)) {
            if (!isCurrent(current)) return DnsPacketCodec.error(query, 2)
            // A newly selected strict provider must not silently send its lookups elsewhere.
            if (requiresStrictPrivateDns(linkProperties(network))) break
            try {
                val answer = DnsHttpsTransport.exchange(query, URL(endpoint), deadline, hooks)
                return checkedReply(current, query, answer)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return DnsPacketCodec.error(query, 2)
            } catch (error: Exception) {
                if (!isCurrent(current)) return DnsPacketCodec.error(query, 2)
                Log.w(TAG, "Encrypted DNS resolver failed: ${error.javaClass.simpleName}")
            }
        }
        recordFailure(current, "Encrypted DNS failed on the selected network. Check connection and Private DNS; no plain DNS fallback was sent.")
        return DnsPacketCodec.error(query, 2)
    }

    @android.annotation.TargetApi(29)
    private fun platformResolve(current: Session, query: DnsPacketCodec.Query, network: Network,
            deadline: DnsUpstreamTransport.Deadline): ByteArray? {
        val cancellation = CancellationSignal()
        val resource = AutoCloseable { cancellation.cancel() }
        val result = AtomicReference<ByteArray?>(null)
        val completed = CountDownLatch(1)
        return try {
            deadline.remainingMillis(1500) // Expired queued work must not start a new lookup.
            current.track(resource)
            DnsResolver.getInstance().rawQuery(network, query.dns, DnsResolver.FLAG_EMPTY,
                Executor { it.run() }, cancellation, object : DnsResolver.Callback<ByteArray> {
                    override fun onAnswer(answer: ByteArray, rcode: Int) {
                        if (!cancellation.isCanceled && isCurrent(current) && DnsPacketCodec.matchesResponse(query, answer)) {
                            result.set(answer)
                        }
                        completed.countDown()
                    }
                    override fun onError(error: DnsResolver.DnsException) { completed.countDown() }
                })
            if (completed.await(deadline.remainingMillis(1500).toLong(), TimeUnit.MILLISECONDS)) result.get() else null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (error: Exception) {
            if (isCurrent(current)) Log.w(TAG, "Android resolver failed: ${error.javaClass.simpleName}")
            null
        } finally {
            cancellation.cancel()
            current.untrack(resource)
        }
    }

    private fun checkedReply(current: Session, query: DnsPacketCodec.Query, answer: ByteArray): ByteArray {
        val aliases = DnsAliasInspector.inspect(query, answer)
        if (!aliases.valid) {
            recordFailure(current, "DNS returned an invalid response.")
            return DnsPacketCodec.error(query, 2)
        }
        // Lists may change while a query is in flight; check the original and reachable alias chain.
        if (RulesStore.isBlocked(this, query.hostname) || aliases.targets.any { RulesStore.isBlocked(this, it) }) {
            noteBlocked(query.hostname)
            return DnsPacketCodec.error(query, 3)
        }
        recordReplyHealth(current, answer)
        // Preserve TTLs, CNAMEs, DNSSEC and HTTPS/SVCB data. writeReply handles UDP sizing.
        return answer
    }

    private fun noteBlocked(host: String) {
        lastBlockedHost.set(host)
        lastBlockedAt.set(System.currentTimeMillis())
    }

    private fun recordSuccess(current: Session) {
        if (!isCurrent(current) || lockdownEnabled.get()) return
        failedQueries.set(0)
        dnsHealth.set("ok")
        lastError.set("")
    }

    private fun recordReplyHealth(current: Session, reply: ByteArray) {
        if (DnsPacketCodec.rcode(reply) == 0 || DnsPacketCodec.rcode(reply) == 3) recordSuccess(current)
        else recordFailure(current, "The upstream DNS server returned an error.")
    }

    private fun recordFailure(current: Session, message: String) {
        if (!isCurrent(current) || lockdownEnabled.get()) return
        dnsHealth.set("failed")
        lastError.set(message)
        if (failedQueries.incrementAndGet() <= 3) Log.e(TAG, message)
    }

    private fun notification(): Notification {
        requireNotNull(getSystemService(NotificationManager::class.java)).createNotificationChannel(
            NotificationChannel(CHANNEL, "SafeNest protection", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent().setClassName(this, "com.safenest.app.MainActivity"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("SafeNest DNS filter")
            .setContentText("Open SafeNest to check connection and protection status")
            .setOngoing(true).setContentIntent(open).build()
    }

    @Synchronized private fun stopProtection(error: String? = null) {
        disposeSession()
        dnsHealth.set(if (error == null) "waiting" else "failed")
        lastError.set(error ?: "")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Synchronized private fun disposeSession() {
        val old = session
        session = null
        old?.close()
        isRunning.set(false)
        dnsTransport.set("none")
        dnsTransport.set("none")
        reportedUnderlying = null
        main.removeCallbacks(statusPoll)
        networkCallback?.let { callback ->
            try { requireNotNull(getSystemService(ConnectivityManager::class.java)).unregisterNetworkCallback(callback) } catch (_: Exception) { }
        }
        networkCallback = null
        getSharedPreferences("safenest_app", MODE_PRIVATE).edit().putBoolean("protection_on", false).apply()
    }

    override fun onRevoke() {
        stopProtection("SafeNest's VPN permission was revoked or another VPN replaced it.")
        // During a paid period this is the WARP / other-VPN takeover case: alert immediately.
        runCatching { ProtectionAlerts.check(this) }
        super.onRevoke()
    }
    override fun onDestroy() {
        val wasRunning = session != null
        disposeSession()
        if (wasRunning) {
            dnsHealth.set("failed")
            lastError.set("Android stopped the DNS service. Reopen SafeNest to check protection.")
            runCatching { ProtectionAlerts.check(this) }
        }
        super.onDestroy()
    }
}
