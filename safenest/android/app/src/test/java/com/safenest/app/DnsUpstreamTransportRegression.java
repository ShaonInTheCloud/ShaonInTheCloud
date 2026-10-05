package com.safenest.app;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Real loopback UDP/TCP tests; no Internet, Android API, mocked DNS, or credentials required. */
public final class DnsUpstreamTransportRegression {
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static int checks;
    private DnsUpstreamTransportRegression() { }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("DNS upstream regression passed: " + checks + " assertions using real loopback UDP/TCP sockets.");
    }

    public static void runAll() throws Exception {
        checks = 0;
        udpIgnoresInvalidReplyAndPreservesBytes();
        authoritativeNxDomainIsReturned();
        truncationUsesTcp();
        malformedTcpFrameFails();
        deadlineBoundsUnresponsiveResolver();
        expiredQueueBudgetSendsNothing();
        trickledTcpCannotExtendDeadline();
        cancellationClosesTransport();
    }

    private static void udpIgnoresInvalidReplyAndPreservesBytes() throws Exception {
        DnsPacketCodec.Query query = query();
        byte[] answer = DnsPacketCodec.addresses(query, java.util.Collections.singletonList(new byte[]{(byte)192, 0, 2, 1}));
        // Non-default TTL verifies byte preservation rather than address reconstruction.
        answer[query.questionEnd + 9] = 17;
        Hooks hooks = new Hooks();
        try (DatagramSocket server = new DatagramSocket(0, LOOPBACK)) {
            FutureTask<Void> task = start(() -> {
                DatagramPacket request = receive(server);
                byte[] wrong = answer.clone(); wrong[1] ^= 1;
                respond(server, request, wrong);
                respond(server, request, answer);
            });
            byte[] actual = DnsUpstreamTransport.exchange(query, LOOPBACK, server.getLocalPort(), new DnsUpstreamTransport.Deadline(2000), hooks);
            check(Arrays.equals(answer, actual), "UDP exact bytes and TTL preserved after wrong ID is ignored");
            task.get(3, TimeUnit.SECONDS);
        }
        check(hooks.active.isEmpty(), "UDP descriptor released");
    }

    private static void authoritativeNxDomainIsReturned() throws Exception {
        DnsPacketCodec.Query query = query();
        byte[] answer = DnsPacketCodec.error(query, 3);
        Hooks hooks = new Hooks();
        try (DatagramSocket server = new DatagramSocket(0, LOOPBACK)) {
            FutureTask<Void> task = start(() -> respond(server, receive(server), answer));
            byte[] actual = DnsUpstreamTransport.exchange(query, LOOPBACK, server.getLocalPort(), new DnsUpstreamTransport.Deadline(1500), hooks);
            check(Arrays.equals(answer, actual), "NXDOMAIN does not trigger alternate-provider fallback");
            task.get(3, TimeUnit.SECONDS);
        }
        check(hooks.active.isEmpty(), "NXDOMAIN socket released");
    }

    private static void truncationUsesTcp() throws Exception {
        DnsPacketCodec.Query query = query();
        byte[] answer = DnsPacketCodec.addresses(query, java.util.Collections.singletonList(new byte[]{(byte)192, 0, 2, 8}));
        Hooks hooks = new Hooks();
        try (ServerSocket tcp = new ServerSocket(0, 1, LOOPBACK);
             DatagramSocket udp = new DatagramSocket(tcp.getLocalPort(), LOOPBACK)) {
            FutureTask<Void> task = start(() -> {
                truncated(udp, query);
                try (Socket incoming = tcp.accept()) {
                    readQuery(incoming, query);
                    DataOutputStream out = new DataOutputStream(incoming.getOutputStream());
                    out.writeShort(answer.length); out.write(answer); out.flush();
                }
            });
            byte[] actual = DnsUpstreamTransport.exchange(query, LOOPBACK, tcp.getLocalPort(), new DnsUpstreamTransport.Deadline(2500), hooks);
            check(Arrays.equals(answer, actual), "UDP TC triggers complete TCP DNS exchange");
            task.get(3, TimeUnit.SECONDS);
        }
        check(hooks.prepared == 2, "UDP and TCP both use socket protection hooks");
        check(hooks.active.isEmpty(), "TCP fallback descriptors released");
    }

    private static void malformedTcpFrameFails() throws Exception {
        DnsPacketCodec.Query query = query();
        Hooks hooks = new Hooks();
        try (ServerSocket tcp = new ServerSocket(0, 1, LOOPBACK);
             DatagramSocket udp = new DatagramSocket(tcp.getLocalPort(), LOOPBACK)) {
            FutureTask<Void> task = start(() -> {
                truncated(udp, query);
                try (Socket incoming = tcp.accept()) {
                    readQuery(incoming, query);
                    incoming.getOutputStream().write(new byte[]{0, 2, 0, 0});
                }
            });
            boolean rejected = false;
            try { DnsUpstreamTransport.exchange(query, LOOPBACK, tcp.getLocalPort(), new DnsUpstreamTransport.Deadline(2500), hooks); }
            catch (IOException expected) { rejected = true; }
            check(rejected, "malformed TCP length is rejected");
            task.get(3, TimeUnit.SECONDS);
        }
        check(hooks.active.isEmpty(), "malformed TCP descriptors released");
    }

    private static void deadlineBoundsUnresponsiveResolver() throws Exception {
        Hooks hooks = new Hooks();
        try (DatagramSocket unused = new DatagramSocket(0, LOOPBACK)) {
            long start = System.nanoTime();
            boolean failed = false;
            try { DnsUpstreamTransport.exchange(query(), LOOPBACK, unused.getLocalPort(), new DnsUpstreamTransport.Deadline(150), hooks); }
            catch (IOException expected) { failed = true; }
            check(failed, "nonresponsive resolver fails instead of returning forged data");
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000, "whole operation has a bounded deadline");
        }
        check(hooks.active.isEmpty(), "timeout releases descriptors");
    }

    private static void expiredQueueBudgetSendsNothing() throws Exception {
        Hooks hooks = new Hooks();
        DnsUpstreamTransport.Deadline deadline = new DnsUpstreamTransport.Deadline(1);
        Thread.sleep(10);
        boolean failed = false;
        try { DnsUpstreamTransport.exchange(query(), LOOPBACK, 9, deadline, hooks); }
        catch (IOException expected) { failed = true; }
        check(failed, "expired queued work fails promptly");
        check(hooks.prepared == 0 && hooks.active.isEmpty(), "expired budget opens no socket and sends no query");
    }

    private static void trickledTcpCannotExtendDeadline() throws Exception {
        DnsPacketCodec.Query query = query();
        Hooks hooks = new Hooks();
        try (ServerSocket tcp = new ServerSocket(0, 1, LOOPBACK);
             DatagramSocket udp = new DatagramSocket(tcp.getLocalPort(), LOOPBACK)) {
            FutureTask<Void> task = start(() -> {
                truncated(udp, query);
                try (Socket incoming = tcp.accept()) {
                    readQuery(incoming, query);
                    DataOutputStream out = new DataOutputStream(incoming.getOutputStream());
                    out.writeShort(600); out.flush();
                    for (int i = 0; i < 20; i++) {
                        try { out.write(0); out.flush(); Thread.sleep(35); }
                        catch (IOException expectedClose) { return; }
                    }
                }
            });
            long start = System.nanoTime();
            boolean failed = false;
            try { DnsUpstreamTransport.exchange(query, LOOPBACK, tcp.getLocalPort(), new DnsUpstreamTransport.Deadline(200), hooks); }
            catch (IOException expected) { failed = true; }
            check(failed, "trickle response does not reset deadline on every byte");
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500, "trickled DNS has bounded latency");
            task.get(3, TimeUnit.SECONDS);
        }
        check(hooks.active.isEmpty(), "trickled response descriptor released");
    }

    private static void cancellationClosesTransport() throws Exception {
        Hooks hooks = new Hooks();
        try (DatagramSocket server = new DatagramSocket(0, LOOPBACK)) {
            FutureTask<Void> client = start(() -> {
                boolean failed = false;
                try { DnsUpstreamTransport.exchange(query(), LOOPBACK, server.getLocalPort(), new DnsUpstreamTransport.Deadline(4500), hooks); }
                catch (IOException expected) { failed = true; }
                check(failed, "closing tracked sockets cancels in-flight DNS");
            });
            receive(server);
            for (AutoCloseable socket : hooks.active) socket.close();
            client.get(1, TimeUnit.SECONDS);
        }
        check(hooks.active.isEmpty(), "cancelled descriptor released");
    }

    private static void readQuery(Socket incoming, DnsPacketCodec.Query query) throws IOException {
        incoming.setSoTimeout(2000);
        DataInputStream in = new DataInputStream(incoming.getInputStream());
        int size = in.readUnsignedShort();
        byte[] actual = new byte[size]; in.readFully(actual);
        check(Arrays.equals(actual, query.dns), "TCP preserves query wire bytes");
    }

    private static void truncated(DatagramSocket udp, DnsPacketCodec.Query query) throws IOException {
        byte[] response = DnsPacketCodec.error(query, 0); response[2] |= 2;
        respond(udp, receive(udp), response);
    }
    private static DatagramPacket receive(DatagramSocket server) throws IOException {
        server.setSoTimeout(2500);
        DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
        server.receive(packet); return packet;
    }
    private static void respond(DatagramSocket server, DatagramPacket request, byte[] answer) throws IOException {
        server.send(new DatagramPacket(answer, answer.length, request.getSocketAddress()));
    }
    private interface ServerWork { void run() throws Exception; }
    private static FutureTask<Void> start(ServerWork work) {
        FutureTask<Void> task = new FutureTask<>(() -> { work.run(); return null; });
        Thread thread = new Thread(task, "dns-regression-peer"); thread.setDaemon(true); thread.start(); return task;
    }
    private static DnsPacketCodec.Query query() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0x1234); out.writeShort(0x0100); out.writeShort(1);
        out.writeShort(0); out.writeShort(0); out.writeShort(0);
        for (String label : "www.example.com".split("\\.")) {
            byte[] text = label.getBytes(StandardCharsets.US_ASCII); out.writeByte(text.length); out.write(text);
        }
        out.writeByte(0); out.writeShort(1); out.writeShort(1);
        DnsPacketCodec.Query result = DnsPacketCodec.parseQuery(bytes.toByteArray());
        if (result == null) throw new AssertionError("query fixture");
        return result;
    }
    private static void check(boolean okay, String reason) { checks++; if (!okay) throw new AssertionError(reason); }

    private static final class Hooks implements DnsUpstreamTransport.SocketHooks {
        final Set<AutoCloseable> active = ConcurrentHashMap.newKeySet();
        int prepared;
        @Override public void prepare(DatagramSocket socket) { prepared++; active.add(socket); }
        @Override public void prepare(Socket socket) { prepared++; active.add(socket); }
        @Override public void release(AutoCloseable socket) { active.remove(socket); }
    }
}
