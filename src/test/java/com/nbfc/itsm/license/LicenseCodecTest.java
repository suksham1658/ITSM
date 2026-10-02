package com.nbfc.itsm.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The license file can be verified, and any edit or wrong key is rejected. */
class LicenseCodecTest {

    private static KeyPair vendor;
    private static KeyPair attacker;
    private final LicenseCodec codec = new LicenseCodec(new ObjectMapper());

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        vendor = g.generateKeyPair();
        attacker = g.generateKeyPair();
    }

    private License sample() {
        License l = new License();
        l.setLicenseId("abc");
        l.setCustomer("Authum Ltd");
        l.setIssuedEpochMs(1000L);
        l.setExpiresEpochMs(2000L);
        l.setGraceDays(7);
        return l;
    }

    @Test
    void genuineFileVerifies() throws Exception {
        String file = codec.encode(sample(), vendor.getPrivate());
        License back = codec.decode(file, vendor.getPublic());
        assertEquals("Authum Ltd", back.getCustomer());
        assertEquals(2000L, back.getExpiresEpochMs());
        assertEquals(7, back.getGraceDays());
    }

    @Test
    void editingTheExpiryBreaksTheSignature() throws Exception {
        String file = codec.encode(sample(), vendor.getPrivate());
        // Flip a character in the payload line (first Base64 line after the header).
        String[] lines = file.split("\n");
        lines[1] = lines[1].substring(0, lines[1].length() - 3) + (lines[1].endsWith("A") ? "B==" : "A==");
        String edited = String.join("\n", lines);
        assertThrows(LicenseException.class, () -> codec.decode(edited, vendor.getPublic()));
    }

    @Test
    void anotherKeyCannotForgeAnAcceptedFile() throws Exception {
        String forged = codec.encode(sample(), attacker.getPrivate());
        LicenseException ex = assertThrows(LicenseException.class, () -> codec.decode(forged, vendor.getPublic()));
        assertTrue(ex.getMessage().toLowerCase().contains("signature"));
    }

    @Test
    void wrongProductRejected() throws Exception {
        License l = sample();
        l.setProduct("Something Else");
        String file = codec.encode(l, vendor.getPrivate());
        assertThrows(LicenseException.class, () -> codec.decode(file, vendor.getPublic()));
    }

    @Test
    void embeddedPublicKeyLoads() {
        PublicKey key = LicenseCodec.embeddedPublicKey();
        assertEquals("RSA", key.getAlgorithm());
    }
}
