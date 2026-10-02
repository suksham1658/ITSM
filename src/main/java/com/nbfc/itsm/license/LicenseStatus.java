package com.nbfc.itsm.license;

/** Outcome of checking the license. Only VALID and GRACE let the portal run for everyone. */
public enum LicenseStatus {
    /** Signed, not expired. Nothing is shown to anyone. */
    VALID,
    /** Expired, but still within the grace window; only the System Administrator is told. */
    GRACE,
    /** Past the grace window. Only the System Administrator can sign in, to install a renewal. */
    EXPIRED,
    /** No license file present. */
    MISSING,
    /** File present but the signature or contents are not valid (edited, forged, wrong product). */
    INVALID,
    /** The server clock was moved backwards to dodge expiry. */
    TAMPERED
}
