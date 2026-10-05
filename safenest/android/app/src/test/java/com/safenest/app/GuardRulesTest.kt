package com.safenest.app

import org.junit.Assert.*
import org.junit.Test

class GuardRulesTest {
    @Test fun browsersRemainOpenEvenWithAnOldBlockedAppSelection() {
        for (name in listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix")) {
            assertFalse(name, GuardRules.shouldReturnHome(name, selectedOrVpn = true, essential = false))
        }
    }

    @Test fun selectedAppsStillReturnHomeButOrdinaryAndEssentialAppsDoNot() {
        assertTrue(GuardRules.shouldReturnHome("com.example.blockedapp", true, false))
        assertFalse(GuardRules.shouldReturnHome("com.example.allowedapp", false, false))
        assertFalse(GuardRules.shouldReturnHome("com.android.settings", true, true))
        assertFalse(GuardRules.shouldReturnHome("https://blocked.example", true, false))
    }

    @Test fun extractsHostWithoutPathSearchOrFragment() {
        assertEquals("example.org", GuardRules.hostFromAddressBar("https://example.org/page?next=blocked.example#part"))
        assertEquals("sub.example.org", GuardRules.hostFromAddressBar("SUB.Example.Org/path"))
        assertEquals("example.org", GuardRules.hostFromAddressBar("example.org:8443"))
        assertEquals("example.org", GuardRules.hostFromAddressBar("https://example.org./"))
    }

    @Test fun doesNotConfuseLookalikeHostWithActualDomain() {
        assertEquals("example.org.evil.test", GuardRules.hostFromAddressBar("https://example.org.evil.test/"))
        assertNull(GuardRules.hostFromAddressBar("https://example.org@evil.test/"))
        assertNull(GuardRules.hostFromAddressBar("https://example.org\\@evil.test/"))
    }

    @Test fun rejectsSearchTermsInternalPagesCredentialsAndIpLiterals() {
        for (value in listOf("find example.org", "chrome://settings", "about:blank", "file:///tmp/a", "javascript:alert(1)",
            "https://user:secret@example.org", "https://127.0.0.1", "https://[::1]", "https://%65xample.org")) {
            assertNull(value, GuardRules.hostFromAddressBar(value))
        }
    }

    @Test fun validatesDomainsAndPackageNames() {
        for (value in listOf("https://-bad.org", "https://bad..org", "https://bad.org:0", "https://bad.org:70000", "https://bad.org:abc")) {
            assertNull(value, GuardRules.hostFromAddressBar(value))
        }
        assertEquals("xn--bcher-kva.example", GuardRules.hostFromAddressBar("https://bücher.example/"))
        assertTrue(GuardRules.isPackageName("com.example.app"))
        assertFalse(GuardRules.isPackageName("com.example.app;anything"))
        assertFalse(GuardRules.isPackageName("com..app"))
        assertFalse(GuardRules.isPackageName("https://example.com"))
    }
}
