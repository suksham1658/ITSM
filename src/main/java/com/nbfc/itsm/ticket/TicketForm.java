package com.nbfc.itsm.ticket;

public class TicketForm {

    private Long ticketTypeId;
    private Long categoryId;
    private Long subCategoryId;
    private String subject;
    private String description;
    private String priorityCode = "Medium";
    private String impactCode = "Individual";
    private String urgencyCode = "Medium";
    private String confidentialityCode = "Normal";
    private String location;
    private String applicationName;
    private boolean majorIncident;
    private String intent = "submit";
    /** Hardware only: ENTER (type the serial number) or NA (not available). */
    private String serialMode;
    private String serialNumber;

    public String getSerialMode() {
        return serialMode;
    }

    public void setSerialMode(String serialMode) {
        this.serialMode = serialMode;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Long getTicketTypeId() {
        return ticketTypeId;
    }

    public void setTicketTypeId(Long ticketTypeId) {
        this.ticketTypeId = ticketTypeId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getSubCategoryId() {
        return subCategoryId;
    }

    public void setSubCategoryId(Long subCategoryId) {
        this.subCategoryId = subCategoryId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriorityCode() {
        return priorityCode;
    }

    public void setPriorityCode(String priorityCode) {
        this.priorityCode = priorityCode;
    }

    public String getImpactCode() {
        return impactCode;
    }

    public void setImpactCode(String impactCode) {
        this.impactCode = impactCode;
    }

    public String getUrgencyCode() {
        return urgencyCode;
    }

    public void setUrgencyCode(String urgencyCode) {
        this.urgencyCode = urgencyCode;
    }

    public String getConfidentialityCode() {
        return confidentialityCode;
    }

    public void setConfidentialityCode(String confidentialityCode) {
        this.confidentialityCode = confidentialityCode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public boolean isMajorIncident() {
        return majorIncident;
    }

    public void setMajorIncident(boolean majorIncident) {
        this.majorIncident = majorIncident;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    // ---- IMAC request details (captured only when the ticket type is IMAC) ----
    private String imacUsername;
    private String imacSapId;
    private String imacAsset;
    private String imacMake;
    private String imacModel;
    private String imacGrade;
    private String imacDepartment;
    private String imacSerialNo;
    private String imacRam;
    private String imacContactNo;
    private String imacOfficeAddress;
    private String imacLocation;
    private String imacHostname;
    private Long imacLocationId;

    public Long getImacLocationId() { return imacLocationId; }
    public void setImacLocationId(Long v) { this.imacLocationId = v; }

    public String getImacUsername() { return imacUsername; }
    public void setImacUsername(String v) { this.imacUsername = v; }
    public String getImacSapId() { return imacSapId; }
    public void setImacSapId(String v) { this.imacSapId = v; }
    public String getImacAsset() { return imacAsset; }
    public void setImacAsset(String v) { this.imacAsset = v; }
    public String getImacMake() { return imacMake; }
    public void setImacMake(String v) { this.imacMake = v; }
    public String getImacModel() { return imacModel; }
    public void setImacModel(String v) { this.imacModel = v; }
    public String getImacGrade() { return imacGrade; }
    public void setImacGrade(String v) { this.imacGrade = v; }
    public String getImacDepartment() { return imacDepartment; }
    public void setImacDepartment(String v) { this.imacDepartment = v; }
    public String getImacSerialNo() { return imacSerialNo; }
    public void setImacSerialNo(String v) { this.imacSerialNo = v; }
    public String getImacRam() { return imacRam; }
    public void setImacRam(String v) { this.imacRam = v; }
    public String getImacContactNo() { return imacContactNo; }
    public void setImacContactNo(String v) { this.imacContactNo = v; }
    public String getImacOfficeAddress() { return imacOfficeAddress; }
    public void setImacOfficeAddress(String v) { this.imacOfficeAddress = v; }
    public String getImacLocation() { return imacLocation; }
    public void setImacLocation(String v) { this.imacLocation = v; }
    public String getImacHostname() { return imacHostname; }
    public void setImacHostname(String v) { this.imacHostname = v; }
}
