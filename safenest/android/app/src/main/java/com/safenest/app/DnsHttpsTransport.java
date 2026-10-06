package com.safenest.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

/** RFC 8484 wire-format POST. The caller opens HTTPS on the chosen physical network.
 * Uses platform certificate/hostname checks; never retries over plain DNS or follows redirects.
 */
public final class DnsHttpsTransport {
    private DnsHttpsTransport() { }
    private static final int MAX_REPLY = 65535;
    private static final ScheduledThreadPoolExecutor EXPIRY = expiryExecutor();

    private static ScheduledThreadPoolExecutor expiryExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2, task -> {
            Thread thread = new Thread(task, "SafeNest-DoH-timeout");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    public interface ConnectionHooks {
        URLConnection open(URL endpoint) throws IOException;
        void prepare(AutoCloseable cancellation) throws IOException;
        void release(AutoCloseable cancellation);
    }

    /** Literal IP endpoints avoid a circular or unencrypted bootstrap name lookup.
     * Platform TLS checks must validate the IP against the resolver's certificate.
     */
    public static String[] endpoints(boolean ipv6First) {
        String[] ipv4 = {"https://1.1.1.1/dns-query", "https://1.0.0.1/dns-query"};
        String[] ipv6 = {"https://[2606:4700:4700::1111]/dns-query", "https://[2606:4700:4700::1001]/dns-query"};
        return ipv6First ? new String[]{ipv6[0], ipv6[1], ipv4[0], ipv4[1]}
                : new String[]{ipv4[0], ipv4[1], ipv6[0], ipv6[1]};
    }

    public static byte[] exchange(DnsPacketCodec.Query query, URL endpoint,
            DnsUpstreamTransport.Deadline deadline, ConnectionHooks hooks) throws IOException {
        if (!"https".equals(endpoint.getProtocol()) || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getRef() != null
                || !"/dns-query".equals(endpoint.getPath())) {
            throw new IOException("Invalid encrypted DNS endpoint");
        }
        deadline.remainingMillis(2200); // Includes queue time; do not send stale work.
        URLConnection opened = hooks.open(endpoint);
        if (!(opened instanceof HttpsURLConnection)) throw new IOException("HTTPS is required for DNS");
        HttpsURLConnection connection = (HttpsURLConnection) opened;
        AutoCloseable cancellation = connection::disconnect;
        ScheduledFuture<?> expiry = null;
        try {
            hooks.prepare(cancellation);
            DnsUpstreamTransport.Deadline attempt = new DnsUpstreamTransport.Deadline(deadline.remainingMillis(2200));
            connection.setConnectTimeout(attempt.remainingMillis(1800));
            connection.setReadTimeout(attempt.remainingMillis(1800));
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/dns-message");
            connection.setRequestProperty("Content-Type", "application/dns-message");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Cache-Control", "no-store");
            connection.setRequestProperty("User-Agent", "SafeNest-DNS/1");
            connection.setFixedLengthStreamingMode(query.dns.length);
            expiry = EXPIRY.schedule(connection::disconnect, attempt.remainingMillis(2200), TimeUnit.MILLISECONDS);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(query.dns); }
            if (connection.getResponseCode() != 200) throw new IOException("Encrypted DNS endpoint did not return success");
            String type = connection.getContentType();
            if (type == null || !"application/dns-message".equals(type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))) {
                throw new IOException("Invalid encrypted DNS content type");
            }
            String encoding = connection.getContentEncoding();
            if (encoding != null && !"identity".equalsIgnoreCase(encoding)) throw new IOException("Encoded DNS response rejected");
            long length = connection.getContentLengthLong();
            if (length > MAX_REPLY || (length >= 0 && length < 12)) throw new IOException("Invalid encrypted DNS length");
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                while (true) {
                    deadline.remainingMillis(2200);
                    attempt.remainingMillis(2200);
                    int read = input.read(buffer);
                    if (read < 0) break;
                    if (read == 0) continue;
                    if (result.size() + read > MAX_REPLY) throw new IOException("Encrypted DNS reply too large");
                    result.write(buffer, 0, read);
                }
            }
            byte[] answer = result.toByteArray();
            if ((length >= 0 && answer.length != length) || !DnsPacketCodec.matchesResponse(query, answer)
                    || DnsPacketCodec.isTruncated(answer)) throw new IOException("Invalid encrypted DNS response");
            return answer;
        } finally {
            if (expiry != null) expiry.cancel(false);
            try { connection.disconnect(); } finally { hooks.release(cancellation); }
        }
    }
}
