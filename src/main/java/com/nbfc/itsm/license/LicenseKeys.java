package com.nbfc.itsm.license;

/**
 * The vendor's PUBLIC key, compiled into the application (not a loose file in the WAR). This is what makes the
 * licensing hard to bypass: to trust a different key a customer would have to decompile, edit this constant and
 * recompile — not merely swap a file inside the WAR.
 *
 * <p>The public key is not secret. To re-key (e.g. after rotating a lost/leaked private key): run
 * {@code LicenseKeygen}, copy the printed Base64 public key over the value below, and rebuild the WAR.</p>
 */
public final class LicenseKeys {

    private LicenseKeys() {
    }

    /** Base64 (X.509) RSA public key. Replace only via the LicenseKeygen workflow, then rebuild. */
    public static final String PUBLIC_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqHBRDx6UkQHmHNdNK5ZYCT4K0ct3tPKX3AeSaVBDbFljxPjxiBUzUGv/OQPW/oMA8I1jQtkG7clnyaxt9qwQ+oU3nlPR8wLtgM1SI8U0vgZc/wFiMGZJWl0yP274vtLDVapCBVyOOK4xg47PfefydFoVJZKPlh2vOw1UGNflu5bunv5bZg2Zs+mEJPxeDcQxbwdnCcKT/HOgmPo3gSLuOxnLjBV25jaBGOBh7QqSgCFvT1Wu9FAzpdQa3BLqPe9cejdiR1n8oTQFanFlu5LZJyfN7uoc6FjqzrIg0Z8sKgX5uwrnxxZK64yiWGC7ys6ZpdKx0c4bXjTGBF3WiwR7NQIDAQAB";
}
