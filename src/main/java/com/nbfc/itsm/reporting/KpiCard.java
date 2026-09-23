package com.nbfc.itsm.reporting;

public class KpiCard {

    private String label;
    private String value;
    private String trend;
    private String trendClass;
    private String context;
    private String icon;
    private String color;

    public KpiCard() {
    }

    public KpiCard(String label, String value, String trend, String trendClass, String context,
                   String icon, String color) {
        this.label = label;
        this.value = value;
        this.trend = trend;
        this.trendClass = trendClass;
        this.context = context;
        this.icon = icon;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getTrend() {
        return trend;
    }

    public void setTrend(String trend) {
        this.trend = trend;
    }

    public String getTrendClass() {
        return trendClass;
    }

    public void setTrendClass(String trendClass) {
        this.trendClass = trendClass;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
