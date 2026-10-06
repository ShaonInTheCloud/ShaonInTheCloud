package com.safenest.app;

/** Leaves a protected detail page before Home; delayed actions never follow the user to another app. */
public final class ControlScreenExit {
    public interface Driver {
        boolean active();
        boolean back();
        boolean home();
        String foregroundPackage();
        boolean protectedDetail();
        void post(Runnable action, long delayMs);
        default void note(String outcome) {}
        default boolean isHomePackage(String pkg) { return false; }
    }
    private final String appPackage;
    private final Driver driver;
    private int generation;
    private String pendingPackage;
    private boolean homeRequested;
    public ControlScreenExit(String appPackage, Driver driver) {
        this.appPackage = appPackage;
        this.driver = driver;
    }
    public void cancel() { generation++; pendingPackage = null; homeRequested = false; driver.note("cancelled"); }
    public void observeForeground(String pkg) {
        if (homeRequested && pkg != null && driver.isHomePackage(pkg)) {
            generation++;
            pendingPackage = null;
            homeRequested = false;
        }
    }
    public boolean exit(String controlPackage, Runnable onHome) {
        if (!driver.active()) return false;
        // Content events from the same page must not repeatedly replace the
        // transaction while Back is still changing the active window.
        if (controlPackage.equals(pendingPackage)) return true;
        int token = ++generation;
        pendingPackage = controlPackage;
        homeRequested = false;
        if (!driver.back()) {
            homeRequested = true;
            if (!driver.active() || !driver.home()) { pendingPackage = null; homeRequested = false; driver.note("home_failed"); return false; }
            driver.note("home_sent");
            onHome.run();
            releaseAfterHome(token);
            return true;
        }
        driver.note("back_sent");
        driver.post(() -> finish(token, controlPackage, onHome, 0, 1), 250);
        return true;
    }
    private void finish(int token, String controlPackage, Runnable onHome, int retry, int backs) {
        if (token != generation) return;
        if (!driver.active()) { pendingPackage = null; driver.note("inactive"); return; }
        String foreground = driver.foregroundPackage();
        if (foreground == null && retry < 6) {
            driver.note("window_pending");
            driver.post(() -> finish(token, controlPackage, onHome, retry + 1, backs), 100);
            return;
        }
        if (controlPackage.equals(foreground) && driver.protectedDetail() && backs < 3 && driver.back()) {
            driver.note("back_nested");
            driver.post(() -> finish(token, controlPackage, onHome, 0, backs + 1), 250);
            return;
        }
        if (controlPackage.equals(foreground) || appPackage.equals(foreground)) {
            homeRequested = true;
            if (driver.home()) { driver.note("home_sent"); onHome.run(); releaseAfterHome(token); }
            else { pendingPackage = null; homeRequested = false; driver.note("home_failed"); }
        } else {
            pendingPackage = null;
            if (foreground != null && driver.isHomePackage(foreground)) { driver.note("home_already"); onHome.run(); }
            else driver.note(foreground == null ? "window_unknown" : "foreground_changed");
        }
    }
    private void releaseAfterHome(int token) {
        // Ignore repeated content events until Android applies Home. This
        // delayed callback only releases state; it performs no navigation.
        driver.post(() -> { if (token == generation) { pendingPackage = null; homeRequested = false; } }, 500);
    }
}
