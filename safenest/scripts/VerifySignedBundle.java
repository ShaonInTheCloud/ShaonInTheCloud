import java.io.InputStream;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarFile;

/** Verifies every payload entry, not just the presence of a META-INF signature file. */
public final class VerifySignedBundle {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Bundle and expected certificate required");
        String expected = args[1].replace(":", "").toLowerCase(java.util.Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid certificate fingerprint");
        int checked = 0;
        long bytes = 0;
        try (JarFile archive = new JarFile(Path.of(args[0]).toFile(), true)) {
            if (archive.getJarEntry("BundleConfig.pb") == null || archive.getJarEntry("base/manifest/AndroidManifest.xml") == null) {
                throw new SecurityException("Not an Android App Bundle");
            }
            var entries = archive.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                try (InputStream input = archive.getInputStream(entry)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        bytes += count;
                        if (bytes > 250L * 1024 * 1024) throw new SecurityException("Bundle exceeds verification limit");
                    }
                }
                CodeSigner[] signers = entry.getCodeSigners();
                if (signers == null || signers.length != 1) throw new SecurityException("Unsigned or multiply signed payload");
                byte[] cert = signers[0].getSignerCertPath().getCertificates().get(0).getEncoded();
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert));
                if (!expected.equals(actual)) throw new SecurityException("Unexpected upload signing certificate");
                checked++;
            }
        }
        if (checked == 0) throw new SecurityException("No signed bundle payload");
        System.out.println("Verified " + checked + " signed bundle entries");
    }
}
