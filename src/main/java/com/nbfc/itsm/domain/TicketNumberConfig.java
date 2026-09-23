package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "ticket_number_config")
public class TicketNumberConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_number_config_id")
    private Long ticketNumberConfigId;

    @Column(name = "prefix", nullable = false, length = 16)
    private String prefix;

    @Column(name = "include_year", nullable = false)
    private boolean includeYear = true;

    @Column(name = "padding", nullable = false)
    private int padding = 6;

    @Column(name = "sequence_year", nullable = false)
    private int sequenceYear;

    @Column(name = "last_allocated", nullable = false)
    private long lastAllocated;

    public Long getTicketNumberConfigId() {
        return ticketNumberConfigId;
    }

    public void setTicketNumberConfigId(Long ticketNumberConfigId) {
        this.ticketNumberConfigId = ticketNumberConfigId;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public boolean isIncludeYear() {
        return includeYear;
    }

    public void setIncludeYear(boolean includeYear) {
        this.includeYear = includeYear;
    }

    public int getPadding() {
        return padding;
    }

    public void setPadding(int padding) {
        this.padding = padding;
    }

    public int getSequenceYear() {
        return sequenceYear;
    }

    public void setSequenceYear(int sequenceYear) {
        this.sequenceYear = sequenceYear;
    }

    public long getLastAllocated() {
        return lastAllocated;
    }

    public void setLastAllocated(long lastAllocated) {
        this.lastAllocated = lastAllocated;
    }
}
