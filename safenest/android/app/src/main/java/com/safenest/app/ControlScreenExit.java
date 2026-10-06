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
    }
    private final String appPackage;
    private final Driver driver;
    private int generation;
    public ControlScreenExit(String appPackage, Driver driver) {
        this.appPackage = appPackage;
        this.driver = driver;
    }
    public void cancel() { generation++; }
    public boolean exit(String controlPackage, Runnable onHome) {
        if (!driver.active()) return false;
        int token = ++generation;
        if (!driver.back()) {
            if (!driver.active() || !driver.home()) return false;
            onHome.run();
            return true;
        }
        driver.post(() -> finish(token, controlPackage, onHome, 0, 1), 120);
        return true;
    }
    private void finish(int token, String controlPackage, Runnable onHome, int retry, int backs) {
        if (token != generation || !driver.active()) return;
        String foreground = driver.foregroundPackage();
        if (foreground == null && retry < 3) {
            driver.post(() -> finish(token, controlPackage, onHome, retry + 1, backs), 40);
            return;
        }
        if (controlPackage.equals(foreground) && driver.protectedDetail() && backs < 3 && driver.back()) {
            driver.post(() -> finish(token, controlPackage, onHome, 0, backs + 1), 120);
            return;
        }
        if ((controlPackage.equals(foreground) || appPackage.equals(foreground)) && driver.home()) onHome.run();
    }
}
