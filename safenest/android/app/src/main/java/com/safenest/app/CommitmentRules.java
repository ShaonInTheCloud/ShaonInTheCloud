package com.safenest.app;

/** Finite paid window. No window can be created from an app toggle or client metadata. */
public final class CommitmentRules {
    private CommitmentRules() {}
    public static boolean validWindow(long starts, long ends, long serverNow) {
        return starts > 0 && starts <= serverNow && ends > serverNow &&
            ends - starts <= 370L * 24 * 60 * 60 * 1000;
    }
    /**
     * Trusted time: the last server time plus uptime since. The wall clock is never read, so moving it can neither
     * shorten nor extend a consented period, before or after a reboot. A reading below the anchor means a reboot:
     * only uptime since boot is added. Powered-off time is not counted until the next server recheck re-anchors.
     */
    public static long effectiveNow(long elapsed, long serverAnchor, long elapsedAnchor, long highWater) {
        if (elapsed >= elapsedAnchor) return Math.max(highWater, serverAnchor + elapsed - elapsedAnchor);
        return Math.max(highWater, serverAnchor) + elapsed;
    }
    public static boolean active(boolean committed, long ends, long now) {
        return committed && ends > now && ends > 0;
    }
}
