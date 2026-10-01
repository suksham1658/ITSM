package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketAttachment;
import com.nbfc.itsm.domain.TicketType;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.ticket.AttachmentService;
import com.nbfc.itsm.ticket.TicketDetail;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.ticket.TicketService;
import com.nbfc.itsm.workflow.WorkflowEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Controller
public class TicketController {

    private final TicketService ticketService;
    private final TicketTypeRepository ticketTypeRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final AttachmentService attachmentService;
    private final EmployeeRepository employeeRepository;
    private final WorkflowEngine workflowEngine;

    public TicketController(TicketService ticketService,
                            TicketTypeRepository ticketTypeRepository,
                            CategoryRepository categoryRepository,
                            SubCategoryRepository subCategoryRepository,
                            AttachmentService attachmentService,
                            EmployeeRepository employeeRepository,
                            WorkflowEngine workflowEngine) {
        this.ticketService = ticketService;
        this.ticketTypeRepository = ticketTypeRepository;
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.attachmentService = attachmentService;
        this.employeeRepository = employeeRepository;
        this.workflowEngine = workflowEngine;
    }

    @GetMapping("/tickets")
    public String myTickets(@AuthenticationPrincipal ItsmUserPrincipal user,
                            @RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "priority", required = false) String priority,
                            @RequestParam(value = "typeId", required = false) Long typeId,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            @RequestParam(value = "size", defaultValue = "20") int size,
                            Model model) {
        return list(user, "mine", "myTickets", "My Tickets", q, status, priority, typeId, page, size, model);
    }

    @GetMapping("/tickets/team")
    @PreAuthorize("hasAuthority('TICKET_VIEW_TEAM')")
    public String team(@AuthenticationPrincipal ItsmUserPrincipal user,
                       @RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "priority", required = false) String priority,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       Model model) {
        return list(user, "team", "teamRequests", "Team Requests", q, status, priority, null, page, 20, model);
    }

    @GetMapping("/tickets/department")
    @PreAuthorize("hasAuthority('TICKET_VIEW_DEPARTMENT')")
    public String dept(@AuthenticationPrincipal ItsmUserPrincipal user,
                       @RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "priority", required = false) String priority,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       Model model) {
        return list(user, "department", "deptRequests", "Department Requests", q, status, priority, null, page, 20, model);
    }

    @GetMapping("/tickets/security")
    @PreAuthorize("hasAuthority('TICKET_VIEW_SECURITY')")
    public String security(@AuthenticationPrincipal ItsmUserPrincipal user,
                           @RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        return list(user, "security", "securityRequests", "Security Requests", q, status, null, null, page, 20, model);
    }

    @GetMapping("/tickets/changes")
    @PreAuthorize("hasAuthority('TICKET_FULFIL')")
    public String changes(@AuthenticationPrincipal ItsmUserPrincipal user,
                          @RequestParam(value = "q", required = false) String q,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          Model model) {
        return list(user, "changes", "changeRequests", "Change Requests", q, null, null, null, page, 20, model);
    }

    @GetMapping("/tickets/raise")
    @PreAuthorize("hasAuthority('TICKET_CREATE')")
    public String raise(Model model, HttpServletRequest request) {
        model.addAttribute("nav", "raise");
        model.addAttribute("pageTitle", "Raise Request");
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TicketForm());
        }
        populateLookups(model);
        BackLinks.addTo(model, request, "/tickets", "Back to My Tickets", "itsm.back.raise");
        return "tickets/raise";
    }

    @PostMapping("/tickets/raise")
    @PreAuthorize("hasAuthority('TICKET_CREATE')")
    public String raiseSubmit(@AuthenticationPrincipal ItsmUserPrincipal user,
                              @ModelAttribute("form") TicketForm form,
                              @RequestParam(value = "attachment", required = false) MultipartFile attachment,
                              RedirectAttributes ra) {
        try {
            Ticket ticket = ticketService.save(user, form, attachment);
            if ("Draft".equals(ticket.getStatusCode())) {
                ra.addFlashAttribute("message", "Draft saved as " + ticket.getPublicNumber() + ".");
            } else {
                ra.addFlashAttribute("message", "Request " + ticket.getPublicNumber() + " submitted.");
            }
            return "redirect:/tickets/" + ticket.getTicketId();
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            // Keep what the user typed so they only fix the flagged fields.
            ra.addFlashAttribute("form", form);
            return "redirect:/tickets/raise";
        }
    }

    @GetMapping("/tickets/{id}")
    @Transactional(readOnly = true)
    public String detail(@AuthenticationPrincipal ItsmUserPrincipal user, @PathVariable("id") Long id, Model model,
                         HttpServletRequest request) {
        TicketDetail detail = ticketService.detail(user, id);
        BackLinks.addTo(model, request, "/tickets", "Back to My Tickets", "itsm.back.ticket");
        model.addAttribute("nav", "myTickets");
        model.addAttribute("pageTitle", detail.getTicket().getPublicNumber());
        model.addAttribute("detail", detail);
        // Service desk step: the implementors assigned to the ticket's category (else the implementor group);
        // implementation step: colleagues in that step's group.
        boolean hasCurrent = detail.getCurrent() != null && detail.getStages() != null;
        AssignmentGroup pool = hasCurrent ? workflowEngine.assigneePool(detail.getStages(), detail.getCurrent()) : null;
        List<Employee> assignees = hasCurrent
                ? workflowEngine.eligibleImplementors(detail.getTicket(), detail.getStages(), detail.getCurrent())
                : new ArrayList<Employee>();
        boolean deskStep = hasCurrent && "ASSIGNMENT".equals(detail.getCurrent().getStageType());
        boolean categoryList = deskStep && detail.getTicket().getCategory() != null
                && !detail.getTicket().getCategory().getImplementorIds().isEmpty();
        model.addAttribute("assignees", assignees);
        model.addAttribute("deskStep", deskStep);
        model.addAttribute("assigneePoolName", categoryList
                ? "implementors for " + detail.getTicket().getCategory().getName()
                : (pool == null ? null : pool.getName()));
        return "tickets/detail";
    }

    /** System Administrator: reject any open ticket at any step (remarks required, audited). */
    @PostMapping("/tickets/{id}/admin-reject")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String adminReject(@AuthenticationPrincipal ItsmUserPrincipal user, @PathVariable("id") Long id,
                              @RequestParam(value = "remarks", required = false) String remarks, RedirectAttributes ra) {
        try {
            ticketService.adminReject(user, id, remarks);
            ra.addFlashAttribute("message", "Ticket rejected by System Administrator. The requester has been notified.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    /**
     * Re-open link from the "closed automatically" e-mail: opens the ticket at the Resolved / Not resolved
     * choice while the re-open period runs; afterwards it says the link has expired.
     */
    @GetMapping("/tickets/{id}/reopen")
    public String reopenLink(@AuthenticationPrincipal ItsmUserPrincipal user, @PathVariable("id") Long id,
                             RedirectAttributes ra) {
        TicketDetail d = ticketService.detail(user, id);
        if (!d.isCanReopen()) {
            ra.addFlashAttribute("errorMessage", d.getReopenUntil() != null
                    ? "This re-open link has expired. Please raise a new request if the issue is still there."
                    : "This ticket cannot be re-opened (it was not closed automatically, or it was already confirmed).");
            return "redirect:/tickets/" + id;
        }
        return "redirect:/tickets/" + id + "#reopen";
    }

    @PostMapping("/tickets/{id}/reopen")
    public String reopen(@AuthenticationPrincipal ItsmUserPrincipal user, @PathVariable("id") Long id,
                         @RequestParam(value = "remarks", required = false) String remarks, RedirectAttributes ra) {
        try {
            ticketService.reopen(user, id, remarks);
            ra.addFlashAttribute("message", "Ticket re-opened and sent back to the implementor.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/confirm-closed")
    public String confirmClosed(@AuthenticationPrincipal ItsmUserPrincipal user, @PathVariable("id") Long id,
                                RedirectAttributes ra) {
        try {
            ticketService.confirmClosed(user, id);
            ra.addFlashAttribute("message", "Thank you. The ticket stays closed.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/action")
    public String action(@AuthenticationPrincipal ItsmUserPrincipal user,
                         @PathVariable("id") Long id,
                         @RequestParam("actionCode") String actionCode,
                         @RequestParam(value = "remarks", required = false) String remarks,
                         @RequestParam(value = "assigneeId", required = false) Long assigneeId,
                         @RequestParam(value = "assigneeIds", required = false) List<Long> assigneeIds,
                         RedirectAttributes ra) {
        try {
            List<Long> chosen = new ArrayList<Long>();
            if (assigneeIds != null) {
                chosen.addAll(assigneeIds);
            }
            if (assigneeId != null) {
                chosen.add(assigneeId);
            }
            ticketService.applyActionFor(user, id, actionCode, remarks, chosen);
            ra.addFlashAttribute("message", new UiText().actionLabel(actionCode) + " recorded.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/comments")
    public String comment(@AuthenticationPrincipal ItsmUserPrincipal user,
                          @PathVariable("id") Long id,
                          @RequestParam("body") String body,
                          @RequestParam(value = "internal", defaultValue = "false") boolean internal,
                          RedirectAttributes ra) {
        try {
            ticketService.addComment(user, id, body, internal);
            ra.addFlashAttribute("message", "Comment added.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/attachments")
    public String attach(@AuthenticationPrincipal ItsmUserPrincipal user,
                         @PathVariable("id") Long id,
                         @RequestParam("file") MultipartFile file,
                         RedirectAttributes ra) {
        try {
            Ticket ticket = ticketService.requireView(user, id);
            Employee uploader = employeeRepository.findById(user.getEmployeeId())
                    .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
            attachmentService.store(ticket, uploader, file);
            ra.addFlashAttribute("message", "Attachment stored.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    @GetMapping("/tickets/{id}/attachments/{aid}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal ItsmUserPrincipal user,
                                           @PathVariable("id") Long id,
                                           @PathVariable("aid") Long aid) throws Exception {
        TicketAttachment att = ticketService.requireAttachment(user, id, aid);
        byte[] data = Files.readAllBytes(attachmentService.resolveFile(att));
        String ct = att.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : att.getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(att.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(ct))
                .body(data);
    }

    private String list(ItsmUserPrincipal user, String scope, String nav, String title, String q, String status,
                        String priority, Long typeId, int page, int size, Model model) {
        int safeSize = size < 1 ? 20 : Math.min(size, 100);
        int safePage = Math.max(page, 0);
        q = SearchText.clean(q);
        Page<Ticket> result = ticketService.search(user, scope, q, status, priority, typeId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAtUtc", "ticketId")));
        model.addAttribute("nav", nav);
        model.addAttribute("pageTitle", title);
        model.addAttribute("tickets", result);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("priority", priority);
        model.addAttribute("typeId", typeId);
        model.addAttribute("ticketTypes", ticketTypeRepository.findByActiveTrueOrderBySortOrderAsc());
        model.addAttribute("emptyMessage", "No tickets match these filters.");
        model.addAttribute("listPath",
                "mine".equals(scope) ? "/tickets"
                        : "team".equals(scope) ? "/tickets/team"
                        : "department".equals(scope) ? "/tickets/department"
                        : "security".equals(scope) ? "/tickets/security"
                        : "/tickets/changes");
        return "tickets/list";
    }

    private void populateLookups(Model model) {
        List<TicketType> types = ticketTypeRepository.findByActiveTrueOrderBySortOrderAsc();
        List<Category> categories = categoryRepository.findByActiveTrueOrderBySortOrderAsc();
        List<SubCategory> subs = subCategoryRepository.findByActiveTrueOrderBySortOrderAsc();
        for (SubCategory s : subs) {
            s.getCategory().getCategoryId();
        }
        model.addAttribute("ticketTypes", types);
        model.addAttribute("categories", categories);
        model.addAttribute("subCategories", subs);
    }
}
