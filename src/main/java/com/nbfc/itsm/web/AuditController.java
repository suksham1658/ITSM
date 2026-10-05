package com.nbfc.itsm.web;

import com.nbfc.itsm.audit.AuditTrailService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Audit Trail: read-only, filterable, newest first (AUDIT_VIEW). */
@Controller
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
public class AuditController {

    private final AuditTrailService auditTrailService;

    public AuditController(AuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(value = "user", required = false) String user,
                        @RequestParam(value = "ticket", required = false) String ticket,
                        @RequestParam(value = "module", required = false) String module,
                        @RequestParam(value = "action", required = false) String action,
                        @RequestParam(value = "result", required = false) String result,
                        @RequestParam(value = "text", required = false) String text,
                        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        AuditTrailService.Filter f = new AuditTrailService.Filter();
        f.setUser(SearchText.clean(user));
        f.setTicket(SearchText.clean(ticket));
        f.setModule(module);
        f.setAction(action);
        f.setResult(result);
        f.setText(SearchText.clean(text));
        f.setFrom(from);
        f.setTo(to);
        model.addAttribute("nav", "auditTrail");
        model.addAttribute("pageTitle", "Audit Trail");
        model.addAttribute("f", f);
        model.addAttribute("result", auditTrailService.search(f, page));
        model.addAttribute("modules", auditTrailService.modules());
        model.addAttribute("actions", auditTrailService.actions());
        return "audit";
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter IST_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss").withZone(IST);
    private static final String[] COLUMNS = {
            "Time (IST)", "User", "Emp No", "Role", "Module", "Action", "Ticket",
            "Old value", "New value", "IP address", "Browser", "Result"};

    /** Download the audit trail that matches the current filters as an Excel (.xls) file. */
    @GetMapping("/audit/export.xls")
    public void exportXls(@RequestParam(value = "user", required = false) String user,
                          @RequestParam(value = "ticket", required = false) String ticket,
                          @RequestParam(value = "module", required = false) String module,
                          @RequestParam(value = "action", required = false) String action,
                          @RequestParam(value = "result", required = false) String result,
                          @RequestParam(value = "text", required = false) String text,
                          @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          HttpServletResponse response) throws IOException {
        AuditTrailService.Filter f = new AuditTrailService.Filter();
        f.setUser(SearchText.clean(user));
        f.setTicket(SearchText.clean(ticket));
        f.setModule(module);
        f.setAction(action);
        f.setResult(result);
        f.setText(SearchText.clean(text));
        f.setFrom(from);
        f.setTo(to);
        List<AuditTrailService.Row> rows = auditTrailService.export(f);

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/vnd.ms-excel; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"audit-trail.xls\"");
        PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        w.println("<?xml version=\"1.0\"?>");
        w.println("<?mso-application progid=\"Excel.Sheet\"?>");
        w.println("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"");
        w.println(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">");
        // Header style: bold, black text, light-grey fill with a thin border.
        w.println("<Styles>");
        w.println("<Style ss:ID=\"hdr\">"
                + "<Font ss:Bold=\"1\" ss:Color=\"#000000\"/>"
                + "<Interior ss:Color=\"#D9D9D9\" ss:Pattern=\"Solid\"/>"
                + "<Alignment ss:Vertical=\"Center\"/>"
                + "<Borders><Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#000000\"/></Borders>"
                + "</Style>");
        w.println("</Styles>");
        w.println("<Worksheet ss:Name=\"Audit Trail\"><Table>");
        w.print("<Row>");
        for (String col : COLUMNS) {
            w.print("<Cell ss:StyleID=\"hdr\"><Data ss:Type=\"String\">");
            w.print(xml(col));
            w.print("</Data></Cell>");
        }
        w.println("</Row>");
        for (AuditTrailService.Row r : rows) {
            w.print("<Row>");
            cell(w, r.getLog().getOccurredAtUtc() == null ? "" : IST_FMT.format(r.getLog().getOccurredAtUtc()));
            cell(w, r.getUserName());
            cell(w, r.getLog().getEmployeeNo());
            cell(w, r.getLog().getRoleCode());
            cell(w, r.getLog().getModuleCode());
            cell(w, r.getLog().getActionCode());
            cell(w, r.getTicketNumber());
            cell(w, r.getLog().getOldValue());
            cell(w, r.getLog().getNewValue());
            cell(w, r.getLog().getIpAddress());
            cell(w, r.getLog().getUserAgent());
            cell(w, r.getLog().getResultCode());
            w.println("</Row>");
        }
        w.println("</Table></Worksheet></Workbook>");
        w.flush();
    }

    private static void cell(PrintWriter w, String value) {
        w.print("<Cell><Data ss:Type=\"String\">");
        w.print(xml(value));
        w.print("</Data></Cell>");
    }

    private static String xml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
