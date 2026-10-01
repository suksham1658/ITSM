package com.nbfc.itsm.workflow;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.SystemSettingRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.WorkflowDefinition;
import com.nbfc.itsm.domain.WorkflowInstance;
import com.nbfc.itsm.domain.WorkflowInstanceRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import com.nbfc.itsm.domain.WorkflowRule;
import com.nbfc.itsm.domain.WorkflowStage;
import com.nbfc.itsm.domain.WorkflowStageRepository;
import com.nbfc.itsm.domain.WorkflowStageTransition;
import com.nbfc.itsm.domain.WorkflowStageTransitionRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.notification.NotificationService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.sla.SlaService;
import com.nbfc.itsm.ticket.TicketMatchContext;
import com.nbfc.itsm.util.TimeUtc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkflowEngine {

    public static final int DEFAULT_REMARKS_MIN = 10;
    public static final int DEFAULT_HOP_CAP = 12;

    private final WorkflowMatcherService matcherService;
    private final WorkflowStageRepository stageRepository;
    private final WorkflowStageTransitionRepository transitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowInstanceStageRepository instanceStageRepository;
    private final TicketRepository ticketRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeRoleAssignmentRepository roleAssignmentRepository;
    private final GroupMembershipService groupMembership;
    private final SystemSettingRepository settingRepository;
    private final SlaService slaService;
    private final AuditRecorder auditRecorder;
    private final NotificationService notifications;
    private final com.nbfc.itsm.domain.TicketAssignmentLogRepository assignmentLog;

    public WorkflowEngine(WorkflowMatcherService matcherService,
                          WorkflowStageRepository stageRepository,
                          WorkflowStageTransitionRepository transitionRepository,
                          WorkflowInstanceRepository instanceRepository,
                          WorkflowInstanceStageRepository instanceStageRepository,
                          TicketRepository ticketRepository,
                          EmployeeRepository employeeRepository,
                          EmployeeRoleAssignmentRepository roleAssignmentRepository,
                          GroupMembershipService groupMembership,
                          SystemSettingRepository settingRepository,
                          SlaService slaService,
                          AuditRecorder auditRecorder,
                          NotificationService notifications,
                          com.nbfc.itsm.domain.TicketAssignmentLogRepository assignmentLog) {
        this.matcherService = matcherService;
        this.stageRepository = stageRepository;
        this.transitionRepository = transitionRepository;
        this.instanceRepository = instanceRepository;
        this.instanceStageRepository = instanceStageRepository;
        this.ticketRepository = ticketRepository;
        this.employeeRepository = employeeRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.groupMembership = groupMembership;
        this.settingRepository = settingRepository;
        this.slaService = slaService;
        this.auditRecorder = auditRecorder;
        this.notifications = notifications;
        this.assignmentLog = assignmentLog;
    }

    @Transactional
    public WorkflowInstance startOnSubmit(Ticket ticket) {
        if (ticket.getWorkflowInstanceId() != null) {
            throw new ItsmException("WORKFLOW_EXISTS", "This ticket already has a workflow instance.");
        }
        TicketMatchContext ctx = contextOf(ticket);
        WorkflowRule rule = matcherService.match(ctx);
        WorkflowDefinition definition = rule.getWorkflowDefinition();
        List<WorkflowStage> templates = stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(definition);
        if (templates.isEmpty()) {
            throw new ItsmException("WORKFLOW_EMPTY", "Matched workflow has no stages.");
        }

        WorkflowInstance instance = new WorkflowInstance();
        instance.setTicketId(ticket.getTicketId());
        instance.setWorkflowDefinition(definition);
        instance.setWorkflowRule(rule);
        instance.setStatusCode("InProgress");
        instance.setCreatedAtUtc(TimeUtc.now());
        instance = instanceRepository.save(instance);

        List<WorkflowInstanceStage> expanded = expand(ticket, instance, templates);
        if (expanded.isEmpty()) {
            throw new ItsmException("WORKFLOW_EXPAND", "Workflow expanded to zero stages.");
        }
        WorkflowInstanceStage first = firstActionable(expanded);
        if (first == null) {
            throw new ItsmException("WORKFLOW_EXPAND", "No actionable stage after expansion.");
        }
        first.setStatusCode("Current");
        instanceStageRepository.saveAll(expanded);
        instance.setCurrentStageId(first.getWorkflowInstanceStageId());
        instanceRepository.save(instance);

        ticket.setWorkflowInstanceId(instance.getWorkflowInstanceId());
        applyTicketStatus(ticket, first);
        ticketRepository.save(ticket);
        slaService.startClocks(ticket);
        auditRecorder.recordTicket("WORKFLOW_START", ticket.getTicketId(), null,
                definition.getCode() + " via rule " + rule.getName());
        notifications.submitted(ticket, first);
        notifications.stepIsWaiting(ticket, first);
        return instance;
    }

    @Transactional
    public Ticket applyAction(Long ticketId, ItsmUserPrincipal principal, String actionCode,
                              String remarks, Long assigneeId) {
        return applyActionFor(ticketId, principal, actionCode, remarks,
                assigneeId == null ? java.util.Collections.<Long>emptyList() : java.util.Collections.singletonList(assigneeId));
    }

    /** {@code assigneeIds}: for ASSIGN at the service desk one or more implementors; for REASSIGN exactly one. */
    @Transactional
    public Ticket applyActionFor(Long ticketId, ItsmUserPrincipal principal, String actionCode,
                              String remarks, List<Long> assigneeIds) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ItsmException("TICKET_NOT_FOUND", "Ticket not found."));
        Employee actor = employeeRepository.findById(principal.getEmployeeId())
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
        WorkflowInstance instance = instanceRepository.findByTicketId(ticket.getTicketId())
                .orElseThrow(() -> new ItsmException("WORKFLOW_MISSING", "Ticket has no workflow instance."));
        List<WorkflowInstanceStage> stages = instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(instance);
        WorkflowInstanceStage current = currentOf(stages);
        if (current == null) {
            throw new ItsmException("WORKFLOW_NO_CURRENT", "There is no current stage to act on.");
        }
        assertCanAct(principal, actor, ticket, current);
        boolean onBehalf = isDelegateFor(actor, current);

        String action = actionCode == null ? "" : actionCode.trim().toUpperCase();
        if ("FULFILMENT".equals(current.getStageType()) && current.getResolvedEmployee() == null
                && current.getAssigneeIds().contains(actor.getEmployeeId())
                && ("ACCEPT".equals(action) || "START".equals(action) || "HOLD".equals(action) || "RESOLVE".equals(action))) {
            // Sent to several implementors: the first one who works on it owns it.
            current.setResolvedEmployee(actor);
            ticket.setAssignedImplementor(actor);
        }
        if (onBehalf) {
            auditRecorder.recordTicket("DELEGATE_ACTION", ticket.getTicketId(), current.getResolvedEmployee().getEmployeeNo(),
                    action + " by " + actor.getEmployeeNo() + " as delegate of " + current.getResolvedEmployee().getEmployeeNo());
            if (remarks != null && !remarks.trim().isEmpty()) {
                remarks = "[On behalf of " + current.getResolvedEmployee().getDisplayName() + "] " + remarks.trim();
            }
        }
        WorkflowStageTransition transition = resolveTransition(current, action);
        if (transition == null) {
            throw new ItsmException("INVALID_TRANSITION",
                    "Action " + action + " is not allowed on stage " + current.getLabel() + ".");
        }
        boolean remarksNeeded = transition.isRemarksRequired()
                || "APPROVE".equals(action) || "REJECT".equals(action) || "SEND_BACK".equals(action);
        if ("CONFIRMATION".equals(current.getStageType()) && "APPROVE".equals(action)) {
            remarksNeeded = false; // the requester just confirms "Resolved"; "Not resolved" still needs a reason
        }
        if (remarksNeeded) {
            requireRemarks(remarks);
        }

        String oldStatus = ticket.getStatusCode();
        if ("APPROVE".equals(action)) {
            complete(current, actor, action, remarks);
            advance(ticket, instance, stages, current);
        } else if ("REJECT".equals(action)) {
            current.setStatusCode("Rejected");
            current.setActionCode(action);
            current.setRemarks(trim(remarks));
            current.setActedAtUtc(TimeUtc.now());
            skipRemaining(stages, current);
            ticket.setStatusCode("Rejected");
            ticket.setRejectReason(trim(remarks));
            instance.setStatusCode("Rejected");
            instance.setCurrentStageId(current.getWorkflowInstanceStageId());
        } else if ("SEND_BACK".equals(action)) {
            sendBack(ticket, instance, stages, current, remarks);
            if ("CONFIRMATION".equals(current.getStageType())) {
                slaService.reopen(ticket); // "Not resolved": the resolution clock runs again
            }
        } else if ("ASSIGN".equals(action) || "REASSIGN".equals(action)) {
            AssignmentGroup pool = assigneePool(stages, current);
            List<Employee> chosen = resolveAssignees(assigneeIds, eligibleImplementors(ticket, stages, current));
            if (pool != null) {
                ticket.setAssignedGroup(pool);
            }
            if ("FULFILMENT".equals(current.getStageType())) {
                // Hand the work to another implementor; the ticket stays on the implementation step.
                if (chosen.size() != 1) {
                    throw new ItsmException("ASSIGNEE_ONE", "Choose one implementor to hand the work over to.");
                }
                // The workflow shows who handed it to whom and why, so a comment is required.
                requireRemarks(remarks);
                Employee from = current.getResolvedEmployee() != null ? current.getResolvedEmployee() : actor;
                logAssignment(ticket, current, action, from, chosen.get(0), actor, remarks);
                if ("On Hold".equals(ticket.getStatusCode())) {
                    slaService.pause(ticket, false);
                }
                slaService.markFirstResponse(ticket);
                ticket.setAssignedImplementor(chosen.get(0));
                current.setResolvedEmployee(chosen.get(0));
                current.getAssigneeIds().clear();
                current.setActionCode(action);
                current.setRemarks(trim(remarks));
                current.setActedAtUtc(TimeUtc.now());
                ticket.setStatusCode("Assigned");
            } else {
                // Service desk step is done. One implementor: they own the next step. Several: all of them see
                // it and the first to accept / start owns it.
                for (Employee e : chosen) {
                    logAssignment(ticket, current, action, null, e, actor, remarks);
                }
                complete(current, actor, action, remarks);
                WorkflowInstanceStage next = nextPending(stages, current);
                if (next != null && ("IMPLEMENTOR".equals(next.getActorStrategy())
                        || "FULFILMENT".equals(next.getStageType()))) {
                    next.getAssigneeIds().clear();
                    if (chosen.size() == 1) {
                        next.setResolvedEmployee(chosen.get(0));
                    } else {
                        next.setResolvedEmployee(null);
                        for (Employee e : chosen) {
                            next.getAssigneeIds().add(e.getEmployeeId());
                        }
                    }
                }
                ticket.setAssignedImplementor(chosen.size() == 1 ? chosen.get(0) : null);
                advance(ticket, instance, stages, current);
                slaService.markFirstResponse(ticket);
            }
        } else if ("ACCEPT".equals(action) || "START".equals(action)) {
            current.setActionCode(action);
            current.setRemarks(trim(remarks));
            current.setActedAtUtc(TimeUtc.now());
            current.setResolvedEmployee(actor);
            ticket.setAssignedImplementor(actor);
            ticket.setStatusCode("In Progress");
            slaService.markFirstResponse(ticket);
            slaService.pause(ticket, false);
        } else if ("HOLD".equals(action)) {
            current.setActionCode(action);
            current.setRemarks(trim(remarks));
            current.setActedAtUtc(TimeUtc.now());
            ticket.setStatusCode("On Hold");
            slaService.markFirstResponse(ticket);
            slaService.pause(ticket, true);
        } else if ("RESOLVE".equals(action)) {
            complete(current, actor, action, remarks);
            advance(ticket, instance, stages, current);
            slaService.markResolved(ticket);
        } else if ("COMPLETE".equals(action)) {
            complete(current, actor, action, remarks);
            advance(ticket, instance, stages, current);
        } else {
            throw new ItsmException("INVALID_TRANSITION", "Unsupported action " + action + ".");
        }

        instanceStageRepository.saveAll(stages);
        instanceRepository.save(instance);
        ticketRepository.save(ticket);
        slaService.refresh(ticket);
        auditRecorder.recordTicket(action, ticket.getTicketId(), oldStatus, ticket.getStatusCode());
        notifyAfter(ticket, actor, action, remarks, current, currentOf(stages));
        return ticket;
    }

    /**
     * Tells the requester what happened and the next people what is now waiting for them.
     * {@code acted} is the step the action was taken on; {@code now} is the step current afterwards.
     */
    private void notifyAfter(Ticket ticket, Employee actor, String action, String remarks,
                             WorkflowInstanceStage acted, WorkflowInstanceStage now) {
        boolean moved = now != null && now != acted;
        if ("Closed".equals(ticket.getStatusCode())) {
            notifications.closed(ticket, actor);
            return;
        }
        if ("APPROVE".equals(action)) {
            notifications.approved(ticket, actor, now);
        } else if ("REJECT".equals(action)) {
            notifications.rejected(ticket, actor, remarks);
            return;
        } else if ("SEND_BACK".equals(action)) {
            notifications.sentBack(ticket, actor, remarks, now);
            return;
        } else if ("ASSIGN".equals(action) || "REASSIGN".equals(action)) {
            if (ticket.getAssignedImplementor() != null) {
                notifications.assigned(ticket, actor, ticket.getAssignedImplementor());
            }
            if (!moved) {
                // Reassigned within the implementation step: tell the new owner.
                notifications.stepIsWaiting(ticket, acted);
            }
        } else if ("ACCEPT".equals(action) || "START".equals(action)) {
            notifications.statusUpdate(ticket, actor, "In progress",
                    "is being worked on by " + actor.getDisplayName() + ".");
        } else if ("HOLD".equals(action)) {
            notifications.statusUpdate(ticket, actor, "On hold",
                    "was put on hold by " + actor.getDisplayName()
                            + (remarks == null || remarks.trim().isEmpty() ? "." : ": " + remarks.trim()));
        }
        if (moved) {
            notifications.stepIsWaiting(ticket, now);
        }
    }

    /**
     * The actor is the delegate (backup approver, Admin &gt; Users) of the person this approval step is
     * resolved to. Approval steps only; the delegate still needs the approve permission.
     */
    public static boolean isDelegateFor(Employee actor, WorkflowInstanceStage current) {
        Employee owner = current.getResolvedEmployee();
        return actor != null && owner != null && "APPROVAL".equals(current.getStageType())
                && owner.getDelegate() != null && owner.getDelegate().isPortalActive()
                && owner.getDelegate().getEmployeeId().equals(actor.getEmployeeId())
                && !owner.getEmployeeId().equals(actor.getEmployeeId());
    }

    public void assertCanAct(ItsmUserPrincipal principal, Employee actor, Ticket ticket, WorkflowInstanceStage current) {
        String type = current.getStageType();
        // Approving needs the approve permission, unless the workflow names one of your roles as this step's
        // approver (e.g. the IT Service Desk role viewing as "IT Service Desk").
        boolean namedRole = current.getResolvedRole() != null
                && principal.getRoleCodes().contains(current.getResolvedRole().getCode());
        if ("APPROVAL".equals(type) && !namedRole && !principal.getAuthorities().stream()
                .anyMatch(a -> "TICKET_APPROVE_ASSIGNED_STAGE".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Not an assigned approver.");
        }
        if ("ASSIGNMENT".equals(type) && !hasAuth(principal, "TICKET_ASSIGN") && !hasAuth(principal, "TICKET_VIEW_QUEUE_ALL")) {
            throw new AccessDeniedException("Cannot assign this ticket.");
        }
        if ("FULFILMENT".equals(type) && !hasAuth(principal, "TICKET_FULFIL")) {
            throw new AccessDeniedException("Cannot fulfil this ticket.");
        }
        if ("CONFIRMATION".equals(type)) {
            if (!ticket.getRequester().getEmployeeId().equals(actor.getEmployeeId())) {
                throw new AccessDeniedException("Only the requester can confirm.");
            }
            return;
        }
        if ("CLOSURE".equals(type)) {
            return;
        }
        if (!isActor(actor, principal, current)) {
            throw new AccessDeniedException("You are not the actor for this stage.");
        }
    }

    public boolean isActor(Employee actor, ItsmUserPrincipal principal, WorkflowInstanceStage current) {
        if (current.getResolvedEmployee() != null
                && current.getResolvedEmployee().getEmployeeId().equals(actor.getEmployeeId())) {
            return true;
        }
        if (isDelegateFor(actor, current)) {
            return true;
        }
        if (current.getResolvedEmployee() == null && !current.getAssigneeIds().isEmpty()) {
            // Sent by the service desk to these implementors only, until one of them picks it up.
            return current.getAssigneeIds().contains(actor.getEmployeeId());
        }
        if (current.getResolvedRole() != null) {
            String code = current.getResolvedRole().getCode();
            if (principal.getRoleCodes().contains(code)) {
                return true;
            }
        }
        if (current.getResolvedGroup() != null) {
            return groupMembership.isMember(current.getResolvedGroup(), actor);
        }
        String strategy = current.getActorStrategy();
        if ("NAMED_ROLE".equals(strategy) && current.getResolvedRole() != null) {
            return principal.getRoleCodes().contains(current.getResolvedRole().getCode());
        }
        if ("SERVICE_DESK".equals(strategy) || "ASSIGNMENT_GROUP".equals(strategy) || "IMPLEMENTOR".equals(strategy)) {
            if (current.getResolvedGroup() != null) {
                return groupMembership.isMember(current.getResolvedGroup(), actor);
            }
        }
        if ("REQUESTER".equals(strategy)) {
            return true;
        }
        return false;
    }

    /**
     * System Administrator override: reject any open ticket at whatever step it is, with remarks. The current
     * step is marked Rejected ("[Rejected by System Administrator …]"), the remaining steps are skipped, the
     * requester is notified and the override is audited (ADMIN_REJECT). No approve / assign on others' behalf.
     */
    @Transactional
    public Ticket adminReject(Long ticketId, ItsmUserPrincipal principal, String remarks) {
        if (principal == null || !principal.getRoleCodes().contains("SYSTEM_ADMINISTRATOR")) {
            throw new AccessDeniedException("Only a System Administrator can reject any ticket.");
        }
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ItsmException("TICKET_NOT_FOUND", "Ticket not found."));
        if ("Closed".equals(ticket.getStatusCode()) || "Rejected".equals(ticket.getStatusCode())) {
            throw new ItsmException("TICKET_FINISHED", "Ticket " + ticket.getPublicNumber() + " is already "
                    + ticket.getStatusCode().toLowerCase() + ".");
        }
        WorkflowInstance instance = instanceRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ItsmException("WORKFLOW_MISSING", "A draft has no workflow yet; the requester can delete or submit it."));
        List<WorkflowInstanceStage> stages = instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(instance);
        WorkflowInstanceStage current = currentOf(stages);
        if (current == null) {
            throw new ItsmException("WORKFLOW_NO_CURRENT", "There is no open step on this ticket.");
        }
        requireRemarks(remarks);
        Employee admin = employeeRepository.findById(principal.getEmployeeId())
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found."));
        String note = "[Rejected by System Administrator " + admin.getDisplayName() + "] " + trim(remarks);
        String oldStatus = ticket.getStatusCode();
        current.setStatusCode("Rejected");
        current.setActionCode("REJECT");
        current.setRemarks(note.length() > 2000 ? note.substring(0, 2000) : note);
        current.setActedAtUtc(TimeUtc.now());
        skipRemaining(stages, current);
        ticket.setStatusCode("Rejected");
        ticket.setRejectReason(trim(remarks));
        instance.setStatusCode("Rejected");
        instance.setCurrentStageId(current.getWorkflowInstanceStageId());
        instanceStageRepository.saveAll(stages);
        instanceRepository.save(instance);
        ticketRepository.save(ticket);
        slaService.refresh(ticket);
        auditRecorder.recordTicket("ADMIN_REJECT", ticket.getTicketId(), oldStatus,
                "Rejected by System Administrator " + admin.getEmployeeNo() + " at step '" + current.getLabel() + "': " + trim(remarks));
        notifications.rejected(ticket, admin, trim(remarks));
        return ticket;
    }

    public void requireRemarks(String remarks) {
        String trimmed = trim(remarks);
        int min = remarksMin();
        if (trimmed.length() < min) {
            throw new ItsmException("REMARKS_REQUIRED",
                    "Remarks are mandatory (at least " + min + " characters after trimming).");
        }
    }

    /** Records a hand-over for the ticket's workflow history (service desk ASSIGN or implementor REASSIGN). */
    private void logAssignment(Ticket ticket, WorkflowInstanceStage stage, String action, Employee from, Employee to,
                               Employee by, String remarks) {
        com.nbfc.itsm.domain.TicketAssignmentLog row = new com.nbfc.itsm.domain.TicketAssignmentLog();
        row.setTicket(ticket);
        row.setStageId(stage.getWorkflowInstanceStageId());
        row.setActionCode(action);
        row.setFromEmployee(from);
        row.setToEmployee(to);
        row.setByEmployee(by);
        row.setRemarks(trim(remarks));
        row.setCreatedAtUtc(TimeUtc.now());
        assignmentLog.save(row);
    }

    /** System Configuration switch; off (default) = a resolved ticket closes without asking the requester. */
    public boolean requesterConfirmation() {
        return settingRepository.findById("workflow.requester-confirmation")
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(true);
    }

    /** Hours a resolved ticket waits for the requester before it closes automatically (default 48). */
    public int confirmationHours() {
        return settingRepository.findById("workflow.confirmation-hours")
                .map(s -> parseInt(s.getSettingValue(), 48)).orElse(48);
    }

    /** Hours the requester may re-open a ticket after an automatic closure (default 48). */
    public int reopenHours() {
        return settingRepository.findById("workflow.reopen-hours")
                .map(s -> parseInt(s.getSettingValue(), 48)).orElse(48);
    }

    // ------------------------------------------------------------------ requester confirmation: auto-close / re-open

    /** Action code on the confirmation step when the system closed the ticket for lack of an answer. */
    public static final String AUTO_CLOSE = "AUTO_CLOSE";
    /** Action code after the requester confirmed an automatically closed ticket (no re-open any more). */
    public static final String CONFIRMED = "CONFIRMED";

    /** When the ticket reached the requester for confirmation: the time the step before it was completed. */
    public java.time.Instant waitingSince(List<WorkflowInstanceStage> stages, WorkflowInstanceStage confirmation) {
        WorkflowInstanceStage prev = previousCompleted(stages, confirmation);
        return prev != null ? prev.getActedAtUtc() : null;
    }

    /** When a ticket waiting for the requester closes on its own, or null. */
    public java.time.Instant autoCloseAt(List<WorkflowInstanceStage> stages, WorkflowInstanceStage confirmation) {
        java.time.Instant since = waitingSince(stages, confirmation);
        return since == null ? null : since.plus(java.time.Duration.ofHours(confirmationHours()));
    }

    /**
     * Closes a ticket whose requester did not confirm within the window. Safe to call repeatedly and from
     * several servers: it does nothing unless the step is still waiting and the window has passed.
     */
    @Transactional
    public boolean autoCloseIfDue(Long stageId, java.time.Instant now) {
        WorkflowInstanceStage stage = instanceStageRepository.findById(stageId).orElse(null);
        if (stage == null || !"Current".equals(stage.getStatusCode()) || !"CONFIRMATION".equals(stage.getStageType())) {
            return false;
        }
        WorkflowInstance instance = stage.getWorkflowInstance();
        List<WorkflowInstanceStage> stages = instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(instance);
        WorkflowInstanceStage current = null;
        for (WorkflowInstanceStage s : stages) {
            if (s.getWorkflowInstanceStageId().equals(stageId)) {
                current = s;
            }
        }
        java.time.Instant due = autoCloseAt(stages, current);
        if (current == null || due == null || now.isBefore(due)) {
            return false;
        }
        Ticket ticket = ticketRepository.findById(instance.getTicketId()).orElse(null);
        if (ticket == null) {
            return false;
        }
        current.setStatusCode("Completed");
        current.setActionCode(AUTO_CLOSE);
        current.setRemarks("Closed automatically: no answer from the requester within " + hoursText(confirmationHours()) + ".");
        current.setActedAtUtc(TimeUtc.now());
        String oldStatus = ticket.getStatusCode();
        advance(ticket, instance, stages, current);
        instanceStageRepository.saveAll(stages);
        instanceRepository.save(instance);
        ticketRepository.save(ticket);
        auditRecorder.recordTicket(AUTO_CLOSE, ticket.getTicketId(), oldStatus, ticket.getStatusCode());
        notifications.autoClosed(ticket);
        return true;
    }

    /** Until when the requester may still re-open this automatically closed ticket, or null. */
    public java.time.Instant reopenUntil(Ticket ticket, List<WorkflowInstanceStage> stages) {
        if (!"Closed".equals(ticket.getStatusCode())) {
            return null;
        }
        for (WorkflowInstanceStage s : stages) {
            if ("CONFIRMATION".equals(s.getStageType()) && AUTO_CLOSE.equals(s.getActionCode()) && s.getActedAtUtc() != null) {
                return s.getActedAtUtc().plus(java.time.Duration.ofHours(reopenHours()));
            }
        }
        return null;
    }

    /** "Not resolved" after an automatic closure: the ticket goes back to the implementor. Requester only. */
    @Transactional
    public Ticket reopenAfterAutoClose(Long ticketId, ItsmUserPrincipal principal, String reason) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ItsmException("TICKET_NOT_FOUND", "Ticket not found."));
        assertRequester(ticket, principal);
        requireRemarks(reason);
        WorkflowInstance instance = instanceRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ItsmException("WORKFLOW_MISSING", "Ticket has no workflow instance."));
        List<WorkflowInstanceStage> stages = instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(instance);
        assertReopenable(ticket, stages);
        WorkflowInstanceStage confirmation = null;
        for (WorkflowInstanceStage s : stages) {
            if ("CONFIRMATION".equals(s.getStageType()) && AUTO_CLOSE.equals(s.getActionCode())) {
                confirmation = s;
            }
        }
        WorkflowInstanceStage work = previousCompleted(stages, confirmation);
        if (work == null) {
            throw new ItsmException("REOPEN_TARGET", "There is no implementation step to send this ticket back to.");
        }
        boolean after = false;
        for (WorkflowInstanceStage s : stages) {
            if (s == confirmation) {
                after = true;
                continue;
            }
            if (after && ("Completed".equals(s.getStatusCode()) || "Current".equals(s.getStatusCode()))) {
                s.setStatusCode("Pending");
                s.setActionCode(null);
                s.setActedAtUtc(null);
            }
        }
        confirmation.setStatusCode("Pending");
        confirmation.setActionCode("REOPENED");
        confirmation.setRemarks("Re-opened by the requester: " + trim(reason));
        confirmation.setActedAtUtc(TimeUtc.now());
        work.setStatusCode("Current");
        work.setActionCode(null);
        instance.setStatusCode("InProgress");
        instance.setCurrentStageId(work.getWorkflowInstanceStageId());
        applyTicketStatus(ticket, work);
        slaService.reopen(ticket);
        instanceStageRepository.saveAll(stages);
        instanceRepository.save(instance);
        ticketRepository.save(ticket);
        auditRecorder.recordTicket("REOPEN", ticket.getTicketId(), "Closed", ticket.getStatusCode());
        Employee actor = employeeRepository.findById(principal.getEmployeeId()).orElse(ticket.getRequester());
        notifications.sentBack(ticket, actor, reason, work);
        return ticket;
    }

    /** "Resolved" after an automatic closure: the requester agrees; the ticket stays closed for good. */
    @Transactional
    public Ticket confirmAutoClose(Long ticketId, ItsmUserPrincipal principal) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ItsmException("TICKET_NOT_FOUND", "Ticket not found."));
        assertRequester(ticket, principal);
        WorkflowInstance instance = instanceRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ItsmException("WORKFLOW_MISSING", "Ticket has no workflow instance."));
        List<WorkflowInstanceStage> stages = instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(instance);
        assertReopenable(ticket, stages);
        for (WorkflowInstanceStage s : stages) {
            if ("CONFIRMATION".equals(s.getStageType()) && AUTO_CLOSE.equals(s.getActionCode())) {
                s.setActionCode(CONFIRMED);
                s.setRemarks("Requester confirmed the issue is resolved (after automatic closure).");
            }
        }
        instanceStageRepository.saveAll(stages);
        auditRecorder.recordTicket(CONFIRMED, ticket.getTicketId(), "Closed", "Closed");
        return ticket;
    }

    private void assertRequester(Ticket ticket, ItsmUserPrincipal principal) {
        if (ticket.getRequester() == null || !ticket.getRequester().getEmployeeId().equals(principal.getEmployeeId())) {
            throw new AccessDeniedException("Only the requester can do this.");
        }
    }

    private void assertReopenable(Ticket ticket, List<WorkflowInstanceStage> stages) {
        java.time.Instant until = reopenUntil(ticket, stages);
        if (until == null) {
            throw new ItsmException("REOPEN_NOT_ALLOWED", "This ticket was not closed automatically, or it was already confirmed.");
        }
        if (TimeUtc.now().isAfter(until)) {
            throw new ItsmException("REOPEN_EXPIRED", "The re-open period (" + hoursText(reopenHours())
                    + " after closure) has ended. Please raise a new request.");
        }
    }

    /** 48 -> "2 days", 36 -> "36 hours". */
    public static String hoursText(int hours) {
        if (hours % 24 == 0) {
            int d = hours / 24;
            return d + (d == 1 ? " day" : " days");
        }
        return hours + (hours == 1 ? " hour" : " hours");
    }

    public int remarksMin() {
        return settingRepository.findById("approval.remarks-min-length")
                .map(s -> parseInt(s.getSettingValue(), DEFAULT_REMARKS_MIN))
                .orElse(DEFAULT_REMARKS_MIN);
    }

    public WorkflowStageTransition resolveTransition(WorkflowInstanceStage current, String action) {
        if (current.getWorkflowStage() != null) {
            return transitionRepository.findByWorkflowStageAndActionCode(current.getWorkflowStage(), action).orElse(null);
        }
        return null;
    }

    public List<WorkflowInstanceStage> loadStages(WorkflowInstance instance) {
        return instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(instance);
    }

    private List<WorkflowInstanceStage> expand(Ticket ticket, WorkflowInstance instance, List<WorkflowStage> templates) {
        List<WorkflowInstanceStage> out = new ArrayList<WorkflowInstanceStage>();
        int order = 10;
        Employee requester = ticket.getRequester();
        for (WorkflowStage template : templates) {
            if ("DYNAMIC_HIERARCHY_TO_HOD".equals(template.getActorStrategy())) {
                List<Employee> hops = managerHops(requester);
                int hopNo = 1;
                for (Employee hop : hops) {
                    WorkflowInstanceStage row = baseFrom(template, instance, order);
                    row.setCode(template.getCode() + "_" + hopNo);
                    row.setLabel(hop.getDisplayName());
                    row.setStageType("APPROVAL");
                    row.setActorStrategy("LDAP_MANAGER");
                    row.setResolvedEmployee(hop);
                    row.setStatusCode("Pending");
                    out.add(row);
                    order += 10;
                    hopNo++;
                }
            } else {
                WorkflowInstanceStage row = baseFrom(template, instance, order);
                row.setStatusCode("Pending");
                resolveStaticActor(row, template, requester);
                out.add(row);
                order += 10;
            }
        }
        return out;
    }

    private void resolveStaticActor(WorkflowInstanceStage row, WorkflowStage template, Employee requester) {
        String strategy = template.getActorStrategy();
        if ("NAMED_ROLE".equals(strategy)) {
            row.setResolvedRole(template.getRole());
        } else if ("SERVICE_DESK".equals(strategy) || "ASSIGNMENT_GROUP".equals(strategy)
                || "IMPLEMENTOR".equals(strategy)) {
            row.setResolvedGroup(template.getAssignmentGroup());
        } else if ("REQUESTER".equals(strategy)) {
            row.setResolvedEmployee(requester);
        } else if ("LDAP_MANAGER".equals(strategy)) {
            if (requester.getManager() == null || !requester.getManager().isPortalActive()) {
                throw new ItsmException("NO_MANAGER", "Requester has no active manager for this workflow.");
            }
            row.setResolvedEmployee(requester.getManager());
        } else if ("LDAP_HOD".equals(strategy)) {
            if (requester.getHod() == null || !requester.getHod().isPortalActive()) {
                throw new ItsmException("NO_HOD", "Requester has no active HOD for this workflow.");
            }
            row.setResolvedEmployee(requester.getHod());
        }
    }

    /** Approvers of the manager-hierarchy steps for a new Service Request from {@code requester}, in order. */
    public List<Employee> managerHops(Employee requester) {
        if (isHod(requester)) {
            return new ArrayList<Employee>();
        }
        if (requester.getManager() == null) {
            throw new ItsmException("NO_MANAGER_CHAIN",
                    "This request needs your manager's approval first, but no manager is set for you. "
                            + "Ask the System Administrator to set your manager (Admin > Users), or have it set in "
                            + "Active Directory and sign in again. You can use Save draft to keep your request meanwhile.");
        }
        int cap = hopCap();
        Employee setHod = requester.getHod();
        if (setHod != null) {
            // An HOD set on Admin > Users always ends the chain: climb to it if it is above the requester,
            // otherwise go from the immediate manager straight to it.
            List<Employee> hops = pathToHod(requester.getManager(), setHod, cap);
            if (hops == null) {
                hops = new ArrayList<Employee>();
                hops.add(requester.getManager());
                if (!requester.getManager().getEmployeeId().equals(setHod.getEmployeeId())) {
                    hops.add(setHod);
                }
            }
            for (Employee hop : hops) {
                requireActive(hop);
            }
            return hops;
        }
        List<Employee> hops = new ArrayList<Employee>();
        Set<Long> seen = new HashSet<Long>();
        Employee current = requester.getManager();
        int n = 0;
        while (current != null && n < cap) {
            if (!seen.add(current.getEmployeeId())) {
                break;
            }
            requireActive(current);
            hops.add(current);
            if (isHod(current)) {
                break;
            }
            Employee next = current.getManager();
            if (next == null || next.getEmployeeId().equals(current.getEmployeeId())) {
                break;
            }
            current = next;
            n++;
        }
        if (hops.isEmpty()) {
            throw new ItsmException("NO_MANAGER_CHAIN", "Manager chain is empty.");
        }
        return hops;
    }

    /** Managers from {@code start} up to and including {@code hod}; null when {@code hod} is not in that chain. */
    private static List<Employee> pathToHod(Employee start, Employee hod, int cap) {
        List<Employee> path = new ArrayList<Employee>();
        Set<Long> seen = new HashSet<Long>();
        Employee current = start;
        while (current != null && path.size() <= cap && seen.add(current.getEmployeeId())) {
            path.add(current);
            if (current.getEmployeeId().equals(hod.getEmployeeId())) {
                return path;
            }
            current = current.getManager();
        }
        return null;
    }

    private static void requireActive(Employee approver) {
        if (!approver.isPortalActive()) {
            throw new ItsmException("INACTIVE_MANAGER",
                    "Your approval chain includes " + approver.getDisplayName() + ", whose portal access is disabled. "
                            + "Ask the System Administrator to enable it or change the reporting line.");
        }
    }

    private boolean isHod(Employee employee) {
        if (employee.getHod() != null && employee.getHod().getEmployeeId().equals(employee.getEmployeeId())) {
            return true;
        }
        for (EmployeeRoleAssignment a : roleAssignmentRepository.findByEmployee(employee)) {
            if (a.getRole() != null && "HOD".equals(a.getRole().getCode()) && a.getRole().isActive()) {
                return employee.getHod() == null || employee.getHod().getEmployeeId().equals(employee.getEmployeeId());
            }
        }
        return false;
    }

    private WorkflowInstanceStage baseFrom(WorkflowStage template, WorkflowInstance instance, int order) {
        WorkflowInstanceStage row = new WorkflowInstanceStage();
        row.setWorkflowInstance(instance);
        row.setWorkflowStage(template);
        row.setStageOrder(order);
        row.setCode(template.getCode());
        row.setLabel(template.getLabel());
        row.setStageType(template.getStageType());
        row.setActorStrategy(template.getActorStrategy());
        return row;
    }

    private void complete(WorkflowInstanceStage current, Employee actor, String action, String remarks) {
        current.setStatusCode("Completed");
        current.setActionCode(action);
        current.setRemarks(trim(remarks));
        current.setActedAtUtc(TimeUtc.now());
        if (current.getResolvedEmployee() == null) {
            current.setResolvedEmployee(actor);
        }
    }

    private void advance(Ticket ticket, WorkflowInstance instance, List<WorkflowInstanceStage> stages,
                         WorkflowInstanceStage justCompleted) {
        WorkflowInstanceStage next = nextPending(stages, justCompleted);
        if (!requesterConfirmation()) {
            while (next != null && "CONFIRMATION".equals(next.getStageType())) {
                next.setStatusCode("Skipped");
                next.setActedAtUtc(TimeUtc.now());
                next = nextPending(stages, next);
            }
        }
        while (next != null && "CLOSURE".equals(next.getStageType()) && "SYSTEM".equals(next.getActorStrategy())) {
            next.setStatusCode("Completed");
            next.setActionCode("COMPLETE");
            next.setActedAtUtc(TimeUtc.now());
            ticket.setStatusCode("Closed");
            instance.setStatusCode("Completed");
            instance.setCurrentStageId(next.getWorkflowInstanceStageId());
            slaService.markResolved(ticket);
            return;
        }
        if (next == null) {
            ticket.setStatusCode("Closed");
            instance.setStatusCode("Completed");
            instance.setCurrentStageId(justCompleted.getWorkflowInstanceStageId());
            slaService.markResolved(ticket);
            return;
        }
        next.setStatusCode("Current");
        instance.setCurrentStageId(next.getWorkflowInstanceStageId());
        applyTicketStatus(ticket, next);
    }

    private void sendBack(Ticket ticket, WorkflowInstance instance, List<WorkflowInstanceStage> stages,
                          WorkflowInstanceStage current, String remarks) {
        current.setStatusCode("Pending");
        current.setActionCode("SEND_BACK");
        current.setRemarks(trim(remarks));
        current.setActedAtUtc(TimeUtc.now());
        String target = current.getWorkflowStage() != null ? current.getWorkflowStage().getSendBackTarget() : null;
        WorkflowInstanceStage dest = null;
        if ("REQUESTER".equals(target)) {
            dest = findByStrategy(stages, "REQUESTER");
        }
        if (dest == null) {
            dest = previousCompleted(stages, current);
        }
        if (dest == null) {
            throw new ItsmException("SEND_BACK_TARGET", "No previous stage to send back to.");
        }
        dest.setStatusCode("Current");
        dest.setActionCode(null);
        instance.setCurrentStageId(dest.getWorkflowInstanceStageId());
        applyTicketStatus(ticket, dest);
    }

    private void skipRemaining(List<WorkflowInstanceStage> stages, WorkflowInstanceStage from) {
        boolean after = false;
        for (WorkflowInstanceStage s : stages) {
            if (s.getWorkflowInstanceStageId() != null
                    && from.getWorkflowInstanceStageId() != null
                    && s.getWorkflowInstanceStageId().equals(from.getWorkflowInstanceStageId())) {
                after = true;
                continue;
            }
            if (after && "Pending".equals(s.getStatusCode())) {
                s.setStatusCode("Skipped");
            }
        }
    }

    private WorkflowInstanceStage nextPending(List<WorkflowInstanceStage> stages, WorkflowInstanceStage from) {
        boolean after = false;
        for (WorkflowInstanceStage s : stages) {
            if (s == from || (s.getStageOrder() == from.getStageOrder() && s.getCode().equals(from.getCode()))) {
                after = true;
                continue;
            }
            if (after && ("Pending".equals(s.getStatusCode()) || "Current".equals(s.getStatusCode()))) {
                return s;
            }
        }
        return null;
    }

    private WorkflowInstanceStage previousCompleted(List<WorkflowInstanceStage> stages, WorkflowInstanceStage from) {
        WorkflowInstanceStage prev = null;
        for (WorkflowInstanceStage s : stages) {
            if (s == from || s.getStageOrder() == from.getStageOrder() && s.getCode().equals(from.getCode())) {
                break;
            }
            if ("Completed".equals(s.getStatusCode()) || "Current".equals(s.getStatusCode())) {
                prev = s;
            }
        }
        return prev;
    }

    private WorkflowInstanceStage findByStrategy(List<WorkflowInstanceStage> stages, String strategy) {
        for (WorkflowInstanceStage s : stages) {
            if (strategy.equals(s.getActorStrategy())) {
                return s;
            }
        }
        return null;
    }

    private WorkflowInstanceStage currentOf(List<WorkflowInstanceStage> stages) {
        for (WorkflowInstanceStage s : stages) {
            if ("Current".equals(s.getStatusCode())) {
                return s;
            }
        }
        return null;
    }

    private WorkflowInstanceStage firstActionable(List<WorkflowInstanceStage> stages) {
        for (WorkflowInstanceStage s : stages) {
            if (!"CLOSURE".equals(s.getStageType()) || !"SYSTEM".equals(s.getActorStrategy())) {
                return s;
            }
        }
        return stages.isEmpty() ? null : stages.get(0);
    }

    private void applyTicketStatus(Ticket ticket, WorkflowInstanceStage current) {
        String type = current.getStageType();
        if ("APPROVAL".equals(type)) {
            ticket.setStatusCode("Pending Approval");
            ticket.setProgressCode(current.getLabel());
        } else if ("ASSIGNMENT".equals(type)) {
            ticket.setStatusCode(ticket.getAssignedImplementor() == null ? "Approved" : "Assigned");
            ticket.setProgressCode(current.getLabel());
            if (current.getResolvedGroup() != null) {
                ticket.setAssignedGroup(current.getResolvedGroup());
            }
        } else if ("FULFILMENT".equals(type)) {
            // "Assigned" until the implementor clicks START / ACCEPT, which sets "In Progress".
            ticket.setStatusCode(ticket.getAssignedImplementor() == null ? "Approved" : "Assigned");
            ticket.setProgressCode(current.getLabel());
            if (current.getResolvedGroup() != null && ticket.getAssignedGroup() == null) {
                ticket.setAssignedGroup(current.getResolvedGroup());
            }
        } else if ("CONFIRMATION".equals(type)) {
            ticket.setStatusCode("Resolved");
            ticket.setProgressCode(current.getLabel());
        } else if ("CLOSURE".equals(type)) {
            ticket.setStatusCode("Closed");
        }
    }

    /**
     * Group an ASSIGN / REASSIGN may choose from. At a service-desk (ASSIGNMENT) step that is the
     * group of the next implementation (FULFILMENT) step, e.g. IT Implementors, because the desk
     * hands the ticket on; at a FULFILMENT step it is that step's own group (reassign a colleague).
     */
    public AssignmentGroup assigneePool(List<WorkflowInstanceStage> stages, WorkflowInstanceStage current) {
        if ("ASSIGNMENT".equals(current.getStageType())) {
            WorkflowInstanceStage next = nextPending(stages, current);
            while (next != null && !"FULFILMENT".equals(next.getStageType())) {
                next = nextPending(stages, next);
            }
            if (next != null && next.getResolvedGroup() != null) {
                return next.getResolvedGroup();
            }
        }
        return current.getResolvedGroup();
    }

    /**
     * Who may be chosen at this step. At the service desk (ASSIGNMENT): the implementors the administrator
     * assigned to the ticket's category (Admin &gt; Categories), or, when none are set, the members of the next
     * implementation group. At an implementation step (REASSIGN): that step's group. Active people only, no repeats.
     */
    public List<Employee> eligibleImplementors(Ticket ticket, List<WorkflowInstanceStage> stages, WorkflowInstanceStage current) {
        if ("ASSIGNMENT".equals(current.getStageType()) && ticket.getCategory() != null
                && !ticket.getCategory().getImplementorIds().isEmpty()) {
            List<Employee> list = new ArrayList<Employee>();
            for (Employee e : employeeRepository.findAllById(ticket.getCategory().getImplementorIds())) {
                if (e.isPortalActive()) {
                    list.add(e);
                }
            }
            if (!list.isEmpty()) {
                list.sort(java.util.Comparator.comparing(Employee::getDisplayName, String.CASE_INSENSITIVE_ORDER));
                return list;
            }
        }
        AssignmentGroup pool = assigneePool(stages, current);
        return pool == null ? new ArrayList<Employee>() : groupMembership.activeMembers(pool);
    }

    /** The chosen people, each once, all from {@code eligible}. */
    private List<Employee> resolveAssignees(List<Long> ids, List<Employee> eligible) {
        Set<Long> wanted = new java.util.LinkedHashSet<Long>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    wanted.add(id);
                }
            }
        }
        if (wanted.isEmpty()) {
            throw new ItsmException("ASSIGNEE_REQUIRED", "Select at least one implementor.");
        }
        java.util.Map<Long, Employee> byId = new java.util.LinkedHashMap<Long, Employee>();
        for (Employee e : eligible) {
            byId.put(e.getEmployeeId(), e);
        }
        List<Employee> out = new ArrayList<Employee>();
        for (Long id : wanted) {
            Employee e = byId.get(id);
            if (e == null) {
                Employee who = employeeRepository.findById(id).orElse(null);
                throw new ItsmException("ASSIGNEE_NOT_IN_GROUP", (who == null ? "The selected person" : who.getDisplayName())
                        + " is not an implementor for this ticket's category.");
            }
            out.add(e);
        }
        return out;
    }

    private TicketMatchContext contextOf(Ticket ticket) {
        String dept = ticket.getDepartment() != null ? ticket.getDepartment().getName() : null;
        return new TicketMatchContext(
                ticket.getTicketType().getName(),
                ticket.getTicketType().getCode(),
                ticket.getCategory().getName(),
                ticket.getSubCategory().getName(),
                ticket.getConfidentialityCode(),
                dept,
                ticket.getPriorityCode());
    }

    private int hopCap() {
        return settingRepository.findById("workflow.max-manager-hops")
                .map(s -> parseInt(s.getSettingValue(), DEFAULT_HOP_CAP))
                .orElse(DEFAULT_HOP_CAP);
    }

    private static boolean hasAuth(ItsmUserPrincipal principal, String code) {
        return principal.getAuthorities().stream().anyMatch(a -> code.equals(a.getAuthority()));
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String trim(String remarks) {
        return remarks == null ? "" : remarks.trim();
    }
}
