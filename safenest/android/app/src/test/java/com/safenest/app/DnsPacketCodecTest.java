package com.safenest.app;

import org.junit.Test;

public final class DnsPacketCodecTest {
    @Test public void packetProtocolRegression() { DnsPacketCodecRegression.runAll(); }
}
