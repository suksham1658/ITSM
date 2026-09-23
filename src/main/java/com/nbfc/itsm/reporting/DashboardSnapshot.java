package com.nbfc.itsm.reporting;

import java.util.ArrayList;
import java.util.List;

public class DashboardSnapshot {

    private String title = "Executive Dashboard";
    private String scopeLabel = "No signed-in ITSM principal";
    private List<KpiCard> kpis = new ArrayList<KpiCard>();
    private ChartPayload statusChart = emptyChart("doughnut");
    private ChartPayload categoryChart = emptyChart("bar");
    private ChartPayload priorityChart = emptyChart("bar");
    private ChartPayload trendChart = emptyChart("line");

    private static ChartPayload emptyChart(String type) {
        ChartPayload p = new ChartPayload();
        p.setType(type);
        p.setEmpty(true);
        return p;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getScopeLabel() {
        return scopeLabel;
    }

    public void setScopeLabel(String scopeLabel) {
        this.scopeLabel = scopeLabel;
    }

    public List<KpiCard> getKpis() {
        return kpis;
    }

    public void setKpis(List<KpiCard> kpis) {
        this.kpis = kpis;
    }

    public ChartPayload getStatusChart() {
        return statusChart;
    }

    public void setStatusChart(ChartPayload statusChart) {
        this.statusChart = statusChart;
    }

    public ChartPayload getCategoryChart() {
        return categoryChart;
    }

    public void setCategoryChart(ChartPayload categoryChart) {
        this.categoryChart = categoryChart;
    }

    public ChartPayload getPriorityChart() {
        return priorityChart;
    }

    public void setPriorityChart(ChartPayload priorityChart) {
        this.priorityChart = priorityChart;
    }

    public ChartPayload getTrendChart() {
        return trendChart;
    }

    public void setTrendChart(ChartPayload trendChart) {
        this.trendChart = trendChart;
    }
}
