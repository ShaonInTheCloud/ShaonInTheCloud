package com.safenest.app;

/** Finite paid window. No window can be created from an app toggle or client metadata. */
public final class CommitmentRules {
    private CommitmentRules() {}
    public static boolean validWindow(long starts, long ends, long serverNow) {
        return starts > 0 && starts <= serverNow && ends > serverNow &&
            ends - starts <= 370L * 24 * 60 * 60 * 1000;
    }
    public static long effectiveNow(long wall, long elapsed, long serverAnchor,
                                    long elapsedAnchor, long highWater, boolean sameBoot) {
        if (sameBoot && elapsed >= elapsedAnchor) {
            // A manual wall-clock jump must neither shorten nor extend a consented period.
            return Math.max(highWater, serverAnchor + elapsed - elapsedAnchor);
        }
        return Math.max(Math.max(wall, highWater), serverAnchor);
    }
    public static boolean active(boolean committed, long ends, long now) {
        return committed && ends > now && ends > 0;
    }
}
