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
}
