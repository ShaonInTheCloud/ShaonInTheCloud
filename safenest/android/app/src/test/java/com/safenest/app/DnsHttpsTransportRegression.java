package com.safenest.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Arrays;
import javax.net.ssl.HttpsURLConnection;

public final class DnsHttpsTransportRegression {
    private static int checks;
    private DnsHttpsTransportRegression() { }
    public static void main(String[] args) throws Exception { runAll(); }
    public static void runAll() throws Exception {
        checks = 0;
        byte[] request = new byte[]{0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
                7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0, 0, 1, 0, 1};
        DnsPacketCodec.Query query = DnsPacketCodec.parseQuery(request);
        require(query != null, "valid test question");
        byte[] reply = DnsPacketCodec.error(query, 3);
        Fake valid = new Fake(reply);
        Hooks hooks = new Hooks(valid);
        require(Arrays.equals(reply, exchange(query, hooks)), "wire response preserved");
        require(Arrays.equals(request, valid.body.toByteArray()), "raw question posted as body");
        require("POST".equals(valid.getRequestMethod()), "POST, no name in query string");
        require("application/dns-message".equals(valid.getRequestProperty("Content-Type")), "wire content type");
        require(!valid.getInstanceFollowRedirects() && !valid.getUseCaches(), "no redirects or cached replies");
        require(valid.getRequestProperty("Authorization") == null && valid.getRequestProperty("Cookie") == null, "no account credentials");
        require(hooks.prepared == 1 && hooks.released == 1 && valid.disconnected, "tracked connection released");
        for (int status : new int[]{201, 301, 302, 307, 308, 403, 429, 500}) {
            Fake response = new Fake(reply); response.status = status;
            reject(query, response, "HTTP status " + status);
            require(!response.inputOpened, "redirect/error body not processed");
        }
        for (String type : new String[]{null, "text/html", "application/dns-json"}) {
            Fake response = new Fake(reply); response.type = type; reject(query, response, "bad MIME");
        }
        Fake parameterized = new Fake(reply); parameterized.type = "Application/Dns-Message; charset=binary";
        require(Arrays.equals(reply, exchange(query, new Hooks(parameterized))), "MIME parameter and case allowed");
        Fake compressed = new Fake(reply); compressed.encoding = "gzip"; reject(query, compressed, "compressed body");
        Fake hugeHeader = new Fake(reply); hugeHeader.length = 65536; reject(query, hugeHeader, "oversized declared body");
        Fake hugeBody = new Fake(new byte[65536]); hugeBody.length = -1; reject(query, hugeBody, "oversized streaming body");
        Fake incomplete = new Fake(reply); incomplete.length = reply.length + 1; reject(query, incomplete, "incomplete body");
        reject(query, new Fake(new byte[11]), "short packet");
        byte[] wrongId = reply.clone(); wrongId[0] ^= 1; reject(query, new Fake(wrongId), "wrong DNS ID");
        byte[] wrongName = reply.clone(); wrongName[13] = 'z'; reject(query, new Fake(wrongName), "wrong DNS question");
        byte[] truncated = reply.clone(); truncated[2] |= 2; reject(query, new Fake(truncated), "truncated reply");
        Hooks expired = new Hooks(new Fake(reply));
        DnsUpstreamTransport.Deadline budget = new DnsUpstreamTransport.Deadline(1);
        Thread.sleep(10);
        try { DnsHttpsTransport.exchange(query, new URL("https://1.1.1.1/dns-query"), budget, expired); fail("expired deadline"); }
        catch (IOException expected) { require(expired.opened == 0, "expired work does not open connection"); }
        for (String url : new String[]{"http://1.1.1.1/dns-query", "https://user@1.1.1.1/dns-query",
                "https://1.1.1.1/dns-query?dns=secret", "https://1.1.1.1/dns-query#x", "https://1.1.1.1/"}) {
            Hooks invalid = new Hooks(new Fake(reply));
            try { DnsHttpsTransport.exchange(query, new URL(url), new DnsUpstreamTransport.Deadline(500), invalid); fail("bad endpoint"); }
            catch (IOException expected) { require(invalid.opened == 0, "bad endpoint rejected before opening"); }
        }
        Hooks cancelled = new Hooks(new Fake(reply)); cancelled.cancelOnPrepare = true;
        try { exchange(query, cancelled); fail("cancelled request"); }
        catch (IOException expected) { require(cancelled.released == 1 && cancelled.response.disconnected, "stop/expiry cleanup"); }
        Fake trickle = new Fake(reply); trickle.trickle = true;
        long started = System.nanoTime();
        try { DnsHttpsTransport.exchange(query, new URL("https://1.1.1.1/dns-query"),
                new DnsUpstreamTransport.Deadline(65), new Hooks(trickle)); fail("trickled deadline"); }
        catch (IOException expected) { require((System.nanoTime() - started) / 1_000_000 < 1000 && trickle.disconnected, "whole-body deadline"); }
        require(DnsHttpsTransport.endpoints(true)[0].contains("[2606:"), "IPv6 network preferred");
        require(DnsHttpsTransport.endpoints(false)[0].contains("1.1.1.1"), "IPv4 network preferred");
        for (String endpoint : DnsHttpsTransport.endpoints(false)) {
            URL url = new URL(endpoint);
            require("https".equals(url.getProtocol()) && !url.getHost().matches(".*[g-zG-Z].*"), "literal HTTPS bootstrap");
        }
        System.out.println("DNS-over-HTTPS regressions passed: " + checks + " checks");
    }
    private static byte[] exchange(DnsPacketCodec.Query query, Hooks hooks) throws IOException {
        return DnsHttpsTransport.exchange(query, new URL("https://1.1.1.1/dns-query"),
                new DnsUpstreamTransport.Deadline(500), hooks);
    }
    private static void reject(DnsPacketCodec.Query query, Fake response, String reason) throws IOException {
        Hooks hooks = new Hooks(response);
        try { exchange(query, hooks); fail(reason); }
        catch (IOException expected) { require(response.disconnected && hooks.released == 1, reason + " and cleanup"); }
    }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason); checks++;
    }
    private static void fail(String reason) { throw new AssertionError("Expected rejection: " + reason); }
    private static final class Hooks implements DnsHttpsTransport.ConnectionHooks {
        final Fake response; int opened; int prepared; int released; boolean cancelOnPrepare;
        Hooks(Fake response) { this.response = response; }
        @Override public URLConnection open(URL endpoint) { opened++; return response; }
        @Override public void prepare(AutoCloseable resource) throws IOException {
            prepared++;
            if (cancelOnPrepare) { response.disconnect(); throw new IOException("Stopped"); }
        }
        @Override public void release(AutoCloseable resource) { released++; }
    }
    private static final class Fake extends HttpsURLConnection {
        final byte[] bytes; final ByteArrayOutputStream body = new ByteArrayOutputStream();
        int status = 200; String type = "application/dns-message"; String encoding;
        long length; boolean disconnected; boolean inputOpened; boolean trickle;
        Fake(byte[] bytes) throws IOException { super(new URL("https://1.1.1.1/dns-query")); this.bytes = bytes; length = bytes.length; }
        @Override public void connect() { }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public java.io.OutputStream getOutputStream() throws IOException {
            if (disconnected) throw new IOException("Closed"); return body;
        }
        @Override public int getResponseCode() { return status; }
        @Override public String getContentType() { return type; }
        @Override public String getContentEncoding() { return encoding; }
        @Override public long getContentLengthLong() { return length; }
        @Override public InputStream getInputStream() {
            inputOpened = true;
            if (!trickle) return new ByteArrayInputStream(bytes);
            return new InputStream() {
                int at;
                @Override public int read() throws IOException {
                    try { Thread.sleep(25); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); throw new IOException(stop); }
                    if (disconnected) throw new IOException("Closed");
                    return at < bytes.length ? bytes[at++] & 255 : -1;
                }
                @Override public int read(byte[] target, int offset, int count) throws IOException {
                    int next = read(); if (next < 0) return -1; target[offset] = (byte) next; return 1;
                }
            };
        }
        @Override public String getCipherSuite() { return "TLS_TEST"; }
        @Override public Certificate[] getLocalCertificates() { return null; }
        @Override public Certificate[] getServerCertificates() { return new Certificate[0]; }
        @Override public Principal getPeerPrincipal() { return null; }
        @Override public Principal getLocalPrincipal() { return null; }
    }
}
