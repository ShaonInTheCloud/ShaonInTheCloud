import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("signing", SCRIPTS / "verify-release-signing.py")
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)
ANDROID = "http://schemas.android.com/apk/res/android"
MANIFEST = f'''<manifest xmlns:android="{ANDROID}" package="com.safenest.app" android:versionCode="24" android:versionName="0.4.7">
<application android:allowBackup="false" android:usesCleartextTraffic="false">
<service android:name="com.safenest.app.SafeNestVpnService" android:permission="android.permission.BIND_VPN_SERVICE"/>
<service android:name="com.safenest.app.SafeNestAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"/>
</application></manifest>'''


class ReleaseIdentityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="safenest-signing-test-")
        cls.root = Path(cls.temp.name)
        cls.store = cls.root / "disposable.jks"
        cls.env = {
            "SAFENEST_UPLOAD_STORE_PASSWORD": "disposable-test-only",
            "SAFENEST_UPLOAD_KEY_PASSWORD": "disposable-test-only",
            "SAFENEST_UPLOAD_KEY_ALIAS": "test-upload",
        }
        with patch.dict(os.environ, cls.env):
            subprocess.run(["keytool", "-genkeypair", "-keystore", str(cls.store), "-storetype", "JKS",
                            "-storepass:env", "SAFENEST_UPLOAD_STORE_PASSWORD", "-keypass:env", "SAFENEST_UPLOAD_KEY_PASSWORD",
                            "-alias", "test-upload", "-dname", "CN=Disposable Test Only", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1"],
                           capture_output=True, check=True, timeout=30)
            # Export only this disposable certificate; no reusable private key is stored in source.
            result = subprocess.run(["keytool", "-exportcert", "-keystore", str(cls.store),
                                     "-alias", "test-upload", "-storepass:env", "SAFENEST_UPLOAD_STORE_PASSWORD"],
                                    capture_output=True, check=True, timeout=30)
            cls.fingerprint = signing.hashlib.sha256(result.stdout).hexdigest()
        cls.env["SAFENEST_UPLOAD_CERT_SHA256"] = cls.fingerprint
        cls.bundle = cls.root / "signed.aab"
        with zipfile.ZipFile(cls.bundle, "w") as bundle:
            bundle.writestr("BundleConfig.pb", b"test config")
            bundle.writestr("base/manifest/AndroidManifest.xml", b"test compiled manifest")
            bundle.writestr("base/dex/classes.dex", b"test payload")
        signer = [shutil.which("jarsigner")] if shutil.which("jarsigner") else ["java", "-m", "jdk.jartool/sun.security.tools.jarsigner.Main"]
        with patch.dict(os.environ, cls.env):
            subprocess.run(signer + ["-keystore", str(cls.store), "-storepass:env", "SAFENEST_UPLOAD_STORE_PASSWORD",
                                    "-keypass:env", "SAFENEST_UPLOAD_KEY_PASSWORD", str(cls.bundle), "test-upload"],
                           capture_output=True, check=True, timeout=30)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def setUp(self):
        self.environment = patch.dict(os.environ, self.env)
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def verifier(self, bundle, fingerprint=None):
        return subprocess.run(["java", str(SCRIPTS / "VerifySignedBundle.java"), str(bundle), fingerprint or self.fingerprint],
                              capture_output=True, timeout=30)

    def test_matching_keystore(self):
        self.assertEqual(signing.verify_keystore(self.store), self.fingerprint)

    def test_wrong_fingerprint_and_password_rejected_without_echoing_secret(self):
        with patch.dict(os.environ, {"SAFENEST_UPLOAD_CERT_SHA256": "0" * 64}):
            with self.assertRaisesRegex(ValueError, "does not match"):
                signing.verify_keystore(self.store)
        with patch.dict(os.environ, {"SAFENEST_UPLOAD_STORE_PASSWORD": "incorrect-private-test-password"}):
            with self.assertRaises(ValueError) as error:
                signing.verify_keystore(self.store)
            self.assertNotIn("incorrect-private-test-password", str(error.exception))

    def test_unsigned_wrong_signer_and_tampered_bundle_rejected(self):
        unsigned = self.root / "unsigned.aab"
        with zipfile.ZipFile(unsigned, "w") as bundle:
            bundle.writestr("BundleConfig.pb", b"test config")
            bundle.writestr("base/manifest/AndroidManifest.xml", b"unsigned")
        self.assertNotEqual(self.verifier(unsigned).returncode, 0)
        self.assertNotEqual(self.verifier(self.bundle, "0" * 64).returncode, 0)
        tampered = self.root / "tampered.aab"
        with zipfile.ZipFile(self.bundle) as source, zipfile.ZipFile(tampered, "w") as target:
            for entry in source.infolist():
                content = b"changed" if entry.filename == "base/dex/classes.dex" else source.read(entry)
                target.writestr(entry, content)
        self.assertNotEqual(self.verifier(tampered).returncode, 0)

    def test_actual_signed_payload_verified(self):
        result = self.verifier(self.bundle)
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertIn(b"Verified 3 signed bundle entries", result.stdout)

    def test_manifest_rejects_debug_lab_version_and_admin_variants(self):
        manifest = self.root / "manifest.xml"
        manifest.write_text(MANIFEST)
        self.assertEqual(signing.check_manifest(manifest, "0.4.7"), 24)
        for content in (
            MANIFEST.replace("com.safenest.app\"", "com.safenest.app.lab\"", 1),
            MANIFEST.replace("<application ", '<application android:debuggable="true" '),
            MANIFEST.replace("<application ", '<application android:testOnly="true" '),
            MANIFEST.replace('android:allowBackup="false"', 'android:allowBackup="true"'),
            MANIFEST.replace('android:usesCleartextTraffic="false"', 'android:usesCleartextTraffic="true"'),
            MANIFEST.replace("</application>", '<receiver android:name=".SafeNestAdminReceiver"/></application>'),
        ):
            manifest.write_text(content)
            with self.assertRaises(ValueError):
                signing.check_manifest(manifest, "0.4.7")
        manifest.write_text(MANIFEST)
        with self.assertRaises(ValueError):
            signing.check_manifest(manifest, "0.4.6")


if __name__ == "__main__":
    unittest.main()
