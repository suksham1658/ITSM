package com.nbfc.itsm.admin;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.Location;
import com.nbfc.itsm.domain.LocationRepository;
import com.nbfc.itsm.domain.SystemSetting;
import com.nbfc.itsm.domain.SystemSettingRepository;
import com.nbfc.itsm.exception.ItsmException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Locations master (System Administrator / LOCATION_MANAGE): add/edit/delete sites with their address.
 * Also generates the IMAC hostname from the location: {@code AUTH-<first 3 letters of location>-<7-digit seq>},
 * with a single running sequence (0000001, 0000002, …).
 */
@Service
public class LocationService {

    /** Running IMAC hostname sequence, kept in system_setting. */
    static final String SEQ_KEY = "imac.hostname-seq";

    private final LocationRepository locationRepository;
    private final SystemSettingRepository settingRepository;
    private final AuditRecorder auditRecorder;

    public LocationService(LocationRepository locationRepository, SystemSettingRepository settingRepository,
                           AuditRecorder auditRecorder) {
        this.locationRepository = locationRepository;
        this.settingRepository = settingRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<Location> all() {
        return locationRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Location> active() {
        return locationRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public Location get(Long id) {
        return locationRepository.findById(id).orElseThrow(() -> new ItsmException("LOCATION_NOT_FOUND", "Location not found."));
    }

    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    @Transactional
    public Location create(String name, String address, int sortOrder) {
        String n = requireName(name);
        if (locationRepository.existsByNameIgnoreCase(n)) {
            throw new ItsmException("LOCATION_EXISTS", "A location named \"" + n + "\" already exists.");
        }
        Location l = new Location();
        l.setName(n);
        l.setAddress(clean(address, 2000));
        l.setActive(true);
        l.setSortOrder(sortOrder);
        l = locationRepository.save(l);
        auditRecorder.record("LOCATION", "CREATE", "Added location " + n, "SUCCESS");
        return l;
    }

    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    @Transactional
    public Location update(Long id, String name, String address, boolean active, int sortOrder) {
        Location l = get(id);
        String n = requireName(name);
        if (locationRepository.existsByNameIgnoreCaseAndLocationIdNot(n, id)) {
            throw new ItsmException("LOCATION_EXISTS", "Another location already uses the name \"" + n + "\".");
        }
        l.setName(n);
        l.setAddress(clean(address, 2000));
        l.setActive(active);
        l.setSortOrder(sortOrder);
        l = locationRepository.save(l);
        auditRecorder.record("LOCATION", "UPDATE", "Updated location " + n, "SUCCESS");
        return l;
    }

    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    @Transactional
    public void delete(Long id) {
        Location l = get(id);
        String n = l.getName();
        locationRepository.delete(l);
        auditRecorder.record("LOCATION", "DELETE", "Deleted location " + n, "SUCCESS");
    }

    // ------------------------------------------------------------------ IMAC hostname

    /** The hostname the next IMAC ticket for this location would get (does not consume the number). */
    @Transactional(readOnly = true)
    public String peekHostname(String locationName) {
        return format(locationName, lastSeq() + 1);
    }

    /** Allocates (consumes) the next hostname for this location. */
    @Transactional
    public String allocateHostname(String locationName) {
        long next = lastSeq() + 1;
        SystemSetting row = settingRepository.findById(SEQ_KEY).orElse(null);
        if (row == null) {
            row = new SystemSetting();
            row.setSettingKey(SEQ_KEY);
            row.setCategory("imac");
            row.setDescription("Last IMAC hostname sequence (AUTH-<loc>-NNNNNNN).");
            row.setSecret(false);
        }
        row.setSettingValue(Long.toString(next));
        settingRepository.save(row);
        return format(locationName, next);
    }

    private long lastSeq() {
        SystemSetting row = settingRepository.findById(SEQ_KEY).orElse(null);
        if (row != null && row.getSettingValue() != null) {
            try {
                return Long.parseLong(row.getSettingValue().trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    static String format(String locationName, long seq) {
        return "AUTH-" + loc3(locationName) + "-" + String.format("%07d", seq);
    }

    /** True if {@code hostname} is an AUTH-&lt;loc3&gt;-NNNNNNN value for this location (i.e. was generated for it). */
    public static boolean isHostnameFor(String hostname, String locationName) {
        if (hostname == null) {
            return false;
        }
        String prefix = "AUTH-" + loc3(locationName) + "-";
        return hostname.startsWith(prefix) && hostname.substring(prefix.length()).matches("\\d+");
    }

    /** First three letters (A–Z) of the location name, upper-case; "LOC" if the name has no letters. */
    static String loc3(String locationName) {
        String letters = locationName == null ? "" : locationName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        if (letters.isEmpty()) {
            return "LOC";
        }
        return letters.length() >= 3 ? letters.substring(0, 3) : letters;
    }

    private static String requireName(String name) {
        String n = clean(name, 128);
        if (n == null || n.length() < 2) {
            throw new ItsmException("LOCATION_NAME", "Location name is required (at least 2 characters).");
        }
        return n;
    }

    private static String clean(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
