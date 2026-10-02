package com.nbfc.itsm.license;

/** A license file that is missing a part, has a bad signature, or is not for this product. */
public class LicenseException extends Exception {
    public LicenseException(String message) {
        super(message);
    }
}
