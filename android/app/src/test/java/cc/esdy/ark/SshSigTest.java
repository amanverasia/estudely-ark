package cc.esdy.ark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;

/** Runs against the real signed v1.0 MANIFEST.tsv and MANIFEST.tsv.sig (copied into test resources). */
public class SshSigTest {
    private static byte[] res(String name) throws Exception {
        try (InputStream in = SshSigTest.class.getResourceAsStream("/" + name)) {
            return in.readAllBytes();
        }
    }

    private static String sig() throws Exception { return new String(res("MANIFEST.tsv.sig"), StandardCharsets.US_ASCII); }

    @Test public void builtInKeyHasTheReleaseFingerprint() throws Exception {
        assertEquals(SshSig.RELEASE_KEY_FINGERPRINT, SshSig.fingerprint(SshSig.releaseKey()));
    }

    @Test public void realManifestVerifies() throws Exception {
        SshSig.verify(res("MANIFEST.tsv"), sig(), SshSig.releaseKey(), SshSig.NAMESPACE);
    }

    @Test public void changedManifestByteFails() throws Exception {
        byte[] m = res("MANIFEST.tsv");
        m[m.length / 2] ^= 1;
        assertThrows(SecurityException.class, () -> SshSig.verify(m, sig(), SshSig.releaseKey(), SshSig.NAMESPACE));
    }

    @Test public void changedSignatureByteFails() throws Exception {
        String armored = sig();
        String b64 = armored.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] blob = Base64.getDecoder().decode(b64);
        blob[blob.length - 10] ^= 1; // inside the 64-byte Ed25519 signature
        String bad = "-----BEGIN SSH SIGNATURE-----\n" + Base64.getEncoder().encodeToString(blob) + "\n-----END SSH SIGNATURE-----\n";
        assertThrows(SecurityException.class, () -> SshSig.verify(res("MANIFEST.tsv"), bad, SshSig.releaseKey(), SshSig.NAMESPACE));
    }

    @Test public void differentKeyFails() throws Exception {
        byte[] other = SshSig.releaseKey().clone();
        other[other.length - 1] ^= 1;
        assertThrows(SecurityException.class, () -> SshSig.verify(res("MANIFEST.tsv"), sig(), other, SshSig.NAMESPACE));
    }

    @Test public void wrongNamespaceFails() throws Exception {
        assertThrows(SecurityException.class, () -> SshSig.verify(res("MANIFEST.tsv"), sig(), SshSig.releaseKey(), "file"));
    }
}
