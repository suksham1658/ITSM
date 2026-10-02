package com.nbfc.itsm.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.domain.SystemSetting;
import com.nbfc.itsm.domain.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;

/**
 * Checks the license on demand (cheaply cached) and installs renewals.
 * <ul>
 *   <li>Reads {@code <ITSM_CONFIG_DIR>/itsm-license.lic} and verifies it with the embedded public key.</li>
 *   <li>Within the term: VALID. After expiry but within the file's grace days: GRACE. Later: EXPIRED.</li>
 *   <li>Clock-tamper guard: the last time seen is kept in {@code system_setting}; if the clock jumps far back,
 *       the status becomes TAMPERED.</li>
 * </ul>
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final String LAST_SEEN_KEY = "license.last-seen-utc";
    private static final Duration CLOCK_TOLERANCE = Duration.ofHours(26);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final SystemSettingRepository settingRepository;
    private final LicenseCodec codec;
    private final String publicKeyResource;
    private Path configDir;
    private PublicKey publicKey;

    private volatile LicenseState cached;
    private volatile long cachedAt;

    public LicenseService(@Value("${ITSM_CONFIG_DIR:D:/itsm-config}") String configDir,
                          @Value("${itsm.license.public-key-resource:license/public.key}") String publicKeyResource,
                          SystemSettingRepository settingRepository, ObjectMapper objectMapper) {
        this.configDir = Paths.get(configDir);
        this.publicKeyResource = publicKeyResource;
        this.settingRepository = settingRepository;
        this.codec = new LicenseCodec(objectMapper);
    }

    @PostConstruct
    void init() {
        this.publicKey = LicenseCodec.publicKeyFromResource(publicKeyResource);
    }

    /** The current license state, recomputed at most once every {@link #CACHE_TTL}. */
    public LicenseState current() {
        long now = System.currentTimeMillis();
        LicenseState c = cached;
        if (c != null && now - cachedAt < CACHE_TTL.toMillis()) {
            return c;
        }
        LicenseState fresh = compute();
        cached = fresh;
        cachedAt = now;
        return fresh;
    }

    /** Forces a fresh check (used by the License page and right after an install). */
    public LicenseState refresh() {
        LicenseState fresh = compute();
        cached = fresh;
        cachedAt = System.currentTimeMillis();
        return fresh;
    }

    /**
     * Saves a new license file after checking it is genuine. Throws {@link LicenseException} if the uploaded
     * file is not valid, so nothing is overwritten with a bad file.
     */
    public LicenseState install(byte[] bytes) throws LicenseException {
        String content = new String(bytes, StandardCharsets.UTF_8);
        codec.decode(content, publicKey); // verify before writing
        try {
            Files.createDirectories(configDir);
            Files.write(configDir.resolve(LicenseCodec.FILE_NAME), bytes);
        } catch (Exception ex) {
            throw new LicenseException("Could not save the license file to " + configDir + ": " + ex.getMessage());
        }
        return refresh();
    }

    // ------------------------------------------------------------------ internals

    private LicenseState compute() {
        Path file = configDir.resolve(LicenseCodec.FILE_NAME);
        if (!Files.isReadable(file)) {
            return new LicenseState(LicenseStatus.MISSING, null, 0, 0, 0, "No license file at " + file);
        }
        License lic;
        try {
            lic = codec.decode(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), publicKey);
        } catch (LicenseException ex) {
            log.warn("License invalid: {}", ex.getMessage());
            return new LicenseState(LicenseStatus.INVALID, null, 0, 0, 0, ex.getMessage());
        } catch (Exception ex) {
            return new LicenseState(LicenseStatus.INVALID, null, 0, 0, 0, ex.toString());
        }

        Instant now = Instant.now();
        if (clockMovedBack(now)) {
            return new LicenseState(LicenseStatus.TAMPERED, lic.getCustomer(), lic.getExpiresEpochMs(),
                    lic.getGraceDays(), 0, "Server clock moved backwards.");
        }

        long nowMs = now.toEpochMilli();
        long graceEnd = lic.getExpiresEpochMs() + (long) Math.max(0, lic.getGraceDays()) * DAY_MS;
        if (nowMs <= lic.getExpiresEpochMs()) {
            return new LicenseState(LicenseStatus.VALID, lic.getCustomer(), lic.getExpiresEpochMs(),
                    lic.getGraceDays(), 0, "Valid.");
        }
        if (nowMs <= graceEnd) {
            long remaining = (long) Math.ceil((graceEnd - nowMs) / (double) DAY_MS);
            return new LicenseState(LicenseStatus.GRACE, lic.getCustomer(), lic.getExpiresEpochMs(),
                    lic.getGraceDays(), Math.max(0, remaining), "In grace period.");
        }
        return new LicenseState(LicenseStatus.EXPIRED, lic.getCustomer(), lic.getExpiresEpochMs(),
                lic.getGraceDays(), 0, "Grace period over.");
    }

    /** Records the latest time seen; returns true if the clock is now well before the last time seen. */
    private boolean clockMovedBack(Instant now) {
        try {
            SystemSetting row = settingRepository.findById(LAST_SEEN_KEY).orElse(null);
            long last = 0;
            if (row != null && row.getSettingValue() != null) {
                try {
                    last = Long.parseLong(row.getSettingValue().trim());
                } catch (NumberFormatException ignored) {
                    last = 0;
                }
            }
            boolean tampered = last > 0 && now.toEpochMilli() < last - CLOCK_TOLERANCE.toMillis();
            if (!tampered && now.toEpochMilli() > last + Duration.ofHours(1).toMillis()) {
                if (row == null) {
                    row = new SystemSetting();
                    row.setSettingKey(LAST_SEEN_KEY);
                    row.setCategory("license");
                    row.setDescription("Latest time the app has seen (clock-tamper guard for licensing).");
                    row.setSecret(false);
                }
                row.setSettingValue(Long.toString(now.toEpochMilli()));
                settingRepository.save(row);
            }
            return tampered;
        } catch (RuntimeException ex) {
            // Database not ready or unreadable: do not treat as tampering.
            log.debug("Clock-tamper check skipped: {}", ex.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ test hooks

    void setConfigDirForTest(Path dir) { this.configDir = dir; }
    void setPublicKeyForTest(PublicKey key) { this.publicKey = key; }
    void clearCacheForTest() { this.cached = null; this.cachedAt = 0; }
}
