package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "config_change_request")
public class ConfigChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_change_request_id")
    private Long configChangeRequestId;

    @Column(name = "change_type", nullable = false, length = 64)
    private String changeType;

    @Column(name = "entity_name", nullable = false, length = 128)
    private String entityName;

    @Column(name = "entity_key", length = 128)
    private String entityKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String payloadJson;

    @Column(name = "previous_json", columnDefinition = "nvarchar(max)")
    private String previousJson;

    @Column(name = "description", nullable = false, length = 512)
    private String description;

    @Column(name = "status_code", nullable = false, length = 32)
    private String statusCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Employee requestedBy;

    @Column(name = "requested_at_utc", nullable = false)
    private Instant requestedAtUtc = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Employee reviewedBy;

    @Column(name = "reviewed_at_utc")
    private Instant reviewedAtUtc;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    public Long getConfigChangeRequestId() {
        return configChangeRequestId;
    }

    public void setConfigChangeRequestId(Long configChangeRequestId) {
        this.configChangeRequestId = configChangeRequestId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityKey() {
        return entityKey;
    }

    public void setEntityKey(String entityKey) {
        this.entityKey = entityKey;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getPreviousJson() {
        return previousJson;
    }

    public void setPreviousJson(String previousJson) {
        this.previousJson = previousJson;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public Employee getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Employee requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getRequestedAtUtc() {
        return requestedAtUtc;
    }

    public void setRequestedAtUtc(Instant requestedAtUtc) {
        this.requestedAtUtc = requestedAtUtc;
    }

    public Employee getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Employee reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAtUtc() {
        return reviewedAtUtc;
    }

    public void setReviewedAtUtc(Instant reviewedAtUtc) {
        this.reviewedAtUtc = reviewedAtUtc;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }
}
