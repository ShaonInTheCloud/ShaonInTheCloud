package com.safenest.app;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** Raw DNS transport for an explicitly selected network. Never selects a public resolver. */
public final class DnsUpstreamTransport {
    private DnsUpstreamTransport() { }
    private static final ScheduledThreadPoolExecutor EXPIRY = expiryExecutor();
    private static ScheduledThreadPoolExecutor expiryExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "SafeNest-DNS-timeout");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /** Implementations must register before binding/protecting, and unregister on release. */
    public interface SocketHooks {
        void prepare(DatagramSocket socket) throws IOException;
        void prepare(Socket socket) throws IOException;
        void release(AutoCloseable socket);
    }

    /** Monotonic whole-operation deadline, including slow/trickled TCP responses. */
    public static final class Deadline {
        private final long expires;
        public Deadline(long timeoutMillis) {
            if (timeoutMillis < 1 || timeoutMillis > 60_000) throw new IllegalArgumentException("Invalid DNS deadline");
            expires = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }
        public int remainingMillis(int capMillis) throws SocketTimeoutException {
            long remaining = expires - System.nanoTime();
            if (remaining <= 0) throw new SocketTimeoutException("DNS deadline expired");
            return (int) Math.max(1, Math.min(capMillis, TimeUnit.NANOSECONDS.toMillis(remaining)));
        }
    }

    public static byte[] exchange(DnsPacketCodec.Query query, InetAddress resolver, int port,
            Deadline deadline, SocketHooks hooks) throws IOException {
        deadline.remainingMillis(4500); // Do not send stale queued work after its budget expired.
        byte[] reply;
        try {
            reply = udp(query, resolver, port, deadline, hooks);
        } catch (SocketTimeoutException timeout) {
            // TCP can still work on networks where UDP DNS is unavailable.
            return tcp(query, resolver, port, deadline, hooks);
        }
        return DnsPacketCodec.isTruncated(reply) ? tcp(query, resolver, port, deadline, hooks) : reply;
    }

    private static byte[] udp(DnsPacketCodec.Query query, InetAddress resolver, int port,
            Deadline deadline, SocketHooks hooks) throws IOException {
        DatagramSocket socket = new DatagramSocket(null);
        try {
            hooks.prepare(socket);
            socket.connect(resolver, port); // Kernel limits replies to the selected address/port.
            socket.send(new DatagramPacket(query.dns, query.dns.length));
            Deadline attempt = new Deadline(deadline.remainingMillis(1000));
            byte[] buffer = new byte[65535];
            while (true) {
                socket.setSoTimeout(Math.min(deadline.remainingMillis(1000), attempt.remainingMillis(1000)));
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                byte[] answer = java.util.Arrays.copyOf(buffer, response.getLength());
                if (DnsPacketCodec.matchesResponse(query, answer)) return answer;
                // An unrelated/invalid datagram does not consume the whole resolver attempt.
            }
        } finally {
            socket.close();
            hooks.release(socket);
        }
    }

    private static byte[] tcp(DnsPacketCodec.Query query, InetAddress resolver, int port,
            Deadline deadline, SocketHooks hooks) throws IOException {
        Socket socket = new Socket();
        ScheduledFuture<?> expiry = null;
        try {
            // Closing the descriptor also bounds connect/write stalls, not only socket reads.
            expiry = EXPIRY.schedule(() -> {
                try { socket.close(); } catch (IOException ignored) { }
            }, deadline.remainingMillis(2500), TimeUnit.MILLISECONDS);
            hooks.prepare(socket);
            socket.connect(new InetSocketAddress(resolver, port), deadline.remainingMillis(1000));
            // Queries fit a single normal socket send buffer; reads have a whole-response deadline.
            java.io.OutputStream output = socket.getOutputStream();
            output.write((query.dns.length >>> 8) & 255);
            output.write(query.dns.length & 255);
            output.write(query.dns);
            output.flush();
            Deadline attempt = new Deadline(deadline.remainingMillis(1500));
            InputStream input = socket.getInputStream();
            byte[] header = new byte[2];
            readFully(input, socket, header, deadline, attempt);
            int length = ((header[0] & 255) << 8) | (header[1] & 255);
            if (length < 12) throw new IOException("Invalid DNS TCP frame");
            byte[] response = new byte[length];
            readFully(input, socket, response, deadline, attempt);
            if (!DnsPacketCodec.matchesResponse(query, response) || DnsPacketCodec.isTruncated(response)) {
                throw new IOException("Invalid DNS TCP response");
            }
            return response;
        } finally {
            if (expiry != null) expiry.cancel(false);
            try { socket.close(); } finally { hooks.release(socket); }
        }
    }

    private static void readFully(InputStream input, Socket socket, byte[] destination,
            Deadline deadline, Deadline attempt) throws IOException {
        int at = 0;
        while (at < destination.length) {
            socket.setSoTimeout(Math.min(deadline.remainingMillis(1500), attempt.remainingMillis(1500)));
            int read = input.read(destination, at, destination.length - at);
            if (read < 0) throw new EOFException("Incomplete DNS TCP frame");
            if (read > 0) at += read;
        }
    }
}
