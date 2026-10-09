#!/usr/bin/env python3
"""Generate the Android device-owner provisioning QR for SafeNest Strong lock.

Usage:
  python3 tools/provisioning/make_qr.py --apk SafeNest-direct-release.apk \
      --url https://mysafenestbd.com/downloads/SafeNest-direct.apk --out build/strong-lock

Writes <out>.json (the QR payload) and, when the `qrcode` package is installed, <out>.png.

Safety checks before anything is written:
  * the download URL is HTTPS;
  * the APK is package com.safenest.app and contains the direct build's ProvisioningModeActivity
    (a Play or lab APK cannot be enrolled);
  * the APK is not signed with an Android debug key unless --allow-debug is given for a test phone
    (detected by the standard "CN=Android Debug" certificate name).
Android verifies the downloaded APK against the signing-certificate checksum in the QR, so the
hosted file must be signed with exactly the certificate inspected here.
"""
import argparse
import base64
import json
import os
import re
import shutil
import subprocess
import sys

ADMIN = "com.safenest.app/com.safenest.app.SafeNestAdminReceiver"


def cert_checksum_from_hex(hex_digest: str) -> str:
    """Android expects URL-safe base64 of the SHA-256 certificate digest, without padding."""
    clean = hex_digest.replace(":", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", clean):
        raise ValueError("certificate SHA-256 must be 64 hex characters")
    return base64.urlsafe_b64encode(bytes.fromhex(clean)).decode("ascii").rstrip("=")


def build_payload(url: str, checksum: str) -> dict:
    if not url.startswith("https://"):
        raise ValueError("the APK download URL must use HTTPS")
    return {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": ADMIN,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": url,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": checksum,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
        "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {"com.safenest.provisioning": "qr-v1"},
    }


def _tool(name: str) -> str:
    found = shutil.which(name)
    if found:
        return found
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        bt = os.path.join(sdk, "build-tools")
        for version in sorted(os.listdir(bt), reverse=True) if os.path.isdir(bt) else []:
            candidate = os.path.join(bt, version, name)
            if os.path.exists(candidate):
                return candidate
    raise SystemExit(f"{name} not found; set ANDROID_HOME or put Android build-tools on PATH")


def inspect_apk(apk: str):
    certs = subprocess.run([_tool("apksigner"), "verify", "--print-certs", apk],
                           capture_output=True, text=True, check=True).stdout
    digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})", certs)
    if len(digests) != 1:
        raise SystemExit(f"expected exactly one signer, found {len(digests)}")
    debug = "CN=Android Debug" in certs
    badging = subprocess.run([_tool("aapt2"), "dump", "badging", apk], capture_output=True, text=True, check=True).stdout
    match = re.search(r"package: name='([^']+)'", badging)
    if not match:
        raise SystemExit("could not read the APK package name with aapt2")
    package = match.group(1)
    tree = subprocess.run([_tool("aapt2"), "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                          capture_output=True, text=True, check=True).stdout
    return digests[0], debug, package, "ProvisioningModeActivity" in tree


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--apk", required=True)
    p.add_argument("--url", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--allow-debug", action="store_true", help="permit a debug-signed APK (dedicated test phones only)")
    a = p.parse_args(argv)

    digest, debug, package, has_provisioning = inspect_apk(a.apk)
    if package != "com.safenest.app":
        raise SystemExit(f"APK package is {package}, expected com.safenest.app")
    if not has_provisioning:
        raise SystemExit("APK has no ProvisioningModeActivity: build the direct flavor (assembleDirectRelease)")
    if debug and not a.allow_debug:
        raise SystemExit("APK is signed with an Android debug key; use the production direct signing key")

    payload = build_payload(a.url, cert_checksum_from_hex(digest))
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    text = json.dumps(payload, separators=(",", ":"))
    with open(a.out + ".json", "w", encoding="ascii") as f:
        f.write(json.dumps(payload, indent=2) + "\n")
    try:
        import qrcode  # type: ignore
        qrcode.make(text, error_correction=qrcode.constants.ERROR_CORRECT_M).save(a.out + ".png")
        print(f"Wrote {a.out}.json and {a.out}.png")
    except ImportError:
        print(f"Wrote {a.out}.json (pip install qrcode[pil] to also write the PNG)")
    if debug:
        print("WARNING: debug-signed APK. For a dedicated test phone only.", file=sys.stderr)


if __name__ == "__main__":
    main()
