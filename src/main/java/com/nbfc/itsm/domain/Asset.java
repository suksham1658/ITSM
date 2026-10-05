package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An IT asset (laptop, desktop, server, phone…) tracked in the portal and optionally assigned to an employee.
 * Maps the existing {@code asset} table. The {@code assignedTo} / {@code department} links are eager so the list
 * and edit screens can show names with {@code open-in-view=false}.
 */
@Entity
@Table(name = "asset")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "asset_tag", nullable = false, length = 32)
    private String assetTag;

    @Column(name = "asset_type", nullable = false, length = 64)
    private String assetType;

    @Column(name = "serial_number", length = 64)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    private Employee assignedTo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "location", length = 128)
    private String location;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_end")
    private LocalDate warrantyEnd;

    @Column(name = "status_code", nullable = false, length = 32)
    private String statusCode;

    @Column(name = "operating_system", length = 64)
    private String operatingSystem;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAtUtc = Instant.now();
    }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public Employee getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Employee assignedTo) { this.assignedTo = assignedTo; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public LocalDate getWarrantyEnd() { return warrantyEnd; }
    public void setWarrantyEnd(LocalDate warrantyEnd) { this.warrantyEnd = warrantyEnd; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public Instant getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(Instant updatedAtUtc) { this.updatedAtUtc = updatedAtUtc; }
}
