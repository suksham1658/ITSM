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
                          NotificationService notifications) {
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

        String action = actionCode == null ? "" : actionCode.trim().toUpperCase();
        WorkflowStageTransition transition = resolveTransition(current, action);
        if (transition == null) {
            throw new ItsmException("INVALID_TRANSITION",
                    "Action " + action + " is not allowed on stage " + current.getLabel() + ".");
        }
        boolean remarksNeeded = transition.isRemarksRequired()
                || "APPROVE".equals(action) || "REJECT".equals(action) || "SEND_BACK".equals(action);
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
        } else if ("ASSIGN".equals(action) || "REASSIGN".equals(action)) {
            AssignmentGroup pool = assigneePool(stages, current);
            Employee assignee = resolveAssignee(assigneeId, pool);
            ticket.setAssignedImplementor(assignee);
            if (pool != null) {
                ticket.setAssignedGroup(pool);
            }
            if ("FULFILMENT".equals(current.getStageType())) {
                // Hand the work to another implementor; the ticket stays on the implementation step.
                current.setResolvedEmployee(assignee);
                current.setActionCode(action);
                current.setRemarks(trim(remarks));
                current.setActedAtUtc(TimeUtc.now());
                ticket.setStatusCode("Assigned");
            } else {
                // Service desk / triage step is done: the chosen implementor owns the next step.
                complete(current, actor, action, remarks);
                WorkflowInstanceStage next = nextPending(stages, current);
                if (next != null && ("IMPLEMENTOR".equals(next.getActorStrategy())
                        || "FULFILMENT".equals(next.getStageType()))) {
                    next.setResolvedEmployee(assignee);
                }
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

    public void assertCanAct(ItsmUserPrincipal principal, Employee actor, Ticket ticket, WorkflowInstanceStage current) {
        String type = current.getStageType();
        if ("APPROVAL".equals(type) && !principal.getAuthorities().stream()
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

    public void requireRemarks(String remarks) {
        String trimmed = trim(remarks);
        int min = remarksMin();
        if (trimmed.length() < min) {
            throw new ItsmException("REMARKS_REQUIRED",
                    "Remarks are mandatory (at least " + min + " characters after trimming).");
        }
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

    private Employee resolveAssignee(Long assigneeId, AssignmentGroup group) {
        if (assigneeId == null) {
            throw new ItsmException("ASSIGNEE_REQUIRED", "Select an implementor.");
        }
        Employee assignee = employeeRepository.findById(assigneeId)
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Assignee not found."));
        if (!assignee.isPortalActive()) {
            throw new ItsmException("ASSIGNEE_INACTIVE", "Assignee is not portal-active.");
        }
        if (group != null && !groupMembership.isMember(group, assignee)) {
            throw new ItsmException("ASSIGNEE_NOT_IN_GROUP",
                    assignee.getDisplayName() + " is not a member of " + group.getName() + ".");
        }
        return assignee;
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
