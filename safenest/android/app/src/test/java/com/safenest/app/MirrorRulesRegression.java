package com.safenest.app;

import java.util.List;

public final class MirrorRulesRegression {
    private static final List<String> BRANDS = List.of("babu88", "jeetbuzz", "baji", "1win", "bet365", "betway", "crickex", "melbet", "velki", "vbet", "mostbet", "nagad88", "glorycasino");
    private static int failures = 0;
    private static void expect(String host, String brand) {
        String got = MirrorRules.mirrorOf(host, BRANDS);
        if (brand == null ? got != null : !brand.equals(got)) { failures++; System.out.println("FAIL " + host + " expected " + brand + " got " + got); }
    }
    public static void main(String[] args) {
        // Mirrors
        expect("babu88a.com", "babu88"); expect("www.babu88b.net", "babu88"); expect("babu88-bd.live", "babu88");
        expect("mbabu88.com", "babu88"); expect("babu88app.com", "babu88"); expect("babu88.com.bd", "babu88");
        expect("jeetbuzz123.live", "jeetbuzz"); expect("jeetbuzz-official.com", "jeetbuzz"); expect("thejeetbuzz.net", "jeetbuzz");
        expect("baji999.live", "baji"); expect("bajilive.net", "baji"); expect("baji-bd.com", "baji"); expect("bajibet.org", "baji");
        expect("1win-bd.com", "1win"); expect("1win7.net", "1win"); expect("bet365bangla.com", "bet365"); expect("crickex11.com", "crickex");
        expect("mostbet-bd3.com", "mostbet"); expect("nagad88x.com", "nagad88"); expect("glorycasino-bd.com", "glorycasino");
        // Not mirrors
        expect("bajirao.com", null); expect("bajaj.com", null); expect("alibaba.com", null); expect("google.com", null);
        expect("bet365sports.com", null); expect("velkia.fi", null); expect("betwayne.com", null); expect("1winner.com", null);
        expect("vbetter.com", null); expect("wikipedia.org", null); expect("prothomalo.com", null); expect("babu88a", null);
        expect("babu88a.com.bd", "babu88"); expect("mybabu88login.com", "babu88"); expect("melbet.com", "melbet");
        expect("babu88-really-long-tail-here.com", null); expect("baji.fun", "baji"); expect("bajiwin.com", "baji");
        if (failures > 0) { System.out.println(failures + " MirrorRules regressions"); System.exit(1); }
        System.out.println("MirrorRulesRegression: ok");
    }
}
