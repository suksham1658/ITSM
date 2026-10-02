package com.nbfc.itsm.license;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** A computed snapshot of the license for the UI and the enforcement filter. */
public final class LicenseState {

    private static final DateTimeFormatter IST =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    private final LicenseStatus status;
    private final String customer;
    private final long expiresEpochMs;
    private final int graceDaysTotal;
    private final long graceDaysRemaining;
    private final String detail;

    public LicenseState(LicenseStatus status, String customer, long expiresEpochMs,
                        int graceDaysTotal, long graceDaysRemaining, String detail) {
        this.status = status;
        this.customer = customer;
        this.expiresEpochMs = expiresEpochMs;
        this.graceDaysTotal = graceDaysTotal;
        this.graceDaysRemaining = graceDaysRemaining;
        this.detail = detail;
    }

    public LicenseStatus getStatus() { return status; }
    public String getCustomer() { return customer; }
    public long getExpiresEpochMs() { return expiresEpochMs; }
    public int getGraceDaysTotal() { return graceDaysTotal; }
    public long getGraceDaysRemaining() { return graceDaysRemaining; }
    public String getDetail() { return detail; }

    public String getExpiresOn() { return expiresEpochMs > 0 ? IST.format(Instant.ofEpochMilli(expiresEpochMs)) : "—"; }

    /** VALID or within grace: the portal runs for everyone. */
    public boolean isOperational() { return status == LicenseStatus.VALID || status == LicenseStatus.GRACE; }

    /** Past grace / missing / invalid / tampered: only the System Administrator may sign in. */
    public boolean isBlocking() { return !isOperational(); }

    /** True only while running on borrowed time (expired but inside the grace window). */
    public boolean isGrace() { return status == LicenseStatus.GRACE; }

    /** The message shown only to the System Administrator (null during a valid term). */
    public String sysAdminNotice() {
        switch (status) {
            case GRACE:
                return "License expired — running on grace period, " + graceDaysRemaining + " of " + graceDaysTotal
                        + " day" + (graceDaysTotal == 1 ? "" : "s") + " remaining. Please install a renewed license.";
            case EXPIRED:
                return "License expired and the grace period is over. Only a System Administrator can sign in. "
                        + "Install a renewed license to restore access for everyone.";
            case MISSING:
                return "No license is installed. Only a System Administrator can sign in. Install a license file to continue.";
            case INVALID:
                return "The license file is invalid (edited, corrupted or not issued for this product). Install a valid license.";
            case TAMPERED:
                return "The server clock appears to have moved backwards. Licensing is suspended until this is corrected "
                        + "or a renewed license is installed.";
            default:
                return null;
        }
    }
}
