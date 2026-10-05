# SafeNest baby pink liquid-metal UI — 0.4.1

Updated the latest 0.4.0 Android source (archive version 26) and its website preview.

## Code review

The supplied fragment mixed two revisions. It redeclared babyPink and JavaScript mx/my, overwrote gl_FragColor, left unreachable shader creation code after return, uploaded the same vertex buffer twice, and scheduled requestAnimationFrame twice. deepPink and wave/fold/ridge/mask definitions and the complete program setup were absent from the pasted excerpt.

The replacement defines the complete shader, uses one color output and one frame loop, checks shader compilation and linking, and clamps color blends. Baby pink RGB (1.00, 0.78, 0.88) is the base. Plum reflections, opal lavender and pearl/white highlights create animated liquid-metal folds. High precision is preferred to avoid speckled reflections, with a medium-precision fallback.

## Android

LiquidMetalBackground.kt renders the shared fragment through native OpenGL ES 2.0 beneath the Compose UI. Header and bottom navigation are solid baby pink. Existing protection, account, recovery and catalogue screens retain their actions. Text uses dark plum, with a translucent white layer for contrast.

Settings includes Pause/Play background animation. The choice persists. The renderer pauses when the Activity stops and uses a static frame in battery saver or with system animations disabled. It requests at most about 30 frames per second and caps the surface's longest edge at 1,200 pixels. No background animation changes the VPN/protection state. Version code 18, version name 0.4.1.

Build with the included build script or Android Studio; see BUILDING.md.

## Website preview

website/index.html loads metallic.css, liquid-metal.js and assets/liquid-metal.frag. It has mouse-responsive reflections, Pause/Play control, reduced-motion support, context-loss recovery and a CSS fallback. The existing demo label and preview-only behavior remain. Serve this directory with a local HTTP server. SafeNest-BabyPink-Live-Preview.html is a separate self-contained preview that embeds those same assets and can be opened directly in a browser. Fonts are requested from Google Fonts, with local fallback fonts.

## Verification and limits

Passed JavaScript syntax check; compiled and linked the exact shader in Mesa OpenGL ES 2.0; rendered 36 frames with changing pixels and no GL errors. Checked animation scheduling, pause/resume, hidden-page behavior, reduced motion, context restoration, page lifecycle and resolution cap with a JavaScript harness. Sampled the first shader frame under the Android white layer: minimum contrast about 8.0:1 for primary text and 4.7:1 for secondary text. This is a sampled frame, not an exhaustive accessibility audit.

Android build could not start: the available environment lacks a full JDK and Android SDK. A full browser visual check was also unavailable because the browser download failed. No new APK is included or claimed as tested. The source archive update has not been deployed to the public domain or pushed to GitHub.
