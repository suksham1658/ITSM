package com.nbfc.itsm.license.tools;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * VENDOR TOOL — run once. Creates your RSA key pair.
 * <pre>
 *   java -cp itsm-portal.war!/WEB-INF/classes com.nbfc.itsm.license.tools.LicenseKeygen private.key public.key
 * </pre>
 * Keep <b>private.key</b> secret (it signs licenses). Give <b>public.key</b> to the build so it is embedded at
 * {@code src/main/resources/license/public.key}. Never ship private.key.
 */
public final class LicenseKeygen {
    public static void main(String[] args) throws Exception {
        String priv = args.length > 0 ? args[0] : "private.key";
        String pub = args.length > 1 ? args[1] : "public.key";
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        Files.write(Paths.get(priv), Base64.getEncoder().encode(kp.getPrivate().getEncoded()));
        Files.write(Paths.get(pub), Base64.getEncoder().encode(kp.getPublic().getEncoded()));
        System.out.println("Wrote " + priv + " (keep secret) and " + pub + " (embed in the app).");
    }
}
