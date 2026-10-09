import base64
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import make_qr  # noqa: E402


class MakeQrTest(unittest.TestCase):
    def test_checksum_is_unpadded_urlsafe_base64_of_digest(self):
        hex_digest = "0b0f5ff4e5f7a2ac0a12d4c7172a65028b5938d75888618fa2f1871d4fce6f41"
        value = make_qr.cert_checksum_from_hex(hex_digest)
        self.assertNotIn("=", value)
        self.assertNotIn("+", value)
        self.assertNotIn("/", value)
        self.assertEqual(base64.urlsafe_b64decode(value + "=" * (-len(value) % 4)).hex(), hex_digest)
        self.assertEqual(make_qr.cert_checksum_from_hex(hex_digest.upper()), value)
        self.assertEqual(make_qr.cert_checksum_from_hex(":".join(hex_digest[i:i + 2] for i in range(0, 64, 2))), value)

    def test_rejects_bad_digest_and_plain_http(self):
        with self.assertRaises(ValueError):
            make_qr.cert_checksum_from_hex("abc")
        with self.assertRaises(ValueError):
            make_qr.build_payload("http://mysafenestbd.com/a.apk", "x")

    def test_payload_names_the_safenest_admin_receiver(self):
        payload = make_qr.build_payload("https://mysafenestbd.com/a.apk", "abc")
        self.assertEqual(payload["android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"],
                         "com.safenest.app/com.safenest.app.SafeNestAdminReceiver")
        self.assertTrue(payload["android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED"])


if __name__ == "__main__":
    unittest.main()
