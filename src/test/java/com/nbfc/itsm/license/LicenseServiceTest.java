package com.nbfc.itsm.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.domain.SystemSetting;
import com.nbfc.itsm.domain.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The service maps a license file to VALID / GRACE / EXPIRED / MISSING / INVALID / TAMPERED. */
class LicenseServiceTest {

    private KeyPair vendor;
    private LicenseCodec codec;
    private SystemSettingRepository settings;
    private LicenseService service;
    @TempDir Path configDir;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        vendor = g.generateKeyPair();
        codec = new LicenseCodec(new ObjectMapper());
        settings = mock(SystemSettingRepository.class);
        lenient().when(settings.findById(anyString())).thenReturn(Optional.empty());
        lenient().when(settings.save(any(SystemSetting.class))).thenAnswer(i -> i.getArgument(0));
        service = new LicenseService("D:/itsm-config", settings, new ObjectMapper());
        service.setConfigDirForTest(configDir);
        service.setPublicKeyForTest(vendor.getPublic());
    }

    private void writeLicense(Instant expires, int graceDays) throws Exception {
        License l = new License();
        l.setLicenseId("t");
        l.setCustomer("Authum Ltd");
        l.setIssuedEpochMs(Instant.now().minus(300, ChronoUnit.DAYS).toEpochMilli());
        l.setExpiresEpochMs(expires.toEpochMilli());
        l.setGraceDays(graceDays);
        Files.write(configDir.resolve(LicenseCodec.FILE_NAME), codec.encode(l, vendor.getPrivate()).getBytes("UTF-8"));
        service.clearCacheForTest();
    }

    @Test
    void validDuringTheTerm() throws Exception {
        writeLicense(Instant.now().plus(100, ChronoUnit.DAYS), 7);
        LicenseState s = service.refresh();
        assertEquals(LicenseStatus.VALID, s.getStatus());
        assertEquals("Authum Ltd", s.getCustomer());
        assertEquals(true, s.isOperational());
    }

    @Test
    void graceAfterExpiryWithinWindow() throws Exception {
        writeLicense(Instant.now().minus(2, ChronoUnit.DAYS), 7);
        LicenseState s = service.refresh();
        assertEquals(LicenseStatus.GRACE, s.getStatus());
        assertEquals(true, s.isOperational(), "grace still runs for everyone");
        assertEquals(true, s.getGraceDaysRemaining() >= 4 && s.getGraceDaysRemaining() <= 5);
        assertEquals(true, s.sysAdminNotice().contains("grace period"));
    }

    @Test
    void blockedAfterGrace() throws Exception {
        writeLicense(Instant.now().minus(20, ChronoUnit.DAYS), 7);
        LicenseState s = service.refresh();
        assertEquals(LicenseStatus.EXPIRED, s.getStatus());
        assertEquals(true, s.isBlocking());
    }

    @Test
    void missingWhenNoFile() {
        LicenseState s = service.refresh();
        assertEquals(LicenseStatus.MISSING, s.getStatus());
        assertEquals(true, s.isBlocking());
    }

    @Test
    void invalidWhenSignedByAnotherKey() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        License l = new License();
        l.setCustomer("X");
        l.setExpiresEpochMs(Instant.now().plus(100, ChronoUnit.DAYS).toEpochMilli());
        Files.write(configDir.resolve(LicenseCodec.FILE_NAME), codec.encode(l, g.generateKeyPair().getPrivate()).getBytes("UTF-8"));
        service.clearCacheForTest();
        assertEquals(LicenseStatus.INVALID, service.refresh().getStatus());
    }

    @Test
    void clockMovedBackIsTampered() throws Exception {
        // Last-seen recorded far in the future → turning the clock back is detected.
        SystemSetting row = new SystemSetting();
        row.setSettingKey("license.last-seen-utc");
        row.setSettingValue(Long.toString(Instant.now().plus(30, ChronoUnit.DAYS).toEpochMilli()));
        when(settings.findById("license.last-seen-utc")).thenReturn(Optional.of(row));
        writeLicense(Instant.now().plus(100, ChronoUnit.DAYS), 7);
        assertEquals(LicenseStatus.TAMPERED, service.refresh().getStatus());
    }

    @Test
    void installRejectsAnInvalidFileAndKeepsNothing() {
        assertThrows(LicenseException.class, () -> service.install("not a license".getBytes()));
        assertEquals(LicenseStatus.MISSING, service.refresh().getStatus());
    }

    @Test
    void installAcceptsAGenuineFile() throws Exception {
        License l = new License();
        l.setCustomer("Authum Ltd");
        l.setExpiresEpochMs(Instant.now().plus(365, ChronoUnit.DAYS).toEpochMilli());
        l.setGraceDays(7);
        LicenseState s = service.install(codec.encode(l, vendor.getPrivate()).getBytes("UTF-8"));
        assertEquals(LicenseStatus.VALID, s.getStatus());
    }
}
