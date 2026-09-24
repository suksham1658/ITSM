package com.nbfc.itsm.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.DepartmentRepository;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.reporting.NamedReport;
import com.nbfc.itsm.reporting.ReportFilter;
import com.nbfc.itsm.reporting.ReportRow;
import com.nbfc.itsm.reporting.ReportingService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.validation.FieldLimits;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Controller
@PreAuthorize("hasAuthority('REPORT_VIEW')")
public class ReportController {

    private final ReportingService reportingService;
    private final TicketTypeRepository ticketTypeRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final ObjectMapper objectMapper;

    public ReportController(ReportingService reportingService,
                            TicketTypeRepository ticketTypeRepository,
                            CategoryRepository categoryRepository,
                            DepartmentRepository departmentRepository,
                            ObjectMapper objectMapper) {
        this.reportingService = reportingService;
        this.ticketTypeRepository = ticketTypeRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/reports")
    public String index(Model model) {
        model.addAttribute("nav", "reports");
        model.addAttribute("pageTitle", "Reports");
        model.addAttribute("reports", reportingService.definitions());
        return "reports";
    }

    @GetMapping("/reports/{code}")
    public String view(@AuthenticationPrincipal ItsmUserPrincipal user,
                       @PathVariable String code,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) Long typeId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Long departmentId,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) String priority,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        NamedReport report = reportingService.run(user, code, filter(from, to, typeId, status, departmentId, categoryId, priority),
                Math.max(page, 0), ReportingService.PAGE_SIZE);
        String warning = dateRangeWarning(from, to);
        if (warning != null) {
            model.addAttribute("errorMessage", warning);
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        bindFilters(model, from, to, typeId, status, departmentId, categoryId, priority);
        model.addAttribute("nav", "reports");
        model.addAttribute("pageTitle", report.getTitle());
        model.addAttribute("report", report);
        model.addAttribute("chartJson", toJson(report.getChart()));
        return "reports/detail";
    }

    @GetMapping("/reports/{code}.csv")
    public void csv(@AuthenticationPrincipal ItsmUserPrincipal user,
                    @PathVariable String code,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                    @RequestParam(required = false) Long typeId,
                    @RequestParam(required = false) String status,
                    @RequestParam(required = false) Long departmentId,
                    @RequestParam(required = false) Long categoryId,
                    @RequestParam(required = false) String priority,
                    HttpServletResponse response) throws IOException {
        NamedReport report = reportingService.run(user, code,
                filter(from, to, typeId, status, departmentId, categoryId, priority),
                0, ReportingService.EXPORT_CAP);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + code + ".csv\"");
        PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        w.write('\uFEFF');
        w.println(csvLine(report.getColumns()));
        for (ReportRow row : report.getRows()) {
            w.println(csvLine(row.getCells()));
        }
        w.flush();
    }

    @GetMapping("/reports/{code}.xls")
    public void xls(@AuthenticationPrincipal ItsmUserPrincipal user,
                    @PathVariable String code,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                    @RequestParam(required = false) Long typeId,
                    @RequestParam(required = false) String status,
                    @RequestParam(required = false) Long departmentId,
                    @RequestParam(required = false) Long categoryId,
                    @RequestParam(required = false) String priority,
                    HttpServletResponse response) throws IOException {
        NamedReport report = reportingService.run(user, code,
                filter(from, to, typeId, status, departmentId, categoryId, priority),
                0, ReportingService.EXPORT_CAP);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/vnd.ms-excel; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + code + ".xls\"");
        PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        w.println("<?xml version=\"1.0\"?>");
        w.println("<?mso-application progid=\"Excel.Sheet\"?>");
        w.println("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"");
        w.println(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">");
        w.println("<Worksheet ss:Name=\"Report\"><Table>");
        w.println("<Row>");
        for (String col : report.getColumns()) {
            w.print("<Cell><Data ss:Type=\"String\">");
            w.print(xml(col));
            w.print("</Data></Cell>");
        }
        w.println("</Row>");
        for (ReportRow row : report.getRows()) {
            w.println("<Row>");
            for (String cell : row.getCells()) {
                w.print("<Cell><Data ss:Type=\"String\">");
                w.print(xml(cell));
                w.print("</Data></Cell>");
            }
            w.println("</Row>");
        }
        w.println("</Table></Worksheet></Workbook>");
        w.flush();
    }

    private void bindFilters(Model model, LocalDate from, LocalDate to, Long typeId, String status,
                             Long departmentId, Long categoryId, String priority) {
        LocalDate defFrom = from != null ? from : LocalDate.now(ReportingService.IST).minusDays(90);
        LocalDate defTo = to != null ? to : LocalDate.now(ReportingService.IST);
        model.addAttribute("from", defFrom);
        model.addAttribute("to", defTo);
        model.addAttribute("typeId", typeId);
        model.addAttribute("status", status);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("priority", priority);
        model.addAttribute("types", ticketTypeRepository.findByActiveTrueOrderBySortOrderAsc());
        model.addAttribute("categories", categoryRepository.findByActiveTrueOrderBySortOrderAsc());
        model.addAttribute("departments", departmentRepository.findAll());
    }

    private ReportFilter filter(LocalDate from, LocalDate to, Long typeId, String status,
                                Long departmentId, Long categoryId, String priority) {
        ReportFilter f = new ReportFilter();
        LocalDate start = from != null ? from : LocalDate.now(ReportingService.IST).minusDays(90);
        LocalDate end = to != null ? to : LocalDate.now(ReportingService.IST);
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        f.setFrom(start);
        f.setTo(end);
        f.setTypeId(typeId);
        f.setStatus(status == null || status.trim().isEmpty() ? null : status.trim());
        f.setDepartmentId(departmentId);
        f.setCategoryId(categoryId);
        f.setPriority(priority != null && FieldLimits.PRIORITIES.contains(priority) ? priority : null);
        return f;
    }

    /** Message for the page when the date range had to be corrected, else null. */
    private static String dateRangeWarning(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            return "'From' date was after 'To' date, so the two dates were swapped.";
        }
        return null;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new ItsmException("JSON", "Could not serialise chart data.");
        }
    }

    private static String csvLine(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = cells.get(i) == null ? "" : cells.get(i);
            if (v.indexOf('"') >= 0 || v.indexOf(',') >= 0 || v.indexOf('\n') >= 0) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    private static String xml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
