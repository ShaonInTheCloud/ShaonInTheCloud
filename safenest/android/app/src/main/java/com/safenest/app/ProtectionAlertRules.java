package com.safenest.app;

/** Pure decisions for the "protection was turned off" alert; no Android dependencies. */
public final class ProtectionAlertRules {
    private ProtectionAlertRules() {}

    /** Repeat an unresolved alert at most this often. */
    public static final long MIN_REPEAT_MS = 10 * 60 * 1000L;

    public enum State { PROTECTED, INTERRUPTED, NOT_COMMITTED }

    /**
     * @param committed     a verified protection period is active
     * @param serviceRunning SafeNest's VPN service reports a live session
     * @param holdsVpnConsent VpnService.prepare(...) == null, i.e. no other app took the VPN slot
     */
    public static State state(boolean committed, boolean serviceRunning, boolean holdsVpnConsent) {
        if (!committed) return State.NOT_COMMITTED;
        return serviceRunning && holdsVpnConsent ? State.PROTECTED : State.INTERRUPTED;
    }

    /** Alert on the first interruption, then no more than once per MIN_REPEAT_MS while it lasts. */
    public static boolean shouldNotify(State state, long lastNotifiedAt, long now) {
        if (state != State.INTERRUPTED) return false;
        if (lastNotifiedAt <= 0 || now < lastNotifiedAt) return true;   // first time, or clock moved back
        return now - lastNotifiedAt >= MIN_REPEAT_MS;
    }

    /** Another app holding the VPN consent is the WARP / third-party VPN takeover case. */
    public static boolean likelyOtherVpn(boolean holdsVpnConsent) { return !holdsVpnConsent; }
}
