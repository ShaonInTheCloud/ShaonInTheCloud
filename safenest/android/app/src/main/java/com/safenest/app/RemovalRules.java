package com.safenest.app;

/** When a Strong lock phone may hand back device-owner rights so SafeNest can be uninstalled. */
public final class RemovalRules {
    private RemovalRules() {}

    /**
     * Only after the paid period has ended and every managed restriction has been released.
     * A pending release (policy footprint still present) must finish first, so no setting is
     * left half-restored when Android removes SafeNest's owner rights.
     */
    public static boolean canRemoveManagement(boolean deviceOwner, boolean periodActive, boolean managedFootprint) {
        return deviceOwner && !periodActive && !managedFootprint;
    }
}
