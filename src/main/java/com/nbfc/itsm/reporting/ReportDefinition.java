package com.nbfc.itsm.reporting;

public class ReportDefinition {

    private final String code;
    private final String title;
    private final String icon;
    private final String description;

    public ReportDefinition(String code, String title, String icon, String description) {
        this.code = code;
        this.title = title;
        this.icon = icon;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}
