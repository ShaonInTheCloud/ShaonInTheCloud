# SafeNest 0.2 verification — 28 September 2026

## Completed checks

| Check | Result | What it establishes |
| --- | --- | --- |
| Standalone DNS regression | 20,075 assertions passed, including 20,000 malformed-packet fuzz cases | Tested packet parsing, responses and rejection cases |
| Standalone domain regression | 57 assertions passed, including a 25,000-rule case | Tested normalization and matching boundaries |
| Integrated JVM JUnit suite | 9 tests passed | Actual Java DNS/domain and Kotlin guard rule tests |
| All non-UI source compilation | Passed with Kotlin 2.0.21 / JDK 17 and actual AOSP Android 15/API 35 classes | VPN, guard, managed policies, catalog, admin and rules compile together; no invented Android stubs |
| Kotlin syntax parsing | All 11 Kotlin files: zero syntax errors | UI syntax only, not complete dependency/type checking |
| Java lint | javac -Xlint:all passed | No warnings in checked Java helpers |
| Shell launchers | Bash syntax, arbitrary working directory and paths with spaces checked | Launcher behavior in this environment |
| Gradle wrapper | Official Gradle 8.13 files and published checksums verified | Wrapper included and distribution checksum pinned |

Non-UI compilation produced one deprecation warning for ConnectivityManager.allNetworks. Compiling against API 35 does not prove runtime behavior or the configured API 36 build.

## Not completed

- **No complete APK or AAB was built.** The Gradle attempt could not resolve Android Gradle Plugin 8.13.0 and required Google-hosted dependencies in this environment; requests returned unavailable/404 results. There is no prebuilt APK in this archive.
- Compose/UI dependency compilation and Android resource/manifest processing remain unverified. Syntax parsing is not a substitute.
- No emulator or phone was connected. Normal browsing, DNS routing, blocked browsing, VPN replacement, Chrome policies, Accessibility view IDs and managed-policy recovery have not been demonstrated on a device.
- Windows PowerShell is unavailable here; the Windows launcher was inspected but not executed.
- Production signing, store submission, enrollment, accounts and payments were not validated.

## Reproduce

From SafeNest with JDK 17 available:

```bash
bash run-core-tests.sh
```

With Android Studio/SDK and dependency access available:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1
```

Or open SafeNest/android in Android Studio, sync and Run. Sync alone does not install updated code. Follow device-test-plan.md: first verify example.com works and a manually listed example.org is blocked.

## Conclusion

Available core checks pass. This source is **not yet validated as a working phone release or as equivalent to Gamban**. Keep Android **Block connections without VPN** off for this DNS-only design. Remaining browser/proxy/DNS coverage and runtime tests must be resolved or accurately scoped before distribution.
