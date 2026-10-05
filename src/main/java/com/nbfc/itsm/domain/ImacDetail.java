package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Extra details captured when an IMAC (Install / Move / Add / Change) request is raised. One row per IMAC ticket,
 * shown on the ticket to the requester, the System Administrator and whoever is handling it.
 */
@Entity
@Table(name = "imac_detail")
public class ImacDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "imac_detail_id")
    private Long imacDetailId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "user_sapid", length = 64)
    private String userSapId;

    @Column(name = "asset", length = 128)
    private String asset;

    @Column(name = "make", length = 128)
    private String make;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "grade", length = 64)
    private String grade;

    @Column(name = "department", length = 128)
    private String department;

    @Column(name = "serial_no", length = 128)
    private String serialNo;

    @Column(name = "ram", length = 64)
    private String ram;

    @Column(name = "contact_no", length = 64)
    private String contactNo;

    @Column(name = "office_address", length = 256)
    private String officeAddress;

    @Column(name = "location", length = 128)
    private String location;

    @Column(name = "hostname", length = 128)
    private String hostname;

    public Long getImacDetailId() { return imacDetailId; }
    public void setImacDetailId(Long id) { this.imacDetailId = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getUserSapId() { return userSapId; }
    public void setUserSapId(String v) { this.userSapId = v; }
    public String getAsset() { return asset; }
    public void setAsset(String v) { this.asset = v; }
    public String getMake() { return make; }
    public void setMake(String v) { this.make = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public String getGrade() { return grade; }
    public void setGrade(String v) { this.grade = v; }
    public String getDepartment() { return department; }
    public void setDepartment(String v) { this.department = v; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String v) { this.serialNo = v; }
    public String getRam() { return ram; }
    public void setRam(String v) { this.ram = v; }
    public String getContactNo() { return contactNo; }
    public void setContactNo(String v) { this.contactNo = v; }
    public String getOfficeAddress() { return officeAddress; }
    public void setOfficeAddress(String v) { this.officeAddress = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { this.location = v; }
    public String getHostname() { return hostname; }
    public void setHostname(String v) { this.hostname = v; }
}
