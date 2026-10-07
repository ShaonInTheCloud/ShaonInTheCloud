package com.safenest.app;

import java.util.function.LongSupplier;

/** In-memory, single-attempt token. This is not a substitute for Supabase validation. */
public final class AuthCaptchaToken {
    private String value;
    private final LongSupplier clock;
    private final long received;

    public AuthCaptchaToken(String value) { this(value, System::nanoTime); }

    AuthCaptchaToken(String value, LongSupplier clock) {
        if (value == null || value.trim().isEmpty() || value.length() > 2048)
            throw new IllegalArgumentException("Complete a new security check.");
        this.value = value;
        this.clock = clock;
        this.received = clock.getAsLong();
    }

    public synchronized String take() {
        String token = value;
        value = null; // Consume even when expired or the subsequent request fails.
        long age = clock.getAsLong() - received;
        if (token == null || age < 0 || age >= 300_000_000_000L)
            throw new IllegalStateException("Complete a new security check before retrying.");
        return token;
    }
}
