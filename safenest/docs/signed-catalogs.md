# Signed domain catalogs

SafeNest can now use a maintained domain list in addition to its starter and personal rules. A publisher reviews domains, signs a versioned snapshot, and distributes it as a file or through an HTTPS endpoint. The app authenticates the entire snapshot before interpreting or installing its rules.

**This is publishing infrastructure, not an existing subscription database.** No production feed, trust key, signing private key, live endpoint or claim of complete coverage ships with this project. The demo lists contain only reserved `.example` domains.

## What is implemented

- P-256 ECDSA signatures with SHA-256, using Android's built-in cryptography APIs. The app pins the publisher public key explicitly entered in setup; it never accepts a key supplied by the downloaded document.
- Canonical format version 1; positive monotonically increasing revisions; whole-second UTC issuance and expiry; `gambling` and `adult` categories.
- The exact signed bytes are authenticated before rule parsing. Repeated/older revisions, unknown categories, invalid hosts, malformed data, wrong signatures and expired/future updates are rejected.
- Maximum 100,000 category/domain records and 6 MiB of decoded payload. The signed envelope is limited to 8 MiB + 1 KiB before Base64 decoding. Downloads have per-operation timeouts and a 30-second overall deadline.
- Default Android HTTPS certificate/hostname validation remains enabled. Redirects, user-info credentials, fragments and compressed responses are rejected. Use the final public HTTPS URL.
- Atomic on-disk installation in app-private storage. A failed verification, download or install leaves the previous verified rules in place. The accepted high-water revision persists across process restarts and explicit trust-key changes.
- Expired previously accepted rules continue blocking; status shows they are stale. Expiry never empties a working list automatically.
- Starter/personal edits and signed catalogs are stored separately. Removing an editable entry cannot remove a matching signed entry. New authenticated higher revisions may remove obsolete **catalog** entries without deleting separate personal/starter rules.
- Background rule-change callbacks execute outside the catalog/rule-store locks so managed policy refresh does not block those locks.

This follows the maintained-list aspect of blocking products. It does not recreate Gamban's private catalog or determine whether an unknown website contains gambling or adult material.

## Publisher setup

Requires Python 3.10+ and the `cryptography` package on the publisher's computer. From the `SafeNest` directory:

```powershell
python -m pip install cryptography
python tools/publish_catalog.py keygen --private publisher-private.pem --public publisher-public.pem
```

The private key is created only on the publisher's machine. Keep it private/offline; never put it in Git, the Android project, a ZIP download or the HTTPS feed. The utility requests restrictive Unix permissions; on Windows restrict its NTFS permissions to the publishing account. Distribute only the public PEM through a trusted channel and compare its displayed fingerprint on the device.

Create UTF-8 files `gambling.txt` and `adult.txt` containing one reviewed hostname per line. Blank lines and lines beginning with `#` are ignored. The publisher normalizes ASCII case, trailing dots and internationalized hostnames to ASCII IDN form. It rejects URLs, credentials, ports, paths, wildcard rules and IP addresses. A rule blocks that host and its subdomains; `www.example.com` is intentionally narrower than `example.com`.

Choose a revision greater than every previous release, including releases before a key rotation. Set a future UTC expiry no more than 366 days after issuance. For example, replace the expiry below with a current future date:

```powershell
python tools/publish_catalog.py sign --key publisher-private.pem --gambling gambling.txt --adult adult.txt --revision 1 --expires 2026-10-28T09:00:00Z --output catalog-1.sncatalog
```

For a harmless test, use `tools/catalog-demo-gambling.txt` and `tools/catalog-demo-adult.txt` instead. The utility refuses to overwrite existing key or catalog files. Publish a new filename/revision for each reviewed release, then update your HTTPS endpoint to serve its contents as `application/octet-stream` or `text/plain`, with HTTP 200 and no compression/redirect.

### Android setup

1. Open the signed catalog setup screen.
2. Paste the publisher's **public** PEM key. Leave the source URL blank for offline-only imports, or enter the final public HTTPS URL.
3. Save the configuration, then import a signed `.sncatalog` file or select refresh.
4. Check revision, record count, expiry, fingerprint and error/stale status.
5. Before installing a test catalog, confirm `example.org` actually loads on the phone. Sign a reviewed test catalog containing `example.org`, install it, and confirm it stops loading while an unlisted ordinary website such as `example.com` still loads. Restart the app and repeat before deploying to users. The reserved `.example` demo names cannot prove end-to-end blocking because they do not normally resolve.

The app does not fetch automatically or secretly report browsing history to the catalog publisher. Updates require an explicit import/refresh. The publisher must operate an endpoint, review classifications and issue new signed revisions as needed.

## Canonical wire format

The file is ASCII with LF newlines and exactly three nonempty lines, followed by one terminating LF:

```text
SAFENEST-SIGNED-CATALOG/1
payload:<standard padded Base64 of exact payload bytes>
signature:<standard padded Base64 of DER ECDSA SHA-256 signature over payload bytes>
```

Decoded payload example (the sample signature must be generated; a raw payload is not importable):

```text
SAFENEST-CATALOG/1
revision:1
issued-at:2026-09-28T09:00:00Z
expires-at:2026-10-28T09:00:00Z
adult:adult.example
gambling:gambling.example
```

After the four metadata lines, records must be strictly sorted lexically by the complete ASCII line, unique and newline-terminated. Categories are exactly `adult` or `gambling`. All hosts must already be canonical lowercase ASCII hostnames. The same hostname may occur once in each category. Empty snapshots are not accepted. Issuance allows at most five minutes of positive clock skew; timestamps use `YYYY-MM-DDTHH:MM:SSZ` without offsets or fractional seconds.

Keys use X.509 SubjectPublicKeyInfo PEM with `BEGIN PUBLIC KEY`; P-256 (`secp256r1`) is mandatory. Neither the algorithm nor the key is chosen from untrusted payload fields. Verification uses `Signature.getInstance("SHA256withECDSA")` without forcing a provider.

## Key rotation, stale data and recovery

Saving a new public key is an explicit trust change. The previous verified catalog remains active under its previously accepted key until a higher revision verifies with the newly configured key. The UI marks this condition. Revision numbering must continue across key rotations. Same-revision downloads are rejected as replay; a publisher should issue a new revision when renewing expiry.

An expired installed catalog is retained and marked stale. A newer expired update is rejected. Device clock changes can affect freshness checks; the stored monotonic revision still prevents accepting a same/older revision while private app data remains intact.

If the app-private state is corrupt or cannot be authenticated, signed-catalog loading/updates stop and an error is displayed; independent starter/personal rules remain. This prototype has no catalog recovery/export service. A publisher/developer must investigate and restore a trusted deployment before updating. Clearing app data/reprovisioning also clears personal configuration and the anti-rollback history, so it is not a transparent or automatic recovery operation.

Storage initialization treats a missing file as a fresh installation only when the app-private parent directory is accessible for reading, writing and traversal, and no main, backup (`.bak`) or pending (`.new`) file exists. An existing/unreadable/non-file state or inaccessible parent fails closed instead of resetting the remembered revision to zero. These are read-only metadata probes, not trial writes. Filesystem permission races, read-only mounts and interrupted writes still need Android device fault testing; static inspection and compilation do not prove those recovery paths.

Signatures authenticate the publisher's release; they do not prove its classifications are correct, prevent an authorized user from changing the pin, or resist root/device reset/app-data replacement. This is not a transparency log, multi-party signing system or complete TUF deployment. No checks here inspect encrypted web-page contents, identify every proxy, or make DNS filtering bypass-proof.

## Verification performed

`CatalogVerifierRegression` runs with the JDK alone and checks real signatures, tampering, attacker keys, replay/rollback, expiry/future timestamps, curve restrictions, metadata/canonicalization, domain/category validation, immutability and bounded input. It is also wrapped by a JUnit test.

The publisher's actual Python keygen/sign output was accepted by the actual Java verifier with the expected revision and two demo domains. The complete root `run-core-tests.sh` includes these tests. Android disk failure, device-clock behavior, process death during writes, HTTPS transport on a device and UI flows still require the device test plan; JVM tests do not establish those runtime results.

## Primary references

- Android recommends SHA-2/ECDSA signatures and provider-independent usage: https://developer.android.com/privacy-and-security/cryptography
- Android `AtomicFile` documents synchronized whole-file replacement; caller-side locking remains necessary: https://developer.android.com/reference/android/util/AtomicFile
- Java `Signature` verification API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html

Sources checked 28 September 2026. This format is SafeNest's own design, not a documented Gamban protocol.
