package com.nbfc.itsm.license;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The data inside a license file (signed with the vendor's private key). Dates are epoch milliseconds so the
 * file needs no date library to read. Unknown fields are ignored so newer files still open on older builds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class License {

    private int version = 1;
    private String licenseId;
    private String product = "IT Nexa";
    private String customer;
    private long issuedEpochMs;
    private long expiresEpochMs;
    private int graceDays = 7;

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getLicenseId() { return licenseId; }
    public void setLicenseId(String licenseId) { this.licenseId = licenseId; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }

    public long getIssuedEpochMs() { return issuedEpochMs; }
    public void setIssuedEpochMs(long issuedEpochMs) { this.issuedEpochMs = issuedEpochMs; }

    public long getExpiresEpochMs() { return expiresEpochMs; }
    public void setExpiresEpochMs(long expiresEpochMs) { this.expiresEpochMs = expiresEpochMs; }

    public int getGraceDays() { return graceDays; }
    public void setGraceDays(int graceDays) { this.graceDays = graceDays; }
}
