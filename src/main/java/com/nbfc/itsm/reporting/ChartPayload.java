package com.nbfc.itsm.reporting;

import java.util.ArrayList;
import java.util.List;

public class ChartPayload {

    private String type;
    private List<String> labels = new ArrayList<String>();
    private List<ChartSeries> series = new ArrayList<ChartSeries>();
    private boolean empty = true;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public List<ChartSeries> getSeries() {
        return series;
    }

    public void setSeries(List<ChartSeries> series) {
        this.series = series;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}
