import importlib.util
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("direct_signing", SCRIPTS / "verify-direct-signing.py")
direct = importlib.util.module_from_spec(spec)
spec.loader.exec_module(direct)
PINNED = direct.DIRECT_CERT_SHA256
OTHER = "64:59:CC:EC:36:32:00:31:7A:E0:AD:AC:C6:5F:64:D4:B1:D6:A2:97:BC:BA:D2:A2:CE:0D:32:FD:73:E5:CA:D9"  # the upload cert


def colon(hex_digest):
    return ":".join(hex_digest[i:i + 2] for i in range(0, 64, 2)).upper()


def report(*digests):
    lines = ["Verifies", "Verified using v2 scheme (APK Signature Scheme v2): true", "Number of signers: %d" % len(digests)]
    for n, d in enumerate(digests, 1):
        lines += [f"Signer #{n} certificate DN: CN=Test", f"Signer #{n} certificate SHA-256 digest: {d}",
                  f"Signer #{n} certificate SHA-1 digest: {'0' * 40}"]
    return "\n".join(lines) + "\n"


class DirectSigningTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="safenest-direct-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "app-direct-release.apk"
        self.apk.write_bytes(b"not inspected by the stand-in")

    def fake_apksigner(self, stdout, code=0):
        """A stand-in for build-tools apksigner: prints the given report and exits with the given code."""
        out = self.root / "report.txt"
        out.write_text(stdout)
        tool = self.root / "apksigner"
        tool.write_text(f"#!{sys.executable}\nimport sys\nsys.stdout.write(open({str(out)!r}).read())\nsys.exit({code})\n")
        tool.chmod(tool.stat().st_mode | stat.S_IXUSR)
        return tool

    def test_pin_matches_the_documented_direct_certificate(self):
        doc = (SCRIPTS.parent / "docs" / "release-signing.md").read_text()
        documented = [line for line in doc.splitlines() if line.strip().startswith("- Direct:")]
        self.assertEqual(len(documented), 1, "exactly one documented direct certificate")
        self.assertEqual(direct.normalize(documented[0].split("`")[1]), PINNED)

    def test_matching_signer_passes_in_either_spelling(self):
        for spelling in (PINNED, colon(PINNED)):
            self.assertIn("SHA-256", direct.verify_apk(self.apk, self.fake_apksigner(report(spelling))))

    def test_wrong_signer_fails_with_both_fingerprints(self):
        with self.assertRaises(ValueError) as error:
            direct.verify_apk(self.apk, self.fake_apksigner(report(OTHER)))
        message = str(error.exception)
        self.assertIn("does not match", message)
        self.assertIn(PINNED, message)
        self.assertIn(direct.normalize(OTHER), message)

    def test_extra_signer_or_rotation_entry_with_another_key_fails(self):
        with self.assertRaises(ValueError):
            direct.verify_apk(self.apk, self.fake_apksigner(report(PINNED, OTHER)))
        lineage = report(PINNED) + f"Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: {OTHER}\n"
        with self.assertRaises(ValueError):
            direct.verify_apk(self.apk, self.fake_apksigner(lineage))

    def test_source_stamp_is_not_mistaken_for_the_app_signer(self):
        stamped = report(PINNED) + f"Source Stamp Signer certificate SHA-256 digest: {OTHER}\n"
        direct.verify_apk(self.apk, self.fake_apksigner(stamped))

    def test_unsigned_or_unverifiable_apk_fails(self):
        with self.assertRaisesRegex(ValueError, "could not verify"):
            direct.verify_apk(self.apk, self.fake_apksigner("DOES NOT VERIFY\n", code=1))
        with self.assertRaisesRegex(ValueError, "No signer"):
            direct.verify_apk(self.apk, self.fake_apksigner("Verifies\n"))

    def test_cli_exits_nonzero_and_writes_no_certs_file_on_mismatch(self):
        certs = self.root / "direct-release-certs.txt"
        result = subprocess.run([sys.executable, "-I", str(SCRIPTS / "verify-direct-signing.py"), "--apk", str(self.apk),
                                 "--apksigner", str(self.fake_apksigner(report(OTHER))), "--certs-out", str(certs)],
                                capture_output=True, text=True, timeout=60)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Direct signing check failed", result.stderr)
        self.assertFalse(certs.exists())
        ok = subprocess.run([sys.executable, "-I", str(SCRIPTS / "verify-direct-signing.py"), "--apk", str(self.apk),
                             "--apksigner", str(self.fake_apksigner(report(PINNED))), "--certs-out", str(certs)],
                            capture_output=True, text=True, timeout=60)
        self.assertEqual(ok.returncode, 0, ok.stderr)
        self.assertTrue(certs.exists())

    def test_keystore_must_hold_the_direct_certificate(self):
        env = {"SAFENEST_DIRECT_STORE_PASSWORD": "disposable-test-only", "SAFENEST_DIRECT_KEY_PASSWORD": "disposable-test-only",
               "SAFENEST_DIRECT_KEY_ALIAS": "test-direct"}
        store = self.root / "disposable.jks"
        with patch.dict(os.environ, env):
            subprocess.run(["keytool", "-genkeypair", "-keystore", str(store), "-storetype", "JKS",
                            "-storepass:env", "SAFENEST_DIRECT_STORE_PASSWORD", "-keypass:env", "SAFENEST_DIRECT_KEY_PASSWORD",
                            "-alias", "test-direct", "-dname", "CN=Disposable Test Only", "-keyalg", "RSA", "-keysize", "2048",
                            "-validity", "1"], capture_output=True, check=True, timeout=30)
            exported = subprocess.run(["keytool", "-exportcert", "-keystore", str(store), "-alias", "test-direct",
                                       "-storepass:env", "SAFENEST_DIRECT_STORE_PASSWORD"], capture_output=True, check=True, timeout=30)
            own = direct.hashlib.sha256(exported.stdout).hexdigest()
            # A disposable key is never the real direct key: the pinned check must refuse it.
            with self.assertRaisesRegex(ValueError, "does not match"):
                direct.verify_keystore(store)
            self.assertEqual(direct.verify_keystore(store, expected=own), own)
            with patch.dict(os.environ, {"SAFENEST_DIRECT_STORE_PASSWORD": "incorrect-private-test-password"}):
                with self.assertRaises(ValueError) as error:
                    direct.verify_keystore(store, expected=own)
                self.assertNotIn("incorrect-private-test-password", str(error.exception))


if __name__ == "__main__":
    unittest.main()
