package com.nbfc.itsm.reporting;

import java.util.ArrayList;
import java.util.List;

public class ReportRow {

    private List<String> cells = new ArrayList<String>();

    public ReportRow() {
    }

    public ReportRow(String... values) {
        for (String v : values) {
            cells.add(v == null ? "" : v);
        }
    }

    public List<String> getCells() {
        return cells;
    }

    public void setCells(List<String> cells) {
        this.cells = cells;
    }
}
