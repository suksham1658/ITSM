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
 * Keep <b>private.key</b> secret (it signs licenses). The public key is compiled into the app: paste the printed
 * Base64 value into {@link com.nbfc.itsm.license.LicenseKeys#PUBLIC_KEY_BASE64} and rebuild. Never ship private.key.
 */
public final class LicenseKeygen {
    public static void main(String[] args) throws Exception {
        String priv = args.length > 0 ? args[0] : "private.key";
        String pub = args.length > 1 ? args[1] : "public.key";
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        Files.write(Paths.get(priv), Base64.getEncoder().encode(kp.getPrivate().getEncoded()));
        Files.write(Paths.get(pub), pubB64.getBytes("US-ASCII"));
        System.out.println("Wrote " + priv + " (KEEP SECRET) and " + pub + ".");
        System.out.println();
        System.out.println("Paste this into LicenseKeys.PUBLIC_KEY_BASE64 and rebuild the WAR:");
        System.out.println();
        System.out.println("    public static final String PUBLIC_KEY_BASE64 =");
        System.out.println("            \"" + pubB64 + "\";");
    }
}
