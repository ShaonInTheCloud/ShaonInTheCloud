#!/usr/bin/env python3
"""Fail closed unless direct-distribution material and the signed APK use SafeNest's direct certificate.

The direct key is permanent: the Strong lock provisioning QR names its certificate and Android only
accepts updates signed with it (docs/release-signing.md). Printing the signer is not enough; a wrong
keystore in the environment would otherwise produce an APK that cannot update existing installs.
The expected value is the public certificate SHA-256, so it lives in source and changes only by review.
No secret is read into output.
"""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess

# Public SHA-256 of the direct-distribution signing certificate (docs/release-signing.md, "Direct").
DIRECT_CERT_SHA256 = "e1725d0fc623c02a6993fd2123ea8bb1c52621147f5bac9e04f1a2f70eff6462"
_DIGEST = re.compile(r"^Signer #?\S*.*?certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)\s*$", re.M)


def normalize(value):
    value = (value or "").replace(":", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("Certificate SHA-256 must be 64 hex digits")
    return value


def signer_digests(apksigner_output):
    """Every certificate SHA-256 apksigner reports for the APK's signers (including rotation lineage)."""
    return [normalize(match) for match in _DIGEST.findall(apksigner_output)]


def verify_apk(apk, apksigner, expected=DIRECT_CERT_SHA256):
    """Run apksigner on the actual signed APK; every reported signer must be the direct certificate."""
    expected = normalize(expected)
    result = subprocess.run([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)],
                            capture_output=True, text=True, timeout=120, check=False)
    if result.returncode:
        raise ValueError("apksigner could not verify the APK signature")
    digests = signer_digests(result.stdout)
    if not digests:
        raise ValueError("No signer certificate reported for the APK")
    wrong = sorted({d for d in digests if d != expected})
    if wrong:
        raise ValueError("APK signer certificate does not match the direct certificate: "
                         f"expected {expected}, found {', '.join(wrong)}")
    return result.stdout


def verify_keystore(path, expected=DIRECT_CERT_SHA256):
    """Check the operator-supplied keystore before building, so a wrong key fails fast."""
    for name in ("SAFENEST_DIRECT_STORE_PASSWORD", "SAFENEST_DIRECT_KEY_PASSWORD", "SAFENEST_DIRECT_KEY_ALIAS"):
        if not os.environ.get(name):
            raise ValueError("Required direct signing configuration is missing")
    result = subprocess.run([
        "keytool", "-exportcert", "-rfc", "-keystore", str(path),
        "-alias", os.environ["SAFENEST_DIRECT_KEY_ALIAS"],
        "-storepass:env", "SAFENEST_DIRECT_STORE_PASSWORD",
    ], capture_output=True, timeout=30, check=False)
    if result.returncode:
        raise ValueError("Direct keystore or alias could not be verified")
    match = re.search(rb"-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----", result.stdout, re.S)
    if not match:
        raise ValueError("Direct certificate is missing")
    actual = hashlib.sha256(base64.b64decode(re.sub(rb"\s+", b"", match[1]), validate=True)).hexdigest()
    if actual != normalize(expected):
        raise ValueError("Direct keystore certificate does not match the direct certificate")
    return actual


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keystore", type=Path)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--apksigner", type=Path)
    parser.add_argument("--certs-out", type=Path)
    args = parser.parse_args()
    try:
        if args.keystore:
            verify_keystore(args.keystore)
            print("Direct keystore certificate matches the pinned direct certificate")
        elif args.apk and args.apksigner:
            report = verify_apk(args.apk, args.apksigner)
            if args.certs_out:
                args.certs_out.write_text(report)
            print(f"Signed direct APK certificate matches {DIRECT_CERT_SHA256}")
        else:
            parser.error("Provide --keystore, or --apk with --apksigner")
    except ValueError as error:
        raise SystemExit(f"Direct signing check failed: {error}")


if __name__ == "__main__":
    main()
