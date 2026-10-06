package com.safenest.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class ControlScreenExitRegression {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static final class Fake implements ControlScreenExit.Driver {
        boolean active = true, backWorks = true, homeWorks = true, homeAlready;
        String foreground = "com.android.settings";
        int protectedDepth;
        final List<String> actions = new ArrayList<>();
        final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        public boolean active() { return active; }
        public boolean back() { actions.add("back"); if (backWorks && protectedDepth > 0) protectedDepth--; return backWorks; }
        public boolean home() { actions.add("home"); return homeWorks; }
        public String foregroundPackage() { return foreground; }
        public boolean protectedDetail() { return protectedDepth > 0; }
        public void post(Runnable action, long delayMs) { pending.add(action); }
        public boolean isHomePackage(String pkg) { return homeAlready; }
        void flush() { while (!pending.isEmpty()) pending.removeFirst().run(); }
    }
    private static ControlScreenExit exit(Fake driver) { return new ControlScreenExit("com.safenest.app.lab", driver); }
    public static void main(String[] args) {
        Fake f = new Fake();
        int[] notified = {0};
        check(exit(f).exit("com.android.settings", () -> notified[0]++), "starts protected-page exit");
        check(f.actions.equals(List.of("back")), "Back precedes delayed Home");
        f.flush();
        check(f.actions.equals(List.of("back", "home")) && notified[0] == 1, "Settings parent then Home");
        f = new Fake(); f.foreground = "com.safenest.app.lab";
        exit(f).exit("com.android.settings", () -> {}); f.flush();
        check(f.actions.equals(List.of("back", "home")), "deep link returning to test app still goes Home");
        f = new Fake(); exit(f).exit("com.android.settings", () -> {}); f.active = false; f.flush();
        check(f.actions.equals(List.of("back")), "Stop/expiry/revocation cancels pending Home");
        f = new Fake(); exit(f).exit("com.android.settings", () -> {}); f.foreground = "com.android.chrome"; f.flush();
        check(f.actions.equals(List.of("back")), "never follows user into unrelated app");
        f = new Fake(); ControlScreenExit n = exit(f); n.exit("com.android.settings", () -> {}); n.cancel(); f.flush();
        check(f.actions.equals(List.of("back")), "unbind cancels pending navigation");
        f = new Fake(); f.backWorks = false; exit(f).exit("com.android.settings", () -> {});
        f.flush();
        check(f.actions.equals(List.of("back", "home")) && f.pending.isEmpty(), "Home fallback if Back unavailable");
        f = new Fake(); f.active = false;
        check(!exit(f).exit("com.android.settings", () -> {}) && f.actions.isEmpty(), "inactive guard performs no actions");
        f = new Fake(); f.foreground = null; exit(f).exit("com.android.settings", () -> {}); f.flush();
        check(f.actions.equals(List.of("back")), "unknown foreground retries are bounded and never eject another app");
        f = new Fake(); f.foreground = null; exit(f).exit("com.android.settings", () -> {});
        f.pending.removeFirst().run(); f.foreground = "com.android.settings"; f.flush();
        check(f.actions.equals(List.of("back", "home")), "short window transition is retried");
        f = new Fake(); n = exit(f); n.exit("com.android.settings", () -> {}); n.exit("com.android.settings", () -> {}); f.flush();
        check(f.actions.equals(List.of("back", "home")), "same-page content events share one exit transaction");
        f = new Fake(); n = exit(f); notified[0] = 0;
        n.exit("com.android.settings", () -> notified[0]++);
        for (int i = 0; i < 8; i++) n.exit("com.android.settings", () -> notified[0]++);
        check(f.pending.size() == 1 && f.actions.equals(List.of("back")), "repeated events cannot starve pending Home");
        f.flush();
        check(notified[0] == 1 && f.actions.equals(List.of("back", "home")), "one Home and callback after repeated events");
        f = new Fake(); f.foreground = null; n = exit(f); n.exit("com.android.settings", () -> {});
        for (int i = 0; i < 4; i++) f.pending.removeFirst().run();
        f.foreground = "com.android.settings"; f.flush();
        check(f.actions.equals(List.of("back", "home")), "longer window focus transition completes Home");
        f = new Fake(); n = exit(f); n.exit("com.android.settings", () -> {});
        f.pending.removeFirst().run();
        n.exit("com.android.settings", () -> {});
        check(f.actions.equals(List.of("back", "home")), "old content events cannot restart Back while Home is applying");
        f.homeAlready = true; n.observeForeground("com.android.launcher3"); f.homeAlready = false;
        n.exit("com.android.settings", () -> {}); f.flush();
        check(f.actions.equals(List.of("back", "home", "back", "home")), "a fresh control page is guarded immediately after Home appears");
        f = new Fake(); f.foreground = "com.android.launcher3"; f.homeAlready = true; notified[0] = 0;
        exit(f).exit("com.android.settings", () -> notified[0]++); f.flush();
        check(f.actions.equals(List.of("back")) && notified[0] == 1, "Back already reaching Home records completion without another action");
        f = new Fake(); f.protectedDepth = 2; exit(f).exit("com.android.settings", () -> {}); f.flush();
        check(f.actions.equals(List.of("back", "back", "home")), "nested protected parent is popped before Home");
        f = new Fake(); f.protectedDepth = 10; exit(f).exit("com.android.settings", () -> {}); f.flush();
        check(f.actions.equals(List.of("back", "back", "back", "home")), "protected parent navigation is bounded");
        System.out.println("Control-screen navigation regression: " + checks + " checks passed.");
    }
}
