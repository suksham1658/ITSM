package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "attachment_policy")
public class AttachmentPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_policy_id")
    private Long attachmentPolicyId;

    @Column(name = "max_bytes", nullable = false)
    private int maxBytes;

    @Column(name = "allowed_extensions", nullable = false, length = 512)
    private String allowedExtensions;

    @Column(name = "allowed_mime_types", nullable = false, length = 1000)
    private String allowedMimeTypes;

    @Column(name = "virus_scan_required", nullable = false)
    private boolean virusScanRequired;

    public Long getAttachmentPolicyId() {
        return attachmentPolicyId;
    }

    public void setAttachmentPolicyId(Long attachmentPolicyId) {
        this.attachmentPolicyId = attachmentPolicyId;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public String getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(String allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public String getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    public void setAllowedMimeTypes(String allowedMimeTypes) {
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public boolean isVirusScanRequired() {
        return virusScanRequired;
    }

    public void setVirusScanRequired(boolean virusScanRequired) {
        this.virusScanRequired = virusScanRequired;
    }
}
