package cc.esdy.ark;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

/**
 * Verifies an OpenSSH "SSHSIG" signature (what `ssh-keygen -Y sign` writes) made with an Ed25519 key.
 * Format: https://github.com/openssh/openssh-portable/blob/master/PROTOCOL.sshsig
 * The trusted key is passed in by the caller (built into the app), never read from the download.
 */
public final class SshSig {
    /** The Estudely Ark release key, `ssh-ed25519` public key blob in base64 (from KEYS/allowed_signers of v1.0). */
    public static final String RELEASE_KEY_B64 = "AAAAC3NzaC1lZDI1NTE5AAAAIIn81PY25ZcvbC0Mubb6ARtdK8sDs1mLqr8QvEq78TX0";
    public static final String RELEASE_KEY_FINGERPRINT = "SHA256:PAm9fPRpBLPaPXZVXO3g+ixxl4QW/LSjIhvWDe7uZ4E";
    public static final String NAMESPACE = "estudely-ark";

    private SshSig() {}

    /** OpenSSH-style fingerprint of a public key blob: "SHA256:" + unpadded base64 of its SHA-256. */
    public static String fingerprint(byte[] keyBlob) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(keyBlob);
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(h);
    }

    /** Returns normally if `sigArmored` is a valid signature of `message` by `trustedKeyBlob` in `namespace`; throws otherwise. */
    public static void verify(byte[] message, String sigArmored, byte[] trustedKeyBlob, String namespace) throws Exception {
        String b64 = sigArmored.replace("-----BEGIN SSH SIGNATURE-----", "")
                .replace("-----END SSH SIGNATURE-----", "").replaceAll("\\s", "");
        ByteBuffer in = ByteBuffer.wrap(Base64.getDecoder().decode(b64));
        byte[] magic = new byte[6];
        in.get(magic);
        require(Arrays.equals(magic, "SSHSIG".getBytes(StandardCharsets.US_ASCII)), "not an SSH signature");
        require(in.getInt() == 1, "unsupported SSHSIG version");
        byte[] pub = str(in);
        String ns = new String(str(in), StandardCharsets.UTF_8);
        byte[] reserved = str(in);
        String hashAlg = new String(str(in), StandardCharsets.US_ASCII);
        byte[] sigBlob = str(in);
        require(!in.hasRemaining(), "trailing data after signature");

        require(Arrays.equals(pub, trustedKeyBlob), "signed by a different key");
        require(ns.equals(namespace), "wrong signature namespace: " + ns);
        require(reserved.length == 0, "reserved field is not empty");
        require(hashAlg.equals("sha512") || hashAlg.equals("sha256"), "unsupported hash: " + hashAlg);

        ByteBuffer k = ByteBuffer.wrap(trustedKeyBlob);
        require(new String(str(k), StandardCharsets.US_ASCII).equals("ssh-ed25519"), "key is not ssh-ed25519");
        byte[] rawKey = str(k);
        require(rawKey.length == 32 && !k.hasRemaining(), "bad Ed25519 key");

        ByteBuffer s = ByteBuffer.wrap(sigBlob);
        require(new String(str(s), StandardCharsets.US_ASCII).equals("ssh-ed25519"), "signature is not ssh-ed25519");
        byte[] rawSig = str(s);
        require(rawSig.length == 64 && !s.hasRemaining(), "bad Ed25519 signature");

        byte[] digest = MessageDigest.getInstance(hashAlg.equals("sha512") ? "SHA-512" : "SHA-256").digest(message);
        ByteArrayOutputStream signed = new ByteArrayOutputStream();
        signed.write("SSHSIG".getBytes(StandardCharsets.US_ASCII));
        putStr(signed, namespace.getBytes(StandardCharsets.UTF_8));
        putStr(signed, new byte[0]);
        putStr(signed, hashAlg.getBytes(StandardCharsets.US_ASCII));
        putStr(signed, digest);

        Ed25519Signer v = new Ed25519Signer();
        v.init(false, new Ed25519PublicKeyParameters(rawKey, 0));
        byte[] data = signed.toByteArray();
        v.update(data, 0, data.length);
        require(v.verifySignature(rawSig), "signature does not match");
    }

    public static byte[] releaseKey() { return Base64.getDecoder().decode(RELEASE_KEY_B64); }

    private static byte[] str(ByteBuffer b) {
        int n = b.getInt();
        if (n < 0 || n > b.remaining()) throw new IllegalArgumentException("malformed SSH string");
        byte[] out = new byte[n];
        b.get(out);
        return out;
    }

    private static void putStr(ByteArrayOutputStream o, byte[] v) {
        o.write(v.length >>> 24); o.write(v.length >>> 16); o.write(v.length >>> 8); o.write(v.length);
        o.write(v, 0, v.length);
    }

    private static void require(boolean ok, String why) {
        if (!ok) throw new SecurityException(why);
    }
}
