#!/usr/bin/env python3
"""Verify operator-supplied upload material and a Play-only signed build; log no secrets."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


def expected_certificate():
    value = os.environ.get("SAFENEST_UPLOAD_CERT_SHA256", "").replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("Configure the expected upload certificate SHA-256")
    return value


def verify_keystore(path):
    for name in ("SAFENEST_UPLOAD_STORE_PASSWORD", "SAFENEST_UPLOAD_KEY_PASSWORD", "SAFENEST_UPLOAD_KEY_ALIAS"):
        if not os.environ.get(name):
            raise ValueError("Required upload signing configuration is missing")
    result = subprocess.run([
        "keytool", "-exportcert", "-rfc", "-keystore", str(path),
        "-alias", os.environ["SAFENEST_UPLOAD_KEY_ALIAS"],
        "-storepass:env", "SAFENEST_UPLOAD_STORE_PASSWORD",
    ], capture_output=True, timeout=30, check=False)
    if result.returncode:
        raise ValueError("Upload keystore or alias could not be verified")
    match = re.search(rb"-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----", result.stdout, re.S)
    if not match:
        raise ValueError("Upload certificate is missing")
    cert = base64.b64decode(re.sub(rb"\s+", b"", match[1]), validate=True)
    actual = hashlib.sha256(cert).hexdigest()
    if actual != expected_certificate():
        raise ValueError("Upload certificate does not match the configured fingerprint")
    return actual


def check_manifest(path, version):
    android = "{http://schemas.android.com/apk/res/android}"
    root = ET.parse(path).getroot()
    if root.get("package") != "com.safenest.app" or root.get(android + "versionName") != version:
        raise ValueError("Unexpected release package or version")
    code = root.get(android + "versionCode", "")
    if not code.isdigit() or int(code) < 1:
        raise ValueError("Invalid release version code")
    app = root.find("application")
    if app is None or app.get(android + "debuggable", "false") != "false" or app.get(android + "testOnly", "false") != "false":
        raise ValueError("Debug/test-only build cannot be released")
    if app.get(android + "allowBackup") != "false" or app.get(android + "usesCleartextTraffic") != "false":
        raise ValueError("Release backup/cleartext settings must be disabled")
    if root.find("instrumentation") is not None:
        raise ValueError("Test instrumentation cannot be released")
    for receiver in app.findall("receiver"):
        if receiver.get(android + "permission") == "android.permission.BIND_DEVICE_ADMIN" or "SafeNestAdminReceiver" in receiver.get(android + "name", ""):
            raise ValueError("Device Administrator receiver is not allowed in the Play release")
    for name, permission in (("SafeNestVpnService", "android.permission.BIND_VPN_SERVICE"),
                             ("SafeNestAccessibilityService", "android.permission.BIND_ACCESSIBILITY_SERVICE")):
        if not any(service.get(android + "name", "").endswith(name)
                   and service.get(android + "permission") == permission for service in app.findall("service")):
            raise ValueError("Expected protected Android service missing")
    return int(code)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keystore", type=Path)
    parser.add_argument("--bundle", type=Path)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--version")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.keystore:
        verify_keystore(args.keystore)
        print("Upload certificate matches the configured SHA-256")
    elif all((args.bundle, args.manifest, args.version, args.output)):
        code = check_manifest(args.manifest, args.version)
        fingerprint = expected_certificate()
        verifier = Path(__file__).with_name("VerifySignedBundle.java")
        subprocess.run(["java", str(verifier), str(args.bundle), fingerprint], check=True, timeout=120)
        sha = hashlib.sha256(args.bundle.read_bytes()).hexdigest()
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "SHA256SUMS").write_text(f"{sha}  {args.bundle.name}\n")
        (args.output / "release-evidence.json").write_text(json.dumps({
            "package": "com.safenest.app", "version_name": args.version, "version_code": code,
            "flavor": "play", "bundle_sha256": sha, "upload_certificate_sha256": fingerprint,
            "source_commit": os.environ.get("GITHUB_SHA"), "store_upload_performed": False,
        }, indent=2) + "\n")
        print("Signed Play bundle verified; no Play Store upload performed")
    else:
        parser.error("Provide a keystore, or bundle/manifest/version/output")


if __name__ == "__main__":
    main()
