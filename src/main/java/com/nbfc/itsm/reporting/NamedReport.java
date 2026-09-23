package com.nbfc.itsm.reporting;

import java.util.ArrayList;
import java.util.List;

public class NamedReport {

    private String code;
    private String title;
    private String description;
    private String note;
    private List<String> columns = new ArrayList<String>();
    private List<ReportRow> rows = new ArrayList<ReportRow>();
    private long totalRows;
    private int page;
    private int size;
    private ChartPayload chart = new ChartPayload();

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<ReportRow> getRows() {
        return rows;
    }

    public void setRows(List<ReportRow> rows) {
        this.rows = rows;
    }

    public long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(long totalRows) {
        this.totalRows = totalRows;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public ChartPayload getChart() {
        return chart;
    }

    public void setChart(ChartPayload chart) {
        this.chart = chart;
    }

    public int getTotalPages() {
        if (size <= 0) {
            return 1;
        }
        return (int) Math.max(1L, (totalRows + size - 1) / size);
    }

    public boolean isHasPrevious() {
        return page > 0;
    }

    public boolean isHasNext() {
        return page + 1 < getTotalPages() && totalRows > 0;
    }
}
