package com.nbfc.itsm.asset;

import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/** Backing object for the add / edit asset form. Dates are plain HTML date inputs (ISO). */
public class AssetForm {

    private Long assetId;

    @NotBlank(message = "Asset tag is required.")
    @Size(max = 32, message = "Asset tag can be at most 32 characters.")
    private String assetTag;

    @NotBlank(message = "Asset type is required.")
    @Size(max = 64)
    private String assetType;

    @Size(max = 64)
    private String serialNumber;

    private Long employeeId;

    private Long departmentId;

    @Size(max = 128)
    private String location;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate warrantyEnd;

    @NotBlank(message = "Status is required.")
    @Size(max = 32)
    private String statusCode;

    @Size(max = 64)
    private String operatingSystem;

    @Size(max = 45)
    private String ipAddress;

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

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
}
