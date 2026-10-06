package com.safenest.app;

import java.util.LinkedHashMap;

/** Volatile window ownership from events; no content, titles or persistent history. */
public final class WindowPackageCache {
    private final LinkedHashMap<Integer, String> packages = new LinkedHashMap<>();
    public void observe(int windowId, String pkg) {
        if (windowId < 0 || pkg == null || !pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) return;
        packages.remove(windowId);
        packages.put(windowId, pkg);
        while (packages.size() > 12) packages.remove(packages.keySet().iterator().next());
    }
    public String owner(int windowId) { return packages.get(windowId); }
    public void remove(int windowId) { packages.remove(windowId); }
    public void clear() { packages.clear(); }
}
