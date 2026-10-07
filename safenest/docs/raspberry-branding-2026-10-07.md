# SafeNest Raspberry Pink branding — 2026-10-07

Approved artwork: original option 07, recolor option 2 (Raspberry Pink). Preserve the open-ended geometric S and photographed wordmark; do not use the rejected option 04 rounded-square cutout.

Website: shared mark and original wordmark on marketing, account, legal and support routes; favicon and touch icon; gentle home-page image reveal with a reduced-motion fallback. The 0.4.9 direct development APK replaces the old download. APK bytes are hosted by the website and are not committed into this GitHub source repository.

Android: version 0.4.9 / code 26. Shared adaptive launcher icon and app header, plus a roughly two-second cold-start intro. The S scales and turns gently into view, a soft reflection crosses it, and the original wordmark follows. This animates still photographs, not a generated video or real-time 3D model. Rule loading starts concurrently. Intro state survives configuration changes; Android's disabled system animation setting bypasses motion. No authentication, billing, trial enrollment or protection decisions are changed.

Verified locally: 63 website tests passed; static build and brand links passed; Play, direct and lab debug APK assembly plus lint passed (148 tasks). APK version and debug signature were verified. No phone/emulator playback or live trial/auth acceptance test is claimed.

Direct APK SHA-256: 485c8d69167b9690acfab06d85e3acd6ff3ebd11dd04b65f72ea6a76016b7210

Lab test APK SHA-256: 1dc740581ab25b0320865ed041165cdb80b5de9feec38b7b7e5899ffdc6e540c

The lab package com.safenest.app.lab installs separately from the normal app and can show the intro without replacing an existing installation. The new direct debug certificate differs from the older website 0.4.0 debug APK certificate, so it is not an in-place update of that old signed APK. No signing keys are distributed. This is not a signed Play release.
