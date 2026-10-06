package com.nbfc.itsm.ticket;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.ImacDetail;
import com.nbfc.itsm.domain.ImacDetailRepository;
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
import java.util.Map;
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
    private final AttachmentService attachmentService;
    private final com.nbfc.itsm.domain.TicketAssignmentLogRepository assignmentLogRepository;
    private final ImacDetailRepository imacDetailRepository;
    private final com.nbfc.itsm.domain.LocationRepository locationRepository;
    private final com.nbfc.itsm.admin.LocationService locationService;

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
                         NotificationService notificationService,
                         AttachmentService attachmentService,
                         com.nbfc.itsm.domain.TicketAssignmentLogRepository assignmentLogRepository,
                         ImacDetailRepository imacDetailRepository,
                         com.nbfc.itsm.domain.LocationRepository locationRepository,
                         com.nbfc.itsm.admin.LocationService locationService) {
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
        this.attachmentService = attachmentService;
        this.assignmentLogRepository = assignmentLogRepository;
        this.imacDetailRepository = imacDetailRepository;
        this.locationRepository = locationRepository;
        this.locationService = locationService;
    }

    /** Permission a user needs to raise an IMAC request; the ticket type whose code is this. */
    public static final String IMAC_TYPE = "IMAC";
    public static final String IMAC_PERMISSION = "TICKET_RAISE_IMAC";

    /**
     * Raise Request with an optional attachment: the file is checked first (PDF at most 5 MB) so a bad file
     * stops the whole request, then the ticket and the attachment are saved together.
     */
    @Transactional
    public Ticket save(ItsmUserPrincipal principal, TicketForm form, org.springframework.web.multipart.MultipartFile attachment) {
        boolean withFile = AttachmentService.present(attachment);
        if (withFile) {
            attachmentService.validate(attachment);
        }
        Ticket ticket = save(principal, form);
        if (withFile) {
            Employee uploader = employeeRepository.findById(principal.getEmployeeId())
                    .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
            attachmentService.store(ticket, uploader, attachment);
        }
        return ticket;
    }

    @Transactional
    public Ticket save(ItsmUserPrincipal principal, TicketForm form) {
        if (!has(principal, "TICKET_CREATE")) {
            throw new AccessDeniedException("Cannot create tickets.");
        }
        Employee requester = employeeRepository.findById(principal.getEmployeeId())
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
        Ticket ticket = applyForm(new Ticket(), form, requester);
        if (isImac(ticket) && !has(principal, IMAC_PERMISSION)) {
            throw new AccessDeniedException("You do not have access to raise IMAC requests. Ask the System Administrator.");
        }
        boolean draft = form.getIntent() != null && "draft".equalsIgnoreCase(form.getIntent());
        if (draft) {
            ticket.setStatusCode("Draft");
            ticket.setPublicNumber("DRAFT-" + requester.getEmployeeId() + "-" + UUID.randomUUID().toString().substring(0, 8));
            ticket = ticketRepository.save(ticket);
            upsertImacDetail(ticket, form);
            auditRecorder.recordTicket("CREATE_DRAFT", ticket.getTicketId(), null, ticket.getPublicNumber());
            return ticket;
        }
        ticket.setStatusCode("Pending Approval");
        ticket.setPublicNumber(ticketNumberService.allocate());
        ticket = ticketRepository.save(ticket);
        upsertImacDetail(ticket, form);
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
        if (isImac(ticket) && !has(principal, IMAC_PERMISSION)) {
            throw new AccessDeniedException("You do not have access to raise IMAC requests. Ask the System Administrator.");
        }
        ticket.setPublicNumber(ticketNumberService.allocate());
        ticket.setStatusCode("Pending Approval");
        ticket = ticketRepository.save(ticket);
        upsertImacDetail(ticket, form);
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
                if (current != null && "CONFIRMATION".equals(current.getStageType())) {
                    detail.setAutoCloseAt(workflowEngine.autoCloseAt(stages, current));
                }
                java.time.Instant until = workflowEngine.reopenUntil(ticket, stages);
                detail.setReopenUntil(until);
                detail.setCanReopen(until != null && ticket.getRequester() != null
                        && ticket.getRequester().getEmployeeId().equals(principal.getEmployeeId())
                        && com.nbfc.itsm.util.TimeUtc.now().isBefore(until));
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
                        if ("FULFILMENT".equals(current.getStageType())) {
                            allowed = implementorActions(allowed, ticket);
                        }
                        detail.setAllowed(allowed);
                    }
                }
            }
        }
        List<com.nbfc.itsm.domain.TicketAssignmentLog> handovers =
                assignmentLogRepository.findByTicketOrderByCreatedAtUtcAscTicketAssignmentLogIdAsc(ticket);
        for (com.nbfc.itsm.domain.TicketAssignmentLog h : handovers) {
            h.getToEmployee().getDisplayName();
            h.getByEmployee().getDisplayName();
            if (h.getFromEmployee() != null) {
                h.getFromEmployee().getDisplayName();
            }
        }
        detail.setAssignmentLog(handovers);
        boolean isRequester = ticket.getRequester() != null
                && ticket.getRequester().getEmployeeId().equals(principal.getEmployeeId());
        detail.setShowRequesterDetails(!isRequester);
        if (ticket.getRequester() != null && ticket.getRequester().getDepartment() != null) {
            ticket.getRequester().getDepartment().getName();
        }
        detail.setCanChangePriority(canChangePriority(principal, ticket));
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
        // "handled": every ticket this person is/was an actor on (approved, assigned to, resolved, routed to).
        final java.util.Set<Long> handledIds = new java.util.HashSet<Long>();
        if ("handled".equals(scope) && me != null) {
            for (WorkflowInstanceStage s : instanceStageRepository.findByResolvedEmployee(me)) {
                if (s.getWorkflowInstance() != null) {
                    handledIds.add(s.getWorkflowInstance().getTicketId());
                }
            }
            for (com.nbfc.itsm.domain.TicketAssignmentLog lg : assignmentLogRepository.findByToEmployeeOrByEmployee(me, me)) {
                if (lg.getTicket() != null) {
                    handledIds.add(lg.getTicket().getTicketId());
                }
            }
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
            } else if ("handled".equals(scope)) {
                List<Predicate> or = new ArrayList<Predicate>();
                if (me != null) {
                    or.add(cb.equal(root.get("assignedImplementor"), me));
                }
                if (!handledIds.isEmpty()) {
                    or.add(root.get("ticketId").in(handledIds));
                }
                and.add(or.isEmpty() ? cb.disjunction() : cb.or(or.toArray(new Predicate[0])));
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
        List<Ticket> tickets = new ArrayList<Ticket>(ticketsById(ids).values());
        for (Ticket t : tickets) {
            hydrate(t);
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
        java.util.Set<Long> offered = new java.util.LinkedHashSet<Long>();
        for (WorkflowInstanceStage s : instanceStageRepository.findByStatusCodeAndStageType("Current", "FULFILMENT")) {
            if (s.getResolvedEmployee() == null && s.getAssigneeIds().contains(me.getEmployeeId())) {
                Long ticketId = s.getWorkflowInstance().getTicketId();
                if (seen.add(ticketId)) {
                    offered.add(ticketId);
                }
            }
        }
        list.addAll(ticketsById(offered).values());
        list.sort(NEWEST_FIRST);
        for (Ticket t : list) {
            hydrate(t);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<Ticket> approvalsFor(ItsmUserPrincipal principal) {
        Employee me = employeeRepository.findById(principal.getEmployeeId()).orElse(null);
        List<Ticket> out = new ArrayList<Ticket>();
        if (me == null) {
            return out;
        }
        List<WorkflowInstanceStage> current = candidates(me,
                instanceStageRepository.findByStatusCodeAndStageType("Current", "APPROVAL"));
        Map<Long, Ticket> tickets = ticketsOf(current);
        for (WorkflowInstanceStage s : current) {
            Ticket t = tickets.get(s.getWorkflowInstance().getTicketId());
            if (t != null && canAct(principal, me, t, s)) {
                hydrate(t);
                out.add(t);
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
        List<WorkflowInstanceStage> current = new ArrayList<WorkflowInstanceStage>();
        for (WorkflowInstanceStage s : candidates(me, instanceStageRepository.findByStatusCode("Current"))) {
            if (WaitingItem.ACTION_BY_STAGE_TYPE.containsKey(s.getStageType())) {
                current.add(s);
            }
        }
        Map<Long, Ticket> tickets = ticketsOf(current);
        for (WorkflowInstanceStage s : current) {
            Ticket t = tickets.get(s.getWorkflowInstance().getTicketId());
            if (t != null && !CLOSED.contains(t.getStatusCode()) && canAct(principal, me, t, s)) {
                hydrate(t);
                out.add(new WaitingItem(t, s.getStageType(), s.getLabel()));
            }
        }
        out.sort((a, b) -> NEWEST_FIRST.compare(a.getTicket(), b.getTicket()));
        return out;
    }

    /**
     * Drops steps given to one named person other than me (and not delegated to me) before any ticket
     * is loaded: with many open tickets most current steps belong to someone else.
     */
    private static List<WorkflowInstanceStage> candidates(Employee me, List<WorkflowInstanceStage> stages) {
        List<WorkflowInstanceStage> out = new ArrayList<WorkflowInstanceStage>();
        for (WorkflowInstanceStage s : stages) {
            boolean someoneElse = s.getResolvedEmployee() != null
                    && !s.getResolvedEmployee().getEmployeeId().equals(me.getEmployeeId())
                    && !WorkflowEngine.isDelegateFor(me, s);
            if (!someoneElse) {
                out.add(s);
            }
        }
        return out;
    }

    private boolean canAct(ItsmUserPrincipal principal, Employee me, Ticket t, WorkflowInstanceStage s) {
        try {
            workflowEngine.assertCanAct(principal, me, t, s);
            return true;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }

    /** The tickets of these steps in one query (instead of one query per step). */
    private Map<Long, Ticket> ticketsOf(List<WorkflowInstanceStage> stages) {
        java.util.Set<Long> ids = new java.util.LinkedHashSet<Long>();
        for (WorkflowInstanceStage s : stages) {
            ids.add(s.getWorkflowInstance().getTicketId());
        }
        return ticketsById(ids);
    }

    private Map<Long, Ticket> ticketsById(java.util.Collection<Long> ids) {
        Map<Long, Ticket> out = new java.util.HashMap<Long, Ticket>();
        List<Long> all = new ArrayList<Long>(ids);
        // SQL Server accepts at most 2100 parameters per statement.
        for (int i = 0; i < all.size(); i += 1000) {
            for (Ticket t : ticketRepository.findAllById(all.subList(i, Math.min(i + 1000, all.size())))) {
                out.put(t.getTicketId(), t);
            }
        }
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

    /** IT Service Desk (or System Administrator) may change the priority of an open ticket. */
    public boolean canChangePriority(ItsmUserPrincipal principal, Ticket ticket) {
        boolean open = ticket != null && !CLOSED.contains(ticket.getStatusCode()) && !"Draft".equals(ticket.getStatusCode());
        return open && (has(principal, "TICKET_VIEW_QUEUE_ALL") || has(principal, "TICKET_ASSIGN")
                || has(principal, "ROLE_SYSTEM_ADMINISTRATOR"));
    }

    /**
     * Changes the priority (reason required), recalculates the SLA due times with the new priority's targets and
     * records it as a comment on the ticket and in the audit trail.
     */
    @Transactional
    public Ticket changePriority(ItsmUserPrincipal principal, Long ticketId, String priority, String reason) {
        Ticket ticket = requireView(principal, ticketId);
        if (!canChangePriority(principal, ticket)) {
            throw new AccessDeniedException("Only the IT Service Desk or a System Administrator can change the priority of an open ticket.");
        }
        if (priority == null || !FieldLimits.PRIORITIES.contains(priority)) {
            throw new ItsmException("VALIDATION", "Choose a valid priority.");
        }
        if (priority.equals(ticket.getPriorityCode())) {
            throw new ItsmException("VALIDATION", "The ticket already has priority " + priority + ".");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.length() < workflowEngine.remarksMin()) {
            throw new ItsmException("REMARKS_REQUIRED", "Give a reason (at least " + workflowEngine.remarksMin() + " characters).");
        }
        String old = ticket.getPriorityCode();
        ticket.setPriorityCode(priority);
        ticketRepository.save(ticket);
        slaService.recalculate(ticket);
        Employee actor = employeeRepository.findById(principal.getEmployeeId())
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
        TicketComment note = new TicketComment();
        note.setTicket(ticket);
        note.setAuthor(actor);
        note.setBody("Priority changed from " + old + " to " + priority + ". Reason: " + why);
        note.setInternal(false);
        note.setCreatedAtUtc(TimeUtc.now());
        commentRepository.save(note);
        auditRecorder.recordTicket("PRIORITY_CHANGE", ticket.getTicketId(), old, priority);
        return ticket;
    }

    /** "Not resolved" after an automatic closure: back to the implementor with the requester's reason. */
    @Transactional
    public Ticket reopen(ItsmUserPrincipal principal, Long ticketId, String reason) {
        requireView(principal, ticketId);
        return workflowEngine.reopenAfterAutoClose(ticketId, principal, reason);
    }

    /** "Resolved" after an automatic closure: stays closed and can no longer be re-opened. */
    @Transactional
    public Ticket confirmClosed(ItsmUserPrincipal principal, Long ticketId) {
        requireView(principal, ticketId);
        return workflowEngine.confirmAutoClose(ticketId, principal);
    }

    /** What an implementor can do on the implementation step, in this order (Accept / Start are not offered). */
    static final List<String> IMPLEMENTOR_ACTIONS = Arrays.asList("REASSIGN", "HOLD", "RESOLVE");

    private static List<WorkflowStageTransition> implementorActions(List<WorkflowStageTransition> all, Ticket ticket) {
        List<WorkflowStageTransition> out = new ArrayList<WorkflowStageTransition>();
        for (String code : IMPLEMENTOR_ACTIONS) {
            if ("HOLD".equals(code) && "On Hold".equals(ticket.getStatusCode())) {
                continue; // already on hold: Reassign or Resolve
            }
            for (WorkflowStageTransition t : all) {
                if (code.equals(t.getActionCode())) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** Category code that asks for the device serial number on Raise Request. */
    public static final String SERIAL_CATEGORY = "HARDWARE";
    public static final String SERIAL_NOT_AVAILABLE = "Not available";

    /**
     * Hardware tickets must either carry a serial number or say it is not available; other categories never
     * store one.
     */
    private static String serialNumberOf(Category category, TicketForm form, Validation v) {
        if (category == null || !SERIAL_CATEGORY.equals(category.getCode())) {
            return null;
        }
        String mode = form.getSerialMode() == null ? "" : form.getSerialMode().trim();
        if ("NA".equals(mode)) {
            return SERIAL_NOT_AVAILABLE;
        }
        if (!"ENTER".equals(mode)) {
            v.check(false, "Serial number is required for Hardware: enter it or choose Not available.");
            return null;
        }
        String serial = v.text(form.getSerialNumber(), "Serial number", 2, 100, true);
        if (serial != null && !serial.matches("[A-Za-z0-9 ._/#-]+")) {
            v.check(false, "Serial number may contain letters, numbers, spaces and . _ / # - only.");
        }
        return serial;
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
        String serial = serialNumberOf(category, form, v);
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
        ticket.setSerialNumber(serial);
        ticket.setMajorIncident(form.isMajorIncident());
        ticket.setRequester(requester);
        ticket.setDepartment(requester.getDepartment());
        return ticket;
    }

    private boolean isImac(Ticket t) {
        return t.getTicketType() != null && IMAC_TYPE.equalsIgnoreCase(t.getTicketType().getCode());
    }

    /** Saves (or updates) the IMAC detail row for an IMAC ticket; no-op for other ticket types. */
    private void upsertImacDetail(Ticket ticket, TicketForm form) {
        if (!isImac(ticket)) {
            return;
        }
        ImacDetail d = imacDetailRepository.findByTicketId(ticket.getTicketId()).orElse(new ImacDetail());
        d.setTicketId(ticket.getTicketId());
        d.setUsername(trimToLen(form.getImacUsername(), 128));
        d.setUserSapId(trimToLen(form.getImacSapId(), 64));
        d.setAsset(trimToLen(form.getImacAsset(), 128));
        d.setMake(trimToLen(form.getImacMake(), 128));
        d.setModel(trimToLen(form.getImacModel(), 128));
        d.setGrade(trimToLen(form.getImacGrade(), 64));
        d.setDepartment(trimToLen(form.getImacDepartment(), 128));
        d.setSerialNo(trimToLen(form.getImacSerialNo(), 128));
        d.setRam(trimToLen(form.getImacRam(), 64));
        d.setContactNo(trimToLen(form.getImacContactNo(), 64));
        com.nbfc.itsm.domain.Location loc = form.getImacLocationId() == null ? null
                : locationRepository.findById(form.getImacLocationId()).orElse(null);
        if (loc != null) {
            // Location drives the address and a generated, non-editable hostname (AUTH-<loc3>-NNNNNNN).
            String prevLocation = d.getLocation();
            d.setLocation(trimToLen(loc.getName(), 128));
            d.setOfficeAddress(trimToLen(loc.getAddress(), 2000));
            boolean needHostname = d.getHostname() == null || !d.getHostname().startsWith("AUTH-")
                    || !loc.getName().equals(prevLocation);
            if (needHostname) {
                d.setHostname(trimToLen(locationService.allocateHostname(loc.getName()), 128));
            }
        } else {
            d.setLocation(trimToLen(form.getImacLocation(), 128));
            d.setOfficeAddress(trimToLen(form.getImacOfficeAddress(), 2000));
            d.setHostname(trimToLen(form.getImacHostname(), 128));
        }
        imacDetailRepository.save(d);
    }

    /** IMAC detail for a ticket (for the detail page), or null. */
    @Transactional(readOnly = true)
    public ImacDetail imacDetailFor(Long ticketId) {
        return imacDetailRepository.findByTicketId(ticketId).orElse(null);
    }

    private static String trimToLen(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
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
