package com.nbfc.itsm.notification;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Notification;
import com.nbfc.itsm.domain.NotificationRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.util.TimeUtc;
import com.nbfc.itsm.workflow.GroupMembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-app notifications for the ticket flow. Written in the same transaction as the workflow change,
 * so a notification exists exactly when the change it describes was saved. Nobody is notified about
 * their own action. Per-event on/off switches come from {@code notification_rule.in_app} when the
 * table is present; events without a rule are always on.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    static final String TICKET_CREATED = "TICKET_CREATED";
    static final String APPROVAL_REQUIRED = "APPROVAL_REQUIRED";
    static final String APPROVED = "APPROVED";
    static final String REJECTED = "REJECTED";
    static final String ASSIGNED = "ASSIGNED";
    static final String RESOLVED = "RESOLVED";

    private final NotificationRepository notificationRepository;
    private final GroupMembershipService groupMembership;
    private final EmployeeRoleAssignmentRepository roleAssignmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher events;

    public NotificationService(NotificationRepository notificationRepository,
                               GroupMembershipService groupMembership,
                               EmployeeRoleAssignmentRepository roleAssignmentRepository,
                               JdbcTemplate jdbcTemplate,
                               ApplicationEventPublisher events) {
        this.notificationRepository = notificationRepository;
        this.groupMembership = groupMembership;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.events = events;
    }

    // ------------------------------------------------------------------ events

    /** The requester submitted a ticket; tell them where it went. */
    @Transactional
    public void submitted(Ticket t, WorkflowInstanceStage first) {
        send(TICKET_CREATED, t.getRequester(), t, "Submitted: " + t.getPublicNumber(),
                "Your request \"" + t.getSubject() + "\" was submitted. " + nowWith(first));
        // E-mail to the requester, sent after commit (TicketEmailService).
        events.publishEvent(new TicketEmailEvent(t.getTicketId(), TicketEmailEvent.Kind.CREATED));
    }

    /**
     * A step became current: tell the people who must act on it (approver, group members, role
     * holders, or the requester for confirmation) in the app, including the person who just acted
     * when the next step is theirs too (e.g. the HOD who is also the CISO). The same people get a
     * "now in your queue" e-mail, except the requester, who is e-mailed only on created and closed.
     */
    @Transactional
    public void stepIsWaiting(Ticket t, WorkflowInstanceStage step) {
        if (step == null) {
            return;
        }
        String type = step.getStageType();
        String number = t.getPublicNumber();
        String who = "raised by " + t.getRequester().getDisplayName();
        List<Long> mailTo = new ArrayList<Long>();
        for (Employee e : actorsOf(t, step)) {
            if (!same(e, t.getRequester())) {
                mailTo.add(e.getEmployeeId());
            }
            if ("APPROVAL".equals(type)) {
                send(APPROVAL_REQUIRED, e, t, "Approval needed: " + number,
                        "\"" + t.getSubject() + "\" (" + who + ") is waiting for your approval.");
            } else if ("ASSIGNMENT".equals(type)) {
                send(APPROVAL_REQUIRED, e, t, "New in " + groupName(step) + ": " + number,
                        "\"" + t.getSubject() + "\" (" + who + ") is in the queue. Assign it to an implementor.");
            } else if ("FULFILMENT".equals(type)) {
                if (step.getResolvedEmployee() != null) {
                    send(ASSIGNED, e, t, "Assigned to you: " + number,
                            "\"" + t.getSubject() + "\" (" + who + ") is assigned to you. Start work when ready.");
                } else {
                    send(ASSIGNED, e, t, "New work in " + groupName(step) + ": " + number,
                            "\"" + t.getSubject() + "\" (" + who + ") is approved and ready for an implementor.");
                }
            } else if ("CONFIRMATION".equals(type)) {
                send(RESOLVED, e, t, "Resolved, please confirm: " + number,
                        "\"" + t.getSubject() + "\" was resolved. Open it to confirm it works, or send it back.");
            }
        }
        if (step.getWorkflowInstanceStageId() != null && !mailTo.isEmpty()) {
            // E-mail "now in your queue" to the same people (not the requester); sent after commit (TicketEmailService).
            events.publishEvent(new TicketEmailEvent(t.getTicketId(), TicketEmailEvent.Kind.WAITING,
                    step.getWorkflowInstanceStageId(), mailTo));
        }
    }

    @Transactional
    public void approved(Ticket t, Employee by, WorkflowInstanceStage next) {
        if (same(t.getRequester(), by)) {
            return;
        }
        send(APPROVED, t.getRequester(), t, "Approved by " + by.getDisplayName() + ": " + t.getPublicNumber(),
                "\"" + t.getSubject() + "\" was approved by " + by.getDisplayName() + ". " + nowWith(next));
    }

    @Transactional
    public void rejected(Ticket t, Employee by, String reason) {
        if (same(t.getRequester(), by)) {
            return;
        }
        send(REJECTED, t.getRequester(), t, "Declined by " + by.getDisplayName() + ": " + t.getPublicNumber(),
                "\"" + t.getSubject() + "\" was declined. Reason: " + safe(reason));
    }

    @Transactional
    public void sentBack(Ticket t, Employee by, String reason, WorkflowInstanceStage to) {
        if (!same(t.getRequester(), by)) {
            send(null, t.getRequester(), t, "Sent back by " + by.getDisplayName() + ": " + t.getPublicNumber(),
                    "\"" + t.getSubject() + "\" was sent back. Reason: " + safe(reason) + " " + nowWith(to));
        }
        stepIsWaiting(t, to);
    }

    @Transactional
    public void assigned(Ticket t, Employee by, Employee assignee) {
        if (!same(t.getRequester(), by)) {
            send(ASSIGNED, t.getRequester(), t, "Assigned: " + t.getPublicNumber(),
                    "\"" + t.getSubject() + "\" is assigned to " + assignee.getDisplayName() + ".");
        }
    }

    @Transactional
    public void statusUpdate(Ticket t, Employee by, String headline, String detail) {
        if (!same(t.getRequester(), by)) {
            send(null, t.getRequester(), t, headline + ": " + t.getPublicNumber(),
                    "\"" + t.getSubject() + "\" " + detail);
        }
    }

    @Transactional
    public void closed(Ticket t, Employee by) {
        // The e-mail goes out even when the requester closed it (confirmation), unlike the in-app notice.
        events.publishEvent(new TicketEmailEvent(t.getTicketId(), TicketEmailEvent.Kind.CLOSED));
        if (!same(t.getRequester(), by)) {
            send(null, t.getRequester(), t, "Closed: " + t.getPublicNumber(), "\"" + t.getSubject() + "\" is closed.");
        }
        Employee impl = t.getAssignedImplementor();
        if (impl != null && !same(impl, by)) {
            send(null, impl, t, "Closed: " + t.getPublicNumber(),
                    "\"" + t.getSubject() + "\" was confirmed and closed by " + by.getDisplayName() + ".");
        }
    }

    /** New comment: tell the requester and the assigned implementor (internal notes: implementor only). */
    @Transactional
    public void commented(Ticket t, Employee author, boolean internal) {
        String title = "New comment on " + t.getPublicNumber();
        String body = author.getDisplayName() + " commented on \"" + t.getSubject() + "\".";
        if (!internal && !same(t.getRequester(), author)) {
            send(null, t.getRequester(), t, title, body);
        }
        Employee impl = t.getAssignedImplementor();
        if (impl != null && !same(impl, author) && !same(impl, t.getRequester())) {
            send(null, impl, t, title, body);
        }
    }

    /** The owner of an AD account the Service Desk has just unlocked. */
    public void accountUnlocked(Employee owner, Employee by) {
        send(null, owner, null, "Your account was unlocked",
                "Your Windows / Active Directory account was unlocked by " + by.getDisplayName()
                        + ". If you did not ask for this, contact the IT Service Desk.");
    }

    // ------------------------------------------------------------------ helpers

    /** People who can act on {@code step}: named person, else group members, else role holders. */
    List<Employee> actorsOf(Ticket t, WorkflowInstanceStage step) {
        Map<Long, Employee> out = new LinkedHashMap<Long, Employee>();
        if (step.getResolvedEmployee() != null) {
            out.put(step.getResolvedEmployee().getEmployeeId(), step.getResolvedEmployee());
        } else if (step.getResolvedGroup() != null) {
            for (Employee e : groupMembership.activeMembers(step.getResolvedGroup())) {
                out.put(e.getEmployeeId(), e);
            }
        } else if (step.getResolvedRole() != null) {
            for (EmployeeRoleAssignment a : roleAssignmentRepository.findByRole(step.getResolvedRole())) {
                Employee e = a.getEmployee();
                if (e != null && e.isPortalActive()) {
                    out.put(e.getEmployeeId(), e);
                }
            }
        } else if ("CONFIRMATION".equals(step.getStageType())) {
            out.put(t.getRequester().getEmployeeId(), t.getRequester());
        }
        return new ArrayList<Employee>(out.values());
    }

    private String nowWith(WorkflowInstanceStage step) {
        if (step == null) {
            return "";
        }
        if (step.getResolvedEmployee() != null) {
            return "It is now with " + step.getResolvedEmployee().getDisplayName() + ".";
        }
        if (step.getResolvedGroup() != null) {
            return "It is now with " + step.getResolvedGroup().getName() + ".";
        }
        if (step.getResolvedRole() != null) {
            return "It is now with " + step.getResolvedRole().getName() + ".";
        }
        return "";
    }

    private static String groupName(WorkflowInstanceStage step) {
        return step.getResolvedGroup() != null ? step.getResolvedGroup().getName() : "your queue";
    }

    private void send(String eventCode, Employee to, Ticket t, String title, String body) {
        if (to == null || !to.isPortalActive() || (eventCode != null && !inAppEnabled(eventCode))) {
            return;
        }
        Notification n = new Notification();
        n.setRecipientId(to.getEmployeeId());
        n.setTicketId(t == null ? null : t.getTicketId());
        n.setTitle(cut(title, 256));
        n.setBody(cut(body, 1000));
        n.setRead(false);
        n.setCreatedAtUtc(TimeUtc.now());
        notificationRepository.save(n);
    }

    /** {@code notification_rule.in_app} for the event; missing table/row means enabled. */
    private boolean inAppEnabled(String eventCode) {
        try {
            List<Boolean> rows = jdbcTemplate.query(
                    "SELECT CASE WHEN in_app = 1 AND is_active = 1 THEN 1 ELSE 0 END FROM notification_rule WHERE event_code = ?",
                    (rs, i) -> rs.getInt(1) == 1, eventCode);
            return rows.isEmpty() || rows.get(0);
        } catch (RuntimeException ex) {
            log.debug("notification_rule not readable ({}); treating {} as enabled", ex.getClass().getSimpleName(), eventCode);
            return true;
        }
    }

    private static boolean same(Employee a, Employee b) {
        return a != null && b != null && a.getEmployeeId().equals(b.getEmployeeId());
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
