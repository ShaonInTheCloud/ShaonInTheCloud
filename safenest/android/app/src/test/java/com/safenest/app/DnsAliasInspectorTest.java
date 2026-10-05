package com.safenest.app;

import org.junit.Test;

public final class DnsAliasInspectorTest {
    @Test public void reachableAliasPolicyRegression() {
        DnsAliasInspectorRegression.runAll();
    }
}
