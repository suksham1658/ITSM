package com.nbfc.itsm.license;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads and writes the license file. The file is two Base64 lines — the JSON payload and an RSA (SHA256withRSA)
 * signature over that exact payload — under a comment header. Verifying needs only the public key; producing a
 * file needs the vendor's private key. So the file cannot be edited or forged without that private key.
 */
public final class LicenseCodec {

    public static final String FILE_NAME = "itsm-license.lic";
    private static final String ALG = "SHA256withRSA";
    private static final String HEADER = "# IT Nexa license file — do not edit. Managed by your software vendor.";

    private final ObjectMapper mapper;

    public LicenseCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Vendor side: build the signed file text for a license. */
    public String encode(License license, PrivateKey privateKey) throws Exception {
        byte[] payload = mapper.writeValueAsBytes(license);
        Signature sig = Signature.getInstance(ALG);
        sig.initSign(privateKey);
        sig.update(payload);
        byte[] signature = sig.sign();
        return HEADER + "\n"
                + Base64.getEncoder().encodeToString(payload) + "\n"
                + Base64.getEncoder().encodeToString(signature) + "\n";
    }

    /** App side: verify the signature and return the license, or throw if anything is wrong. */
    public License decode(String fileContent, PublicKey publicKey) throws LicenseException {
        try {
            String payloadB64 = null, sigB64 = null;
            for (String raw : fileContent.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (payloadB64 == null) {
                    payloadB64 = line;
                } else if (sigB64 == null) {
                    sigB64 = line;
                }
            }
            if (payloadB64 == null || sigB64 == null) {
                throw new LicenseException("License file is not in the expected format.");
            }
            byte[] payload = Base64.getDecoder().decode(payloadB64);
            byte[] signature = Base64.getDecoder().decode(sigB64);
            Signature sig = Signature.getInstance(ALG);
            sig.initVerify(publicKey);
            sig.update(payload);
            if (!sig.verify(signature)) {
                throw new LicenseException("License signature does not match — the file was edited or is not genuine.");
            }
            License license = mapper.readValue(payload, License.class);
            if (!"IT Nexa".equalsIgnoreCase(license.getProduct())) {
                throw new LicenseException("License is not issued for this product.");
            }
            return license;
        } catch (LicenseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LicenseException("License file could not be read: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ key loading

    public static PublicKey publicKeyFromBase64(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64.trim());
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid public key", ex);
        }
    }

    public static PrivateKey privateKeyFromBase64(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64.trim());
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid private key", ex);
        }
    }

    /**
     * The vendor public key, compiled into the application from {@link LicenseKeys#PUBLIC_KEY_BASE64}. Unlike a file
     * in the WAR, this cannot be swapped by unzipping the archive — it lives in bytecode. Parsed once and reused.
     */
    public static PublicKey embeddedPublicKey() {
        PublicKey key = EMBEDDED;
        if (key == null) {
            synchronized (LicenseCodec.class) {
                if (EMBEDDED == null) {
                    EMBEDDED = publicKeyFromBase64(LicenseKeys.PUBLIC_KEY_BASE64);
                }
                key = EMBEDDED;
            }
        }
        return key;
    }

    private static volatile PublicKey EMBEDDED;
}
