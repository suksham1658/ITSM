package com.nbfc.itsm.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.domain.TicketSlaRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.reporting.DashboardSnapshot;
import com.nbfc.itsm.reporting.ReportingService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.sla.SlaService;
import com.nbfc.itsm.ticket.TicketService;
import com.nbfc.itsm.util.TimeUtc;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;
import java.util.List;

@Controller
public class PortalPageController {

    private final TicketService ticketService;
    private final TicketSlaRepository ticketSlaRepository;
    private final SlaService slaService;
    private final ReportingService reportingService;
    private final ObjectMapper objectMapper;

    public PortalPageController(TicketService ticketService,
                                TicketSlaRepository ticketSlaRepository,
                                SlaService slaService,
                                ReportingService reportingService,
                                ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.ticketSlaRepository = ticketSlaRepository;
        this.slaService = slaService;
        this.reportingService = reportingService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal ItsmUserPrincipal user, Model model) {
        model.addAttribute("nav", "dashboard");
        DashboardSnapshot snap = reportingService.dashboard(user);
        model.addAttribute("pageTitle", snap.getTitle());
        model.addAttribute("dash", snap);
        model.addAttribute("statusChartJson", json(snap.getStatusChart()));
        model.addAttribute("categoryChartJson", json(snap.getCategoryChart()));
        model.addAttribute("priorityChartJson", json(snap.getPriorityChart()));
        model.addAttribute("trendChartJson", json(snap.getTrendChart()));
        if (user != null) {
            model.addAttribute("recentTickets", ticketService.recentFor(user));
            model.addAttribute("pendingApprovals", ticketService.approvalsFor(user));
        }
        return "dashboard";
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('TICKET_APPROVE_ASSIGNED_STAGE')")
    public String approvals(@AuthenticationPrincipal ItsmUserPrincipal user, Model model) {
        model.addAttribute("nav", "approvals");
        model.addAttribute("pageTitle", "Approvals");
        model.addAttribute("items", ticketService.waitingFor(user));
        model.addAttribute("emptyMessage", "Nothing is waiting for you: no approvals, service desk assignments or implementor work.");
        return "approvals";
    }

    @GetMapping("/queue/desk")
    @PreAuthorize("hasAuthority('TICKET_VIEW_QUEUE_ALL')")
    public String desk(Model model) {
        return queue(model, "ticketQueue", "Ticket Queue", "ASSIGNMENT",
                "No tickets waiting in the service desk queue.");
    }

    @GetMapping("/queue/assignment")
    @PreAuthorize("hasAuthority('TICKET_VIEW_QUEUE_ALL')")
    public String assignment(Model model) {
        return queue(model, "assignment", "Assignment", "ASSIGNMENT",
                "Nothing to assign.");
    }

    @GetMapping("/queue/mine")
    @PreAuthorize("hasAuthority('TICKET_FULFIL')")
    public String myQueue(@AuthenticationPrincipal ItsmUserPrincipal user, Model model) {
        model.addAttribute("nav", "myQueue");
        model.addAttribute("pageTitle", "My Assigned Tickets");
        model.addAttribute("tickets", ticketService.assignedTo(user));
        model.addAttribute("emptyMessage", "No tickets are assigned to you.");
        return "queue";
    }

    @GetMapping("/queue/work")
    @PreAuthorize("hasAuthority('TICKET_FULFIL')")
    public String workQueue(Model model) {
        return queue(model, "workQueue", "Work Queue", "FULFILMENT",
                "Implementor work queue is empty.");
    }

    @GetMapping("/queue/implementation")
    @PreAuthorize("hasAuthority('TICKET_FULFIL')")
    public String implementation(Model model) {
        return queue(model, "implementation", "Implementation", "FULFILMENT",
                "Implementation queue is empty.");
    }

    @GetMapping("/sla")
    @PreAuthorize("hasAuthority('SLA_MONITOR')")
    @Transactional(readOnly = true)
    public String sla(Model model) {
        List<TicketSla> clocks = ticketSlaRepository.findAll();
        for (TicketSla sla : clocks) {
            slaService.refreshState(sla, TimeUtc.now());
            if (sla.getTicket() != null) {
                sla.getTicket().getPublicNumber();
            }
            if (sla.getSlaPolicy() != null) {
                sla.getSlaPolicy().getPriorityCode();
            }
        }
        model.addAttribute("nav", "slaMonitoring");
        model.addAttribute("pageTitle", "SLA Monitoring");
        model.addAttribute("clocks", clocks);
        model.addAttribute("emptyMessage", "No SLA clocks are running.");
        return "sla";
    }

    @GetMapping("/escalations")
    @PreAuthorize("hasAuthority('SLA_MONITOR')")
    @Transactional(readOnly = true)
    public String escalations(Model model) {
        // Open tickets at risk or overdue (states are kept current by the SLA monitor every 5 minutes).
        List<TicketSla> clocks = new java.util.ArrayList<TicketSla>();
        for (TicketSla sla : ticketSlaRepository.findByStateCodeIn(Arrays.asList("NEAR", "BREACHED"))) {
            if (sla.getResolvedUtc() == null && sla.getTicket() != null
                    && !Arrays.asList("Closed", "Rejected", "Draft").contains(sla.getTicket().getStatusCode())) {
                sla.getTicket().getPublicNumber();
                clocks.add(sla);
            }
        }
        model.addAttribute("nav", "escalations");
        model.addAttribute("pageTitle", "Escalations");
        model.addAttribute("clocks", clocks);
        model.addAttribute("emptyMessage", "No NEAR or BREACHED SLA clocks.");
        return "sla";
    }

    @GetMapping("/risk")
    public String risk(Model model) {
        return listPage(model, "risk", "Risk Dashboard", "risk",
                "To review security tickets, open Security Requests.");
    }

    @GetMapping("/kb")
    public String kb(Model model) {
        return listPage(model, "kb", "Knowledge Base", "kb",
                "No knowledge articles have been published yet.");
    }

    private String queue(Model model, String nav, String title, String stageType, String empty) {
        model.addAttribute("nav", nav);
        model.addAttribute("pageTitle", title);
        model.addAttribute("tickets", ticketService.queueByStageType(stageType));
        model.addAttribute("emptyMessage", empty);
        model.addAttribute("rowAction", "ASSIGNMENT".equals(stageType) ? "Assign" : "Open");
        return "queue";
    }

    private String listPage(Model model, String nav, String title, String view, String emptyMessage) {
        model.addAttribute("nav", nav);
        model.addAttribute("pageTitle", title);
        model.addAttribute("emptyMessage", emptyMessage);
        return view;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ItsmException("JSON", "Could not serialise chart data.");
        }
    }
}
