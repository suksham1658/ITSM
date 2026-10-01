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

@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "public_number", nullable = false, unique = true, length = 32)
    private String publicNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_category_id", nullable = false)
    private SubCategory subCategory;

    @Column(name = "subject", nullable = false, length = 256)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "nvarchar(max)")
    private String description;

    @Column(name = "priority_code", nullable = false, length = 16)
    private String priorityCode;

    @Column(name = "impact_code", nullable = false, length = 32)
    private String impactCode;

    @Column(name = "urgency_code", nullable = false, length = 16)
    private String urgencyCode;

    @Column(name = "confidentiality_code", nullable = false, length = 32)
    private String confidentialityCode;

    @Column(name = "location", length = 128)
    private String location;

    @Column(name = "application_name", length = 128)
    private String applicationName;

    /** Hardware tickets: the device serial number, or "Not available". */
    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "required_date")
    private LocalDate requiredDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private Employee requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "status_code", nullable = false, length = 32)
    private String statusCode;

    @Column(name = "progress_code", length = 64)
    private String progressCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_group_id")
    private AssignmentGroup assignedGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_implementor_id")
    private Employee assignedImplementor;

    @Column(name = "workflow_instance_id")
    private Long workflowInstanceId;

    @Column(name = "major_incident", nullable = false)
    private boolean majorIncident;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (createdAtUtc == null) {
            createdAtUtc = now;
        }
        if (updatedAtUtc == null) {
            updatedAtUtc = now;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAtUtc = Instant.now();
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getPublicNumber() {
        return publicNumber;
    }

    public void setPublicNumber(String publicNumber) {
        this.publicNumber = publicNumber;
    }

    public TicketType getTicketType() {
        return ticketType;
    }

    public void setTicketType(TicketType ticketType) {
        this.ticketType = ticketType;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public SubCategory getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(SubCategory subCategory) {
        this.subCategory = subCategory;
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

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public LocalDate getRequiredDate() {
        return requiredDate;
    }

    public void setRequiredDate(LocalDate requiredDate) {
        this.requiredDate = requiredDate;
    }

    public Employee getRequester() {
        return requester;
    }

    public void setRequester(Employee requester) {
        this.requester = requester;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getProgressCode() {
        return progressCode;
    }

    public void setProgressCode(String progressCode) {
        this.progressCode = progressCode;
    }

    public AssignmentGroup getAssignedGroup() {
        return assignedGroup;
    }

    public void setAssignedGroup(AssignmentGroup assignedGroup) {
        this.assignedGroup = assignedGroup;
    }

    public Employee getAssignedImplementor() {
        return assignedImplementor;
    }

    public void setAssignedImplementor(Employee assignedImplementor) {
        this.assignedImplementor = assignedImplementor;
    }

    public Long getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(Long workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public boolean isMajorIncident() {
        return majorIncident;
    }

    public void setMajorIncident(boolean majorIncident) {
        this.majorIncident = majorIncident;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Instant getCreatedAtUtc() {
        return createdAtUtc;
    }

    public void setCreatedAtUtc(Instant createdAtUtc) {
        this.createdAtUtc = createdAtUtc;
    }

    public Instant getUpdatedAtUtc() {
        return updatedAtUtc;
    }

    public void setUpdatedAtUtc(Instant updatedAtUtc) {
        this.updatedAtUtc = updatedAtUtc;
    }
}
