# SafeNest 0.3 verification — 28 September 2026

## Passed in this workspace

| Check | Result |
| --- | --- |
| DNS packet regression | 20,075 assertions, including 20,000 malformed-packet fuzz cases |
| Domain normalization/matching | 57 assertions |
| DNS answer alias inspection | 10,082 assertions, including 10,000 malformed-response cases |
| Real loopback UDP/TCP transport | 22 assertions: reply validation, truncation/TCP retry, deadlines, slow trickles, cancellation and expired queued requests |
| Signed catalog verification | 59 checks with real P-256 signatures: tampering, wrong keys/curves, expiry, future times, replay, rollback and bounded/canonical input |
| Integrated JUnit suite | 20 tests passed, including the above harnesses, guard parsing and eight Chrome-policy tests |
| Non-UI compilation | All five production Java helpers and 11 non-UI Kotlin files compiled together against real AOSP Android 15/API 35 classes using JDK 17 and Kotlin 2.0.21 |
| Kotlin syntax | All 15 Kotlin source files parsed without syntax errors |
| Manifest/resources | XML parsing and manifest component/source cross-check passed |
| Publisher interoperability | Python-generated P-256 signed catalog successfully verified by the actual Java verifier |

No fabricated Android stubs were used. Source hashes remained unchanged during the final integration check. The compiler reported the existing ConnectivityManager.allNetworks deprecation warning. Regression assertion counts overlap with the JUnit harnesses; they are not separate thousands of device tests.

## Not verified

- No complete Android 36 APK/AAB build or Compose dependency/type compilation. The earlier full-build attempt could not obtain required Google-hosted build artifacts in this environment; the 0.3 checks do not resolve that dependency limitation.
- No phone/emulator installation or runtime traffic test. Ordinary internet recovery, OEM lifecycle behavior, Private DNS handling, Chrome runtime policies and actual Accessibility actions need device validation.
- No broad gambling/adult category accuracy evaluation, live catalog hosting, production signing, store submission, backend integration or automatic enrollment.
- The signed catalog store's Android AtomicFile/persistence and HTTPS integration were source-compiled; device crash/restart and real-host tests remain necessary. Cryptographic tests do not prove on-device persistence or publisher classification quality.
- This is not a general full-tunnel DPI implementation. Client-to-local-resolver TCP DNS remains missing, and Android Lockdown remains incompatible with the DNS-only tunnel.

## Reproduce

Use JDK 17 and run `bash run-core-tests.sh` from the extracted SafeNest folder. For Android, open SafeNest/android in Android Studio and follow BUILDING.md, then device-test-plan.md. Install the new app with Run; Gradle sync alone does not update it.

The first device gate is normal browsing plus a deliberately listed harmless domain: example.com must load while example.org, added to My list, must be denied. Keep Block connections without VPN off in this version.

This evidence supports the tested components. It does not certify production readiness, complete bypass prevention or equality with Gamban.
