package com.safenest.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.FilterInputStream
import java.io.InterruptedIOException
import java.net.URI
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

data class CatalogConfiguration(val sourceUrl: String = "", val publicKeyPem: String = "")
data class CatalogStatus(
    val configured: Boolean,
    val revision: Long?,
    val expiresAt: String?,
    val domainCount: Int,
    val stale: Boolean,
    val lastError: String?,
    val sourceUrl: String,
    val keyFingerprint: String?,
    val trustChanged: Boolean,
    val issuedAt: String?,
    val lastCheckedAt: String?
)

/**
 * Explicit local import or HTTPS refresh. No live service/key ships with SafeNest.
 * Call configuration/import/refresh/status on IO when initializing this store.
 * The complete state (pin + signed snapshot + high-water revision) is atomically replaced.
 */
object CatalogStore {
    private const val STATE_VERSION = 1
    private const val MAX_STATE_BYTES = CatalogVerifier.MAX_ENVELOPE_BYTES + 32 * 1024
    private data class State(
        val configuration: CatalogConfiguration = CatalogConfiguration(),
        val keyFingerprint: String? = null,
        val highWaterRevision: Long = 0,
        val installedKey: String = "",
        val envelope: ByteArray? = null,
        val snapshot: CatalogVerifier.Snapshot? = null,
        val lastError: String? = null,
        val lastCheckedAt: String? = null,
        val loadFailed: Boolean = false
    )
    private val lock = Any()
    private var cached: State? = null
    private var cachedPath: String? = null
    private val downloadDeadlines = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "SafeNest-catalog-deadline").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    private fun file(context: Context) = AtomicFile(File(context.applicationContext.filesDir, "signed-domain-catalog-v1.json"))

    /** lock must be held. Failed/corrupt state is never silently overwritten by an update. */
    private fun current(context: Context): State {
        val path = file(context).baseFile.absolutePath
        if (cachedPath == path) return checkNotNull(cached)
        val loaded = try {
            val document = file(context).openRead().use { CatalogVerifier.readBounded(it, MAX_STATE_BYTES) }
            val root = JSONObject(document.toString(Charsets.UTF_8))
            require(root.getInt("formatVersion") == STATE_VERSION) { "Unsupported catalog storage version" }
            val configuration = validateConfiguration(root.getString("sourceUrl"), root.getString("publicKeyPem"))
            val fingerprint = CatalogVerifier.fingerprint(CatalogVerifier.parsePublicKey(configuration.publicKeyPem))
            val highWater = root.getLong("highWaterRevision")
            require(highWater >= 0) { "Invalid catalog revision state" }
            val raw = root.optString("envelope", "").takeIf { it.isNotEmpty() }?.toByteArray(Charsets.US_ASCII)
            val installedKey = root.optString("installedKey", "")
            val snapshot = raw?.let { CatalogVerifier.verifyStored(it, installedKey) }
            require((snapshot?.revision ?: 0) == highWater) { "Catalog revision state is inconsistent" }
            State(configuration, fingerprint, highWater, installedKey, raw, snapshot,
                lastCheckedAt = root.optString("lastCheckedAt", "").takeIf { it.isNotEmpty() })
        } catch (_: FileNotFoundException) {
            val base = file(context).baseFile
            val directory = base.parentFile
            // FNF also reports inaccessible files/directories. Only an accessible private directory
            // with no committed, backup or pending state counts as a pristine installation.
            val pristine = directory != null && directory.isDirectory && directory.canRead()
                && directory.canWrite() && directory.canExecute() && !base.exists()
                && !File(base.path + ".bak").exists() && !File(base.path + ".new").exists()
            if (!pristine) {
                State(lastError = "Saved catalog is unreadable. Starter and personal rules remain active. Publisher recovery is required before updating.", loadFailed = true)
            } else State()
        } catch (_: Exception) {
            State(lastError = "Saved catalog could not be verified. Starter and personal rules remain active. Publisher recovery is required before updating.", loadFailed = true)
        }
        cachedPath = path
        cached = loaded
        return loaded
    }

    private fun persist(context: Context, state: State) {
        val root = JSONObject().put("formatVersion", STATE_VERSION)
            .put("sourceUrl", state.configuration.sourceUrl).put("publicKeyPem", state.configuration.publicKeyPem)
            .put("highWaterRevision", state.highWaterRevision).put("installedKey", state.installedKey)
            .put("envelope", state.envelope?.toString(Charsets.US_ASCII) ?: "")
            .put("lastCheckedAt", state.lastCheckedAt ?: "")
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_STATE_BYTES) { "Catalog storage exceeds its limit" }
        val atomic = file(context)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
        // Memory changes only after AtomicFile has synced and installed the new disk state.
        cached = state
    }

    private fun validateConfiguration(rawUrl: String, rawKey: String): CatalogConfiguration {
        val key = CatalogVerifier.parsePublicKey(rawKey)
        val url = rawUrl.trim()
        require(url.length <= 2048) { "Catalog URL is too long" }
        if (url.isNotEmpty()) {
            val uri = URI(url)
            require(uri.scheme == "https" && uri.rawUserInfo == null && uri.rawFragment == null
                && uri.host != null && DomainRules.normalizeHostname(uri.host) != null
                && (uri.port == -1 || uri.port in 1..65535)) {
                "Use a public HTTPS catalog URL without credentials or a fragment"
            }
        }
        return CatalogConfiguration(url, CatalogVerifier.publicKeyPem(key))
    }

    fun configuration(context: Context): CatalogConfiguration = synchronized(lock) { current(context).configuration }

    /** Changing a pin preserves the previous verified snapshot until a higher revision verifies with the new pin. */
    fun configure(context: Context, sourceUrl: String, publicKeyPem: String): CatalogStatus {
        check(!ProtectionCommitment.isActive(context)) { "Publisher trust stays fixed during the active protection period." }
        val next = validateConfiguration(sourceUrl, publicKeyPem)
        synchronized(lock) {
            val previous = current(context)
            check(!previous.loadFailed) { previous.lastError ?: "Saved catalog cannot be verified" }
            persist(context, previous.copy(configuration = next,
                keyFingerprint = CatalogVerifier.fingerprint(CatalogVerifier.parsePublicKey(next.publicKeyPem)), lastError = null))
        }
        CatalogUpdateJob.schedule(context)
        return status(context)
    }

    /** Last-known-good catalog participates in blocking even when expired. UI must show stale status. */
    fun domains(context: Context, category: RuleCategory): Set<String> = synchronized(lock) {
        val snapshot = current(context).snapshot
        when (category) {
            RuleCategory.GAMBLING -> snapshot?.gambling ?: emptySet()
            RuleCategory.ADULT -> snapshot?.adult ?: emptySet()
            RuleCategory.PERSONAL -> emptySet()
        }
    }

    fun status(context: Context): CatalogStatus = synchronized(lock) {
        val state = current(context)
        val snapshot = state.snapshot
        CatalogStatus(state.configuration.publicKeyPem.isNotEmpty(), snapshot?.revision,
            snapshot?.expiresAt?.toString(), snapshot?.domainCount() ?: 0, snapshot?.isStale(Instant.now()) ?: false,
            state.lastError, state.configuration.sourceUrl, state.keyFingerprint,
            snapshot != null && snapshot.keyFingerprint != state.keyFingerprint,
            snapshot?.issuedAt?.toString(), state.lastCheckedAt)
    }

    /** Caller retains ownership of input. Failed verification or a failed write preserves all working rules. */
    fun importSigned(context: Context, input: InputStream): CatalogStatus {
        val configuration = configuration(context)
        return attempt(context) {
            check(configuration.publicKeyPem.isNotBlank()) { "Configure the publisher public key before importing" }
            val bytes = CatalogVerifier.readBounded(input, CatalogVerifier.MAX_ENVELOPE_BYTES)
            install(context, bytes, configuration)
        }
    }

    /** HTTPS uses Android's default certificate and hostname verification; redirects and compression are not accepted. */
    fun refresh(context: Context): CatalogStatus {
        val configuration = configuration(context)
        return attempt(context) {
            check(configuration.publicKeyPem.isNotBlank() && configuration.sourceUrl.isNotBlank()) { "Configure an HTTPS source and public key before refreshing" }
            val connection = URI(configuration.sourceUrl).toURL().openConnection() as HttpsURLConnection
            val timedOut = AtomicBoolean(false)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            // Per-read timeouts alone allow endless trickle downloads. Disconnect bounds the entire attempt.
            val timeoutTask = downloadDeadlines.schedule({ timedOut.set(true); connection.disconnect() }, 30, TimeUnit.SECONDS)
            fun checkDeadline() {
                if (timedOut.get() || System.nanoTime() >= deadline) throw InterruptedIOException("Catalog download timed out")
            }
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/octet-stream, text/plain")
                connection.setRequestProperty("Accept-Encoding", "identity")
                check(connection.responseCode == 200) { "Catalog server must return HTTP 200; redirects are not followed" }
                check(connection.contentEncoding == null || connection.contentEncoding.equals("identity", true)) { "Compressed catalog responses are not accepted" }
                check(connection.contentLengthLong <= CatalogVerifier.MAX_ENVELOPE_BYTES) { "Catalog response exceeds its size limit" }
                checkDeadline()
                val bytes = connection.inputStream.use { input ->
                    val boundedTime = object : FilterInputStream(input) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            checkDeadline()
                            val count = super.read(buffer, offset, length)
                            checkDeadline()
                            return count
                        }
                    }
                    CatalogVerifier.readBounded(boundedTime, CatalogVerifier.MAX_ENVELOPE_BYTES)
                }
                checkDeadline()
                install(context, bytes, configuration)
            } finally { timeoutTask.cancel(false); connection.disconnect() }
        }
    }

    private fun install(context: Context, bytes: ByteArray, requestedConfiguration: CatalogConfiguration) {
        // Parse expensive crypto away from the state lock. Commit rechecks revision and pin after races.
        val snapshot = CatalogVerifier.verifyUpdate(bytes, requestedConfiguration.publicKeyPem, 0, Instant.now())
        synchronized(lock) {
            val previous = current(context)
            check(!previous.loadFailed) { previous.lastError ?: "Saved catalog cannot be verified" }
            check(previous.configuration == requestedConfiguration) { "Catalog configuration changed; retry the update" }
            if (snapshot.revision <= previous.highWaterRevision) throw GeneralSecurityException("Catalog revision must increase; replay or rollback rejected")
            check(!snapshot.isStale(Instant.now())) { "Catalog expired before installation" }
            persist(context, previous.copy(highWaterRevision = snapshot.revision,
                installedKey = requestedConfiguration.publicKeyPem, envelope = bytes, snapshot = snapshot,
                lastError = null, lastCheckedAt = Instant.now().toString()))
        }
        // Never acquire RulesStore or call policy APIs while holding CatalogStore's lock.
        RulesStore.catalogChanged(context)
    }

    private fun attempt(context: Context, operation: () -> Unit): CatalogStatus {
        try { operation() }
        catch (failure: Exception) {
            synchronized(lock) {
                val old = current(context)
                val explanation = if (failure is GeneralSecurityException || failure is IllegalArgumentException || failure is IllegalStateException)
                    failure.message?.take(240) ?: "Catalog update rejected"
                else "Catalog download or storage failed. Check the network and available storage; the previous verified rules remain active."
                cached = old.copy(lastError = explanation, lastCheckedAt = Instant.now().toString())
            }
        }
        return status(context)
    }
}
