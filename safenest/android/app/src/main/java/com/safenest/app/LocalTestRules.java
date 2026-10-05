package com.safenest.app;

/** Local QA access is never enabled by a production or release build. */
public final class LocalTestRules {
    public static final long DURATION_MS = 60 * 60 * 1000L;
    private LocalTestRules() {}
    public static boolean enabled(boolean debug, boolean labFlavor) {
        return debug && labFlavor;
    }
    public static boolean active(long deadline, long elapsed, int savedBoot, int currentBoot) {
        return savedBoot >= 0 && savedBoot == currentBoot && elapsed >= 0 &&
            deadline > elapsed && deadline - elapsed <= DURATION_MS;
    }
}
