package com.safenest.app;

import org.junit.Test;

public final class DnsUpstreamTransportTest {
    @Test public void realUdpAndTcpTransportRegressions() throws Exception {
        DnsUpstreamTransportRegression.runAll();
    }
}
