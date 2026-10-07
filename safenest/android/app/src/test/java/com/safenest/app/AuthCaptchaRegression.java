package com.safenest.app;

import java.util.concurrent.atomic.AtomicLong;

public final class AuthCaptchaRegression {
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Challenge should have been rejected");
    }
    public static void main(String[] args) throws Exception {
        reject(() -> new AuthCaptchaToken(null));
        reject(() -> new AuthCaptchaToken(" "));
        reject(() -> new AuthCaptchaToken("x".repeat(2049)));
        AtomicLong clock = new AtomicLong(0);
        AuthCaptchaToken token = new AuthCaptchaToken("fresh", clock::get);
        if (!token.take().equals("fresh")) throw new AssertionError();
        reject(token::take);
        AuthCaptchaToken expired = new AuthCaptchaToken("expired", clock::get);
        clock.set(300_000_000_000L);
        reject(expired::take);
        reject(expired::take);
        AuthCaptchaToken race = new AuthCaptchaToken("one-attempt");
        AtomicLong successes = new AtomicLong();
        Runnable take = () -> { try { race.take(); successes.incrementAndGet(); } catch (IllegalStateException expected) {} };
        Thread a = new Thread(take), b = new Thread(take);
        a.start(); b.start(); a.join(); b.join();
        if (successes.get() != 1) throw new AssertionError("Concurrent replay succeeded");
        System.out.println("Auth CAPTCHA missing/oversize/expiry/replay/concurrency regressions passed");
    }
}
