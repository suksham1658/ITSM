package com.nbfc.itsm.ticket;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketAttachment;
import com.nbfc.itsm.domain.TicketAttachmentRepository;
import com.nbfc.itsm.domain.TicketComment;
import com.nbfc.itsm.domain.TicketCommentRepository;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.domain.TicketSlaRepository;
import com.nbfc.itsm.domain.TicketType;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.domain.WorkflowInstance;
import com.nbfc.itsm.domain.WorkflowInstanceRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import com.nbfc.itsm.domain.WorkflowStageTransition;
import com.nbfc.itsm.domain.WorkflowStageTransitionRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.notification.NotificationService;
import com.nbfc.itsm.validation.FieldLimits;
import com.nbfc.itsm.validation.Validation;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.sla.SlaService;
import com.nbfc.itsm.util.TimeUtc;
import com.nbfc.itsm.workflow.WorkflowEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private static final List<String> CLOSED = Arrays.asList("Closed", "Rejected");
    /** Newest ticket first; the id breaks ties between tickets created in the same instant. */
    static final Comparator<Ticket> NEWEST_FIRST = Comparator
            .comparing(Ticket::getCreatedAtUtc, Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
            .thenComparing(Ticket::getTicketId, Comparator.nullsLast(Comparator.<Long>reverseOrder()));

    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final EmployeeRepository employeeRepository;
    private final TicketNumberService ticketNumberService;
    private final WorkflowEngine workflowEngine;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowInstanceStageRepository instanceStageRepository;
    private final WorkflowStageTransitionRepository transitionRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final TicketSlaRepository slaRepository;
    private final SlaService slaService;
    private final AuditRecorder auditRecorder;
    private final NotificationService notificationService;

    public TicketService(TicketRepository ticketRepository,
                         TicketTypeRepository ticketTypeRepository,
                         CategoryRepository categoryRepository,
                         SubCategoryRepository subCategoryRepository,
                         EmployeeRepository employeeRepository,
                         TicketNumberService ticketNumberService,
                         WorkflowEngine workflowEngine,
                         WorkflowInstanceRepository instanceRepository,
                         WorkflowInstanceStageRepository instanceStageRepository,
                         WorkflowStageTransitionRepository transitionRepository,
                         TicketCommentRepository commentRepository,
                         TicketAttachmentRepository attachmentRepository,
                         TicketSlaRepository slaRepository,
                         SlaService slaService,
                         AuditRecorder auditRecorder,
                         NotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.employeeRepository = employeeRepository;
        this.ticketNumberService = ticketNumberService;
        this.workflowEngine = workflowEngine;
        this.instanceRepository = instanceRepository;
        this.instanceStageRepository = instanceStageRepository;
        this.transitionRepository = transitionRepository;
        this.commentRepository = commentRepository;
        this.attachmentRepository = attachmentRepository;
        this.slaRepository = slaRepository;
        this.slaService = slaService;
        this.auditRecorder = auditRecorder;
        this.notificationService = notificationService;
    }

    @Transactional
    public Ticket save(ItsmUserPrincipal principal, TicketForm form) {
        if (!has(principal, "TICKET_CREATE")) {
            throw new AccessDeniedException("Cannot create tickets.");
        }
        Employee requester = employeeRepository.findById(principal.getEmployeeId())
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
        Ticket ticket = applyForm(new Ticket(), form, requester);
        boolean draft = form.getIntent() != null && "draft".equalsIgnoreCase(form.getIntent());
        if (draft) {
            ticket.setStatusCode("Draft");
            ticket.setPublicNumber("DRAFT-" + requester.getEmployeeId() + "-" + UUID.randomUUID().toString().substring(0, 8));
            ticket = ticketRepository.save(ticket);
            auditRecorder.recordTicket("CREATE_DRAFT", ticket.getTicketId(), null, ticket.getPublicNumber());
            return ticket;
        }
        ticket.setStatusCode("Pending Approval");
        ticket.setPublicNumber(ticketNumberService.allocate());
        ticket = ticketRepository.save(ticket);
        auditRecorder.recordTicket("CREATE", ticket.getTicketId(), null, ticket.getPublicNumber());
        workflowEngine.startOnSubmit(ticket);
        return ticketRepository.findById(ticket.getTicketId()).orElse(ticket);
    }

    @Transactional
    public Ticket submitDraft(ItsmUserPrincipal principal, Long ticketId, TicketForm form) {
        Ticket ticket = requireView(principal, ticketId);
        if (!"Draft".equals(ticket.getStatusCode())) {
            throw new ItsmException("NOT_DRAFT", "Only drafts can be submitted this way.");
        }
        if (!ticket.getRequester().getEmployeeId().equals(principal.getEmployeeId())) {
            throw new AccessDeniedException("Only the requester can submit this draft.");
        }
        applyForm(ticket, form, ticket.getRequester());
        ticket.setPublicNumber(ticketNumberService.allocate());
        ticket.setStatusCode("Pending Approval");
        ticket = ticketRepository.save(ticket);
        auditRecorder.recordTicket("SUBMIT", ticket.getTicketId(), "Draft", ticket.getPublicNumber());
        workflowEngine.startOnSubmit(ticket);
        return ticketRepository.findById(ticket.getTicketId()).orElse(ticket);
    }

    @Transactional
    public Ticket applyAction(ItsmUserPrincipal principal, Long ticketId, String action, String remarks, Long assigneeId) {
        return applyActionFor(principal, ticketId, action, remarks,
                assigneeId == null ? java.util.Collections.<Long>emptyList() : java.util.Collections.singletonList(assigneeId));
    }

    /** System Administrator override: reject any open ticket, with remarks. */
    @Transactional
    public Ticket adminReject(ItsmUserPrincipal principal, Long ticketId, String remarks) {
        if (remarks != null && remarks.trim().length() > FieldLimits.REMARKS_MAX) {
            throw new ItsmException("REMARKS_TOO_LONG", "Remarks must be at most " + FieldLimits.REMARKS_MAX + " characters.");
        }
        return workflowEngine.adminReject(ticketId, principal, remarks);
    }

    /** {@code assigneeIds}: one or more implementors for ASSIGN at the service desk, one for REASSIGN. */
    @Transactional
    public Ticket applyActionFor(ItsmUserPrincipal principal, Long ticketId, String action, String remarks, List<Long> assigneeIds) {
        requireView(principal, ticketId);
        if (remarks != null && remarks.trim().length() > FieldLimits.REMARKS_MAX) {
            throw new ItsmException("REMARKS_TOO_LONG",
                    "Remarks must be at most " + FieldLimits.REMARKS_MAX + " characters.");
        }
        return workflowEngine.applyActionFor(ticketId, principal, action, remarks == null ? null : remarks.trim(), assigneeIds);
    }

    @Transactional
    public TicketComment addComment(ItsmUserPrincipal principal, Long ticketId, String body, boolean internal) {
        Ticket ticket = requireView(principal, ticketId);
        if (!StringUtils.hasText(body)) {
            throw new ItsmException("COMMENT_EMPTY", "Comment text is required.");
        }
        Validation v = new Validation();
        v.text(body, "Comment", FieldLimits.COMMENT_MIN, FieldLimits.COMMENT_MAX, true);
        v.throwIfInvalid("COMMENT_INVALID");
        boolean staff = canSeeInternal(principal);
        if (internal && !staff) {
            throw new AccessDeniedException("Internal comments are for service desk / implementors / approvers.");
        }
        Employee author = employeeRepository.findById(principal.getEmployeeId())
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
        TicketComment row = new TicketComment();
        row.setTicket(ticket);
        row.setAuthor(author);
        row.setBody(body.trim());
        row.setInternal(internal && staff);
        row.setCreatedAtUtc(TimeUtc.now());
        row = commentRepository.save(row);
        auditRecorder.recordTicket("COMMENT", ticket.getTicketId(), null, internal ? "internal" : "public");
        notificationService.commented(ticket, author, row.isInternal());
        return row;
    }

    @Transactional(readOnly = true)
    public TicketDetail detail(ItsmUserPrincipal principal, Long ticketId) {
        Ticket ticket = requireView(principal, ticketId);
        hydrate(ticket);
        TicketDetail detail = new TicketDetail();
        detail.setTicket(ticket);
        Employee actor = employeeRepository.findById(principal.getEmployeeId()).orElse(null);
        if (ticket.getWorkflowInstanceId() != null) {
            WorkflowInstance instance = instanceRepository.findById(ticket.getWorkflowInstanceId()).orElse(null);
            detail.setInstance(instance);
            if (instance != null) {
                List<WorkflowInstanceStage> stages = workflowEngine.loadStages(instance);
                for (WorkflowInstanceStage s : stages) {
                    if (s.getResolvedEmployee() != null) {
                        s.getResolvedEmployee().getDisplayName();
                    }
                    if (s.getResolvedRole() != null) {
                        s.getResolvedRole().getName();
                    }
                    if (s.getResolvedGroup() != null) {
                        s.getResolvedGroup().getName();
                    }
                    if (s.getWorkflowStage() != null) {
                        s.getWorkflowStage().getLabel();
                    }
                }
                detail.setStages(stages);
                WorkflowInstanceStage current = null;
                for (WorkflowInstanceStage s : stages) {
                    if ("Current".equals(s.getStatusCode())) {
                        current = s;
                    }
                }
                detail.setCurrent(current);
                if (current != null && actor != null) {
                    boolean can = false;
                    try {
                        workflowEngine.assertCanAct(principal, actor, ticket, current);
                        can = true;
                    } catch (AccessDeniedException ex) {
                        can = false;
                    }
                    detail.setCanAct(can);
                    if (can && current.getWorkflowStage() != null) {
                        List<WorkflowStageTransition> allowed =
                                transitionRepository.findByWorkflowStage(current.getWorkflowStage());
                        detail.setAllowed(allowed);
                    }
                }
            }
        }
        List<TicketComment> comments = commentRepository.findByTicketOrderByCreatedAtUtcAsc(ticket);
        boolean showInternal = canSeeInternal(principal);
        detail.setShowInternalComments(showInternal);
        detail.setCanCommentInternal(showInternal);
        List<TicketComment> visible = new ArrayList<TicketComment>();
        for (TicketComment c : comments) {
            c.getAuthor().getDisplayName();
            if (!c.isInternal() || showInternal) {
                visible.add(c);
            }
        }
        detail.setComments(visible);
        List<TicketAttachment> files = attachmentRepository.findByTicketOrderByUploadedAtUtcAsc(ticket);
        for (TicketAttachment a : files) {
            a.getUploadedBy().getDisplayName();
        }
        detail.setAttachments(files);
        TicketSla sla = slaRepository.findByTicket(ticket).orElse(null);
        if (sla != null) {
            slaService.refreshState(sla, TimeUtc.now());
            if (sla.getSlaPolicy() != null) {
                sla.getSlaPolicy().getPriorityCode();
            }
        }
        detail.setSla(sla);
        return detail;
    }

    @Transactional(readOnly = true)
    public Page<Ticket> search(ItsmUserPrincipal principal, String scope, String q, String status,
                               String priority, Long typeId, Pageable pageable) {
        Employee me = employeeRepository.findById(principal.getEmployeeId()).orElse(null);
        if ("team".equals(scope) && !has(principal, "TICKET_VIEW_TEAM")) {
            throw new AccessDeniedException("No team view.");
        }
        if ("department".equals(scope) && !has(principal, "TICKET_VIEW_DEPARTMENT")) {
            throw new AccessDeniedException("No department view.");
        }
        if ("security".equals(scope) && !has(principal, "TICKET_VIEW_SECURITY")) {
            throw new AccessDeniedException("No security view.");
        }
        if ("changes".equals(scope) && !has(principal, "TICKET_FULFIL") && !has(principal, "TICKET_VIEW_QUEUE_ALL")) {
            throw new AccessDeniedException("No change view.");
        }
        Specification<Ticket> spec = (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> and = new ArrayList<Predicate>();
            Join<Object, Object> requester = root.join("requester", JoinType.LEFT);
            Join<Object, Object> type = root.join("ticketType", JoinType.LEFT);
            Join<Object, Object> category = root.join("category", JoinType.LEFT);
            if ("mine".equals(scope)) {
                and.add(cb.equal(requester.get("employeeId"), principal.getEmployeeId()));
            } else if ("team".equals(scope)) {
                if (me == null) {
                    and.add(cb.disjunction());
                } else {
                    and.add(cb.or(
                            cb.equal(requester.get("employeeId"), principal.getEmployeeId()),
                            cb.equal(requester.get("manager"), me)));
                }
            } else if ("department".equals(scope)) {
                if (me != null && me.getDepartment() != null) {
                    and.add(cb.equal(root.get("department"), me.getDepartment()));
                } else {
                    and.add(cb.disjunction());
                }
            } else if ("security".equals(scope)) {
                and.add(cb.or(
                        cb.equal(root.get("confidentialityCode"), "Highly Confidential"),
                        cb.equal(type.get("name"), "Security Incident"),
                        cb.equal(category.get("name"), "Cyber Security")));
            } else if ("changes".equals(scope)) {
                and.add(cb.equal(type.get("name"), "Change Request"));
            }
            if (StringUtils.hasText(status)) {
                and.add(cb.equal(root.get("statusCode"), status));
            }
            if (StringUtils.hasText(priority)) {
                and.add(cb.equal(root.get("priorityCode"), priority));
            }
            if (typeId != null) {
                and.add(cb.equal(type.get("ticketTypeId"), typeId));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                and.add(cb.or(
                        cb.like(cb.lower(root.get("publicNumber")), like),
                        cb.like(cb.lower(root.get("subject")), like),
                        cb.like(cb.lower(root.get("statusCode")), like)));
            }
            return cb.and(and.toArray(new Predicate[0]));
        };
        Page<Ticket> page = ticketRepository.findAll(spec, pageable);
        for (Ticket t : page.getContent()) {
            hydrate(t);
        }
        return page;
    }

    @Transactional(readOnly = true)
    public List<Ticket> queueByStageType(String stageType) {
        List<WorkflowInstanceStage> current = instanceStageRepository.findByStatusCodeAndStageType("Current", stageType);
        java.util.Set<Long> ids = new java.util.LinkedHashSet<Long>();
        for (WorkflowInstanceStage s : current) {
            ids.add(s.getWorkflowInstance().getTicketId());
        }
        if ("ASSIGNMENT".equals(stageType)) {
            // Service desk queue: also any other step waiting for the IT Service Desk (e.g. a workflow whose
            // desk stage is an approval by the IT Service Desk role).
            for (WorkflowInstanceStage s : instanceStageRepository.findByStatusCode("Current")) {
                boolean deskRole = s.getResolvedRole() != null && DESK.equals(s.getResolvedRole().getCode());
                boolean deskGroup = s.getResolvedGroup() != null && DESK.equals(s.getResolvedGroup().getCode());
                if ((deskRole || deskGroup) && s.getResolvedEmployee() == null) {
                    ids.add(s.getWorkflowInstance().getTicketId());
                }
            }
        }
        List<Ticket> tickets = new ArrayList<Ticket>();
        for (Long ticketId : ids) {
            ticketRepository.findById(ticketId).ifPresent(t -> {
                hydrate(t);
                tickets.add(t);
            });
        }
        tickets.sort(NEWEST_FIRST);
        return tickets;
    }

    /** Role and group code of the IT Service Desk. */
    private static final String DESK = "IT_SERVICE_DESK";

    @Transactional(readOnly = true)
    public List<Ticket> assignedTo(ItsmUserPrincipal principal) {
        Employee me = employeeRepository.findById(principal.getEmployeeId()).orElse(null);
        if (me == null) {
            return new ArrayList<Ticket>();
        }
        Specification<Ticket> spec = (root, query, cb) -> cb.equal(root.get("assignedImplementor"), me);
        List<Ticket> list = new ArrayList<Ticket>(
                ticketRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAtUtc", "ticketId")));
        // Also tickets the service desk sent to several implementors including me, not yet picked up.
        java.util.Set<Long> seen = new java.util.HashSet<Long>();
        for (Ticket t : list) {
            seen.add(t.getTicketId());
        }
        for (WorkflowInstanceStage s : instanceStageRepository.findByStatusCodeAndStageType("Current", "FULFILMENT")) {
            if (s.getResolvedEmployee() == null && s.getAssigneeIds().contains(me.getEmployeeId())) {
                Long ticketId = s.getWorkflowInstance().getTicketId();
                if (seen.add(ticketId)) {
                    ticketRepository.findById(ticketId).ifPresent(list::add);
                }
            }
        }
        list.sort(NEWEST_FIRST);
        for (Ticket t : list) {
            hydrate(t);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<Ticket> approvalsFor(ItsmUserPrincipal principal) {
        Employee me = employeeRepository.findById(principal.getEmployeeId()).orElse(null);
        List<WorkflowInstanceStage> current = instanceStageRepository.findByStatusCodeAndStageType("Current", "APPROVAL");
        List<Ticket> out = new ArrayList<Ticket>();
        for (WorkflowInstanceStage s : current) {
            if (me == null) {
                continue;
            }
            try {
                Ticket t = ticketRepository.findById(s.getWorkflowInstance().getTicketId()).orElse(null);
                if (t == null) {
                    continue;
                }
                workflowEngine.assertCanAct(principal, me, t, s);
                hydrate(t);
                out.add(t);
            } catch (AccessDeniedException ex) {
                /* skip */
            }
        }
        out.sort(NEWEST_FIRST);
        return out;
    }

    /**
     * Approvals page: every open ticket whose current step this user can act on now — approve,
     * assign as IT Service Desk, work on as implementor, or confirm as requester. Newest first.
     */
    @Transactional(readOnly = true)
    public List<WaitingItem> waitingFor(ItsmUserPrincipal principal) {
        Employee me = employeeRepository.findById(principal.getEmployeeId()).orElse(null);
        List<WaitingItem> out = new ArrayList<WaitingItem>();
        if (me == null) {
            return out;
        }
        for (WorkflowInstanceStage s : instanceStageRepository.findByStatusCode("Current")) {
            if (!WaitingItem.ACTION_BY_STAGE_TYPE.containsKey(s.getStageType())) {
                continue;
            }
            // A step given to one person waits only for them (or their delegate), not their whole group.
            if (s.getResolvedEmployee() != null && !s.getResolvedEmployee().getEmployeeId().equals(me.getEmployeeId())
                    && !WorkflowEngine.isDelegateFor(me, s)) {
                continue;
            }
            try {
                Ticket t = ticketRepository.findById(s.getWorkflowInstance().getTicketId()).orElse(null);
                if (t == null || CLOSED.contains(t.getStatusCode())) {
                    continue;
                }
                workflowEngine.assertCanAct(principal, me, t, s);
                hydrate(t);
                out.add(new WaitingItem(t, s.getStageType(), s.getLabel()));
            } catch (AccessDeniedException ex) {
                /* not this user's step */
            }
        }
        out.sort((a, b) -> NEWEST_FIRST.compare(a.getTicket(), b.getTicket()));
        return out;
    }

    @Transactional(readOnly = true)
    public Ticket requireView(ItsmUserPrincipal principal, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ItsmException("TICKET_NOT_FOUND", "Ticket not found."));
        if (!canView(principal, ticket)) {
            throw new AccessDeniedException("You cannot view this ticket.");
        }
        return ticket;
    }

    @Transactional(readOnly = true)
    public TicketAttachment requireAttachment(ItsmUserPrincipal principal, Long ticketId, Long attachmentId) {
        Ticket ticket = requireView(principal, ticketId);
        TicketAttachment att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ItsmException("ATTACHMENT_MISSING", "Attachment not found."));
        if (!att.getTicket().getTicketId().equals(ticket.getTicketId())) {
            throw new AccessDeniedException("Attachment does not belong to this ticket.");
        }
        return att;
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return ticketRepository.countByStatusCodeNotIn(CLOSED);
    }

    @Transactional(readOnly = true)
    public List<Ticket> recentFor(ItsmUserPrincipal principal) {
        Employee me = employeeRepository.findById(principal.getEmployeeId()).orElse(null);
        if (me == null) {
            return new ArrayList<Ticket>();
        }
        List<Ticket> list = ticketRepository.findTop8ByRequesterOrderByCreatedAtUtcDesc(me);
        for (Ticket t : list) {
            hydrate(t);
        }
        return list;
    }

    public boolean canView(ItsmUserPrincipal principal, Ticket ticket) {
        if (ticket.getRequester() != null && ticket.getRequester().getEmployeeId().equals(principal.getEmployeeId())) {
            return true;
        }
        if (ticket.getAssignedImplementor() != null
                && ticket.getAssignedImplementor().getEmployeeId().equals(principal.getEmployeeId())) {
            return true;
        }
        if (has(principal, "TICKET_VIEW_QUEUE_ALL") || has(principal, "ADMIN_SYSTEM")
                || has(principal, "AUDIT_VIEW")) {
            return true;
        }
        Employee me = employeeRepository.findById(principal.getEmployeeId()).orElse(null);
        if (me != null && ticket.getDepartment() != null && me.getDepartment() != null
                && me.getDepartment().getDepartmentId().equals(ticket.getDepartment().getDepartmentId())) {
            if (has(principal, "TICKET_VIEW_TEAM") || has(principal, "TICKET_VIEW_DEPARTMENT")) {
                return true;
            }
        }
        if (has(principal, "TICKET_VIEW_SECURITY")) {
            boolean sec = "Highly Confidential".equals(ticket.getConfidentialityCode());
            if (ticket.getTicketType() != null && "Security Incident".equals(ticket.getTicketType().getName())) {
                sec = true;
            }
            if (ticket.getCategory() != null && "Cyber Security".equals(ticket.getCategory().getName())) {
                sec = true;
            }
            if (sec) {
                return true;
            }
        }
        if (ticket.getWorkflowInstanceId() != null && me != null) {
            WorkflowInstance instance = instanceRepository.findById(ticket.getWorkflowInstanceId()).orElse(null);
            if (instance != null) {
                List<WorkflowInstanceStage> stages = workflowEngine.loadStages(instance);
                for (WorkflowInstanceStage s : stages) {
                    if ("Current".equals(s.getStatusCode()) && workflowEngine.isActor(me, principal, s)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Ticket applyForm(Ticket ticket, TicketForm form, Employee requester) {
        Validation lookups = new Validation();
        TicketType type = form.getTicketTypeId() == null ? null
                : ticketTypeRepository.findById(form.getTicketTypeId()).filter(TicketType::isActive).orElse(null);
        lookups.required(type, "Select a ticket type.");
        Category category = form.getCategoryId() == null ? null
                : categoryRepository.findById(form.getCategoryId()).filter(Category::isActive).orElse(null);
        lookups.required(category, "Select a category.");
        SubCategory sub = form.getSubCategoryId() == null ? null
                : subCategoryRepository.findById(form.getSubCategoryId()).filter(SubCategory::isActive).orElse(null);
        lookups.required(sub, "Select a sub-category.");
        if (category != null && sub != null) {
            lookups.check(sub.getCategory().getCategoryId().equals(category.getCategoryId()),
                    "Sub-category does not belong to the selected category.");
        }
        Validation v = new Validation();
        String subject = v.text(form.getSubject(), "Subject", FieldLimits.SUBJECT_MIN, FieldLimits.SUBJECT_MAX, true);
        String description = v.text(form.getDescription(), "Description",
                FieldLimits.DESCRIPTION_MIN, FieldLimits.DESCRIPTION_MAX, true);
        String priority = v.oneOf(nz(form.getPriorityCode(), "Medium"), "Priority", FieldLimits.PRIORITIES);
        String impact = v.oneOf(nz(form.getImpactCode(), "Individual"), "Impact", FieldLimits.IMPACTS);
        String urgency = v.oneOf(nz(form.getUrgencyCode(), nz(form.getPriorityCode(), "Medium")), "Urgency",
                FieldLimits.PRIORITIES);
        String confidentiality = v.oneOf(nz(form.getConfidentialityCode(), "Normal"), "Confidentiality",
                FieldLimits.CONFIDENTIALITY);
        String location = v.text(form.getLocation(), "Location", 0, FieldLimits.LOCATION_MAX, false);
        String application = v.text(form.getApplicationName(), "Application", 0, FieldLimits.APPLICATION_MAX, false);
        if (lookups.hasErrors() || v.hasErrors()) {
            List<String> all = new ArrayList<String>(lookups.errors());
            all.addAll(v.errors());
            throw new ItsmException(lookups.hasErrors() ? "LOOKUP" : "TICKET_INVALID", Validation.message(all));
        }

        ticket.setTicketType(type);
        ticket.setCategory(category);
        ticket.setSubCategory(sub);
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setPriorityCode(priority);
        ticket.setImpactCode(impact);
        ticket.setUrgencyCode(urgency);
        ticket.setConfidentialityCode(confidentiality);
        ticket.setLocation(location);
        ticket.setApplicationName(application);
        ticket.setMajorIncident(form.isMajorIncident());
        ticket.setRequester(requester);
        ticket.setDepartment(requester.getDepartment());
        return ticket;
    }

    private void hydrate(Ticket t) {
        if (t.getTicketType() != null) {
            t.getTicketType().getName();
        }
        if (t.getCategory() != null) {
            t.getCategory().getName();
        }
        if (t.getSubCategory() != null) {
            t.getSubCategory().getName();
        }
        if (t.getRequester() != null) {
            t.getRequester().getDisplayName();
        }
        if (t.getAssignedImplementor() != null) {
            t.getAssignedImplementor().getDisplayName();
        }
        if (t.getAssignedGroup() != null) {
            t.getAssignedGroup().getName();
        }
        if (t.getDepartment() != null) {
            t.getDepartment().getName();
        }
    }

    private boolean canSeeInternal(ItsmUserPrincipal principal) {
        return has(principal, "TICKET_VIEW_QUEUE_ALL") || has(principal, "TICKET_FULFIL")
                || has(principal, "TICKET_ASSIGN") || has(principal, "TICKET_APPROVE_ASSIGNED_STAGE")
                || has(principal, "ADMIN_SYSTEM");
    }

    private static boolean has(ItsmUserPrincipal principal, String code) {
        return principal.getAuthorities().stream().anyMatch(a -> code.equals(a.getAuthority()));
    }

    private static String nz(String v, String d) {
        return StringUtils.hasText(v) ? v : d;
    }
}
