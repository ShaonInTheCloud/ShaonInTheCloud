package com.safenest.app;

import org.junit.Test;

public final class CatalogVerifierTest {
    @Test public void verifiesOnlyFreshCanonicalPinnedSnapshots() throws Exception { CatalogVerifierRegression.runAll(); }
}
