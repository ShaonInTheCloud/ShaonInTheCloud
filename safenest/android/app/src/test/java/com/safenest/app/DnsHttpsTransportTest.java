package com.safenest.app;

import org.junit.Test;

public final class DnsHttpsTransportTest {
    @Test public void encryptedWireTransportRegressions() throws Exception {
        DnsHttpsTransportRegression.runAll();
    }
}
