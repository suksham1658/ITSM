package com.nbfc.itsm.reporting;

import java.util.ArrayList;
import java.util.List;

public class ChartSeries {

    private String label;
    private String color;
    private List<Number> data = new ArrayList<Number>();

    public ChartSeries() {
    }

    public ChartSeries(String label, String color, List<Number> data) {
        this.label = label;
        this.color = color;
        this.data = data;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<Number> getData() {
        return data;
    }

    public void setData(List<Number> data) {
        this.data = data;
    }
}
