package com.nbfc.itsm.asset;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.Asset;
import com.nbfc.itsm.domain.AssetRepository;
import com.nbfc.itsm.domain.Department;
import com.nbfc.itsm.domain.DepartmentRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.exception.ItsmException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asset Management: list/search, add, edit and delete IT assets (ASSET_MANAGE). Every change is written to the
 * audit trail. Delete is blocked for an asset still linked to tickets — retire it instead.
 */
@Service
@PreAuthorize("hasAuthority('ASSET_MANAGE')")
public class AssetService {

    public static final int PAGE_SIZE = 25;

    /** Asset lifecycle statuses (code → label), shown in the dropdown and filter. */
    public static final Map<String, String> STATUSES;
    static {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("IN_STOCK", "In stock");
        m.put("ASSIGNED", "Assigned");
        m.put("IN_REPAIR", "In repair");
        m.put("RETIRED", "Retired");
        STATUSES = Collections.unmodifiableMap(m);
    }

    private final AssetRepository assetRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditRecorder auditRecorder;

    public AssetService(AssetRepository assetRepository, EmployeeRepository employeeRepository,
                        DepartmentRepository departmentRepository, AuditRecorder auditRecorder) {
        this.assetRepository = assetRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<Asset> search(Filter f, int page) {
        return assetRepository.findAll(spec(f), PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "updatedAtUtc", "assetId")));
    }

    @Transactional(readOnly = true)
    public Asset get(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ItsmException("ASSET_NOT_FOUND", "Asset not found."));
    }

    @Transactional
    public Asset create(AssetForm form) {
        String tag = requireTag(form.getAssetTag());
        if (assetRepository.existsByAssetTagIgnoreCase(tag)) {
            throw new ItsmException("ASSET_TAG_TAKEN", "An asset with tag \"" + tag + "\" already exists.");
        }
        Asset a = new Asset();
        apply(form, a, tag);
        a = assetRepository.save(a);
        auditRecorder.record("ASSET", "CREATE", "Added asset " + a.getAssetTag()
                + " (" + a.getAssetType() + ", " + a.getStatusCode() + ")", "SUCCESS");
        return a;
    }

    @Transactional
    public Asset update(Long id, AssetForm form) {
        Asset a = get(id);
        String tag = requireTag(form.getAssetTag());
        if (assetRepository.existsByAssetTagIgnoreCaseAndAssetIdNot(tag, id)) {
            throw new ItsmException("ASSET_TAG_TAKEN", "Another asset already uses tag \"" + tag + "\".");
        }
        String before = a.getAssetTag() + " / " + a.getStatusCode()
                + (a.getAssignedTo() != null ? " / " + a.getAssignedTo().getDisplayName() : "");
        apply(form, a, tag);
        a = assetRepository.save(a);
        String after = a.getAssetTag() + " / " + a.getStatusCode()
                + (a.getAssignedTo() != null ? " / " + a.getAssignedTo().getDisplayName() : "");
        auditRecorder.record("ASSET", "UPDATE", "Asset " + a.getAssetTag() + ": " + before + " → " + after, "SUCCESS");
        return a;
    }

    @Transactional
    public void delete(Long id) {
        Asset a = get(id);
        String tag = a.getAssetTag();
        try {
            assetRepository.delete(a);
            assetRepository.flush(); // surface any FK violation here, inside the try
        } catch (DataIntegrityViolationException ex) {
            throw new ItsmException("ASSET_IN_USE",
                    "Asset " + tag + " is linked to one or more tickets and cannot be deleted. "
                            + "Set its status to Retired instead.");
        }
        auditRecorder.record("ASSET", "DELETE", "Deleted asset " + tag, "SUCCESS");
    }

    // ------------------------------------------------------------------ form helpers

    @Transactional(readOnly = true)
    public AssetForm toForm(Asset a) {
        AssetForm f = new AssetForm();
        f.setAssetId(a.getAssetId());
        f.setAssetTag(a.getAssetTag());
        f.setAssetType(a.getAssetType());
        f.setSerialNumber(a.getSerialNumber());
        f.setEmployeeId(a.getAssignedTo() == null ? null : a.getAssignedTo().getEmployeeId());
        f.setDepartmentId(a.getDepartment() == null ? null : a.getDepartment().getDepartmentId());
        f.setLocation(a.getLocation());
        f.setPurchaseDate(a.getPurchaseDate());
        f.setWarrantyEnd(a.getWarrantyEnd());
        f.setStatusCode(a.getStatusCode());
        f.setOperatingSystem(a.getOperatingSystem());
        f.setIpAddress(a.getIpAddress());
        return f;
    }

    @Transactional(readOnly = true)
    public List<Employee> activeEmployees() {
        return employeeRepository.findByPortalActiveTrueOrderByDisplayNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Department> departments() {
        return departmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<String> types() {
        return assetRepository.distinctTypes();
    }

    public Map<String, String> statuses() {
        return STATUSES;
    }

    // ------------------------------------------------------------------ internals

    private void apply(AssetForm form, Asset a, String tag) {
        if (!STATUSES.containsKey(form.getStatusCode())) {
            throw new ItsmException("ASSET_STATUS", "Choose a valid status.");
        }
        a.setAssetTag(tag);
        a.setAssetType(clean(form.getAssetType()));
        a.setSerialNumber(clean(form.getSerialNumber()));
        a.setLocation(clean(form.getLocation()));
        a.setPurchaseDate(form.getPurchaseDate());
        a.setWarrantyEnd(form.getWarrantyEnd());
        a.setStatusCode(form.getStatusCode());
        a.setOperatingSystem(clean(form.getOperatingSystem()));
        a.setIpAddress(clean(form.getIpAddress()));
        a.setAssignedTo(form.getEmployeeId() == null ? null : employeeRepository.findById(form.getEmployeeId())
                .orElseThrow(() -> new ItsmException("ASSET_EMPLOYEE", "Selected employee was not found.")));
        a.setDepartment(form.getDepartmentId() == null ? null : departmentRepository.findById(form.getDepartmentId())
                .orElseThrow(() -> new ItsmException("ASSET_DEPARTMENT", "Selected department was not found.")));
    }

    private Specification<Asset> spec(Filter f) {
        final String text = trim(f.getText());
        final String type = trim(f.getType());
        final String status = trim(f.getStatus());
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<Predicate>();
            if (text != null) {
                String like = "%" + text.toLowerCase() + "%";
                and.add(cb.or(
                        cb.like(cb.lower(root.get("assetTag")), like),
                        cb.like(cb.lower(root.get("serialNumber")), like),
                        cb.like(cb.lower(root.get("assetType")), like),
                        cb.like(cb.lower(root.get("location")), like),
                        cb.like(cb.lower(root.get("ipAddress")), like),
                        cb.like(cb.lower(root.get("operatingSystem")), like)));
            }
            if (type != null) {
                and.add(cb.equal(root.get("assetType"), type));
            }
            if (status != null) {
                and.add(cb.equal(root.get("statusCode"), status));
            }
            return cb.and(and.toArray(new Predicate[0]));
        };
    }

    private static String requireTag(String tag) {
        String t = clean(tag);
        if (t == null) {
            throw new ItsmException("ASSET_TAG", "Asset tag is required.");
        }
        return t;
    }

    private static String clean(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** List / search filters (all optional). */
    public static class Filter {
        private String text;
        private String type;
        private String status;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
