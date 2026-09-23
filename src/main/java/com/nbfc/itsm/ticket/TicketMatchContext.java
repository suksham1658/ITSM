package com.nbfc.itsm.ticket;

public class TicketMatchContext {

    private final String ticketTypeName;
    private final String ticketTypeCode;
    private final String categoryName;
    private final String subCategoryName;
    private final String confidentiality;
    private final String departmentName;
    private final String priority;

    public TicketMatchContext(String ticketTypeName, String ticketTypeCode, String categoryName,
                              String subCategoryName, String confidentiality, String departmentName,
                              String priority) {
        this.ticketTypeName = ticketTypeName;
        this.ticketTypeCode = ticketTypeCode;
        this.categoryName = categoryName;
        this.subCategoryName = subCategoryName;
        this.confidentiality = confidentiality;
        this.departmentName = departmentName;
        this.priority = priority;
    }

    public String value(String key) {
        if ("ticket_type".equals(key)) {
            return ticketTypeName;
        }
        if ("ticket_type_code".equals(key)) {
            return ticketTypeCode;
        }
        if ("category".equals(key)) {
            return categoryName;
        }
        if ("sub_category".equals(key)) {
            return subCategoryName;
        }
        if ("confidentiality".equals(key)) {
            return confidentiality;
        }
        if ("department".equals(key)) {
            return departmentName;
        }
        if ("priority".equals(key)) {
            return priority;
        }
        return null;
    }

    public String getTicketTypeName() {
        return ticketTypeName;
    }

    public String getTicketTypeCode() {
        return ticketTypeCode;
    }
}
