package com.nbfc.itsm.license.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.license.License;
import com.nbfc.itsm.license.LicenseCodec;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.UUID;

/**
 * VENDOR TOOL — run for each customer / renewal. Produces a signed license file.
 * <pre>
 *   java -cp classes com.nbfc.itsm.license.tools.LicenseGenerator \
 *        --key private.key --customer "Authum Ltd" --months 12 [--grace 7] [--out authum.lic]
 * </pre>
 * Send the resulting {@code .lic} to the customer; they drop it in their {@code ITSM_CONFIG_DIR}.
 */
public final class LicenseGenerator {
    public static void main(String[] args) throws Exception {
        String keyPath = null, customer = null, out = null;
        int months = 12, grace = 7;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--key": keyPath = args[++i]; break;
                case "--customer": customer = args[++i]; break;
                case "--months": months = Integer.parseInt(args[++i]); break;
                case "--grace": grace = Integer.parseInt(args[++i]); break;
                case "--out": out = args[++i]; break;
                default: break;
            }
        }
        if (keyPath == null || customer == null) {
            System.err.println("Usage: --key private.key --customer \"Name\" [--months 12] [--grace 7] [--out file.lic]");
            System.exit(2);
            return;
        }
        if (out == null) {
            out = customer.replaceAll("[^A-Za-z0-9]+", "-").toLowerCase() + ".lic";
        }
        PrivateKey privateKey = LicenseCodec.privateKeyFromBase64(new String(Files.readAllBytes(Paths.get(keyPath))));
        Instant now = Instant.now();
        License lic = new License();
        lic.setLicenseId(UUID.randomUUID().toString());
        lic.setCustomer(customer);
        lic.setIssuedEpochMs(now.toEpochMilli());
        lic.setExpiresEpochMs(now.atZone(java.time.ZoneOffset.UTC).plusMonths(months).toInstant().toEpochMilli());
        lic.setGraceDays(grace);
        String file = new LicenseCodec(new ObjectMapper()).encode(lic, privateKey);
        Files.write(Paths.get(out), file.getBytes("UTF-8"));
        System.out.println("Wrote " + out + " for \"" + customer + "\": valid " + months
                + " months, grace " + grace + " days, id " + lic.getLicenseId());
    }
}
