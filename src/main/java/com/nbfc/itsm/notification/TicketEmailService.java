package com.nbfc.itsm.notification;

import com.nbfc.itsm.config.AsyncConfig;
import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import javax.mail.internet.MimeMessage;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Ticket e-mails, sent after the ticket transaction commits, in the background
 * ({@link AsyncConfig#MAIL_EXECUTOR}), so a slow or unreachable SMTP server never delays or fails the
 * user's action; failures are only logged.
 * <ul>
 *   <li>CREATED / CLOSED: to the requester, when the request / incident is submitted and when it is closed.</li>
 *   <li>WAITING: "now in your queue" to every person who must act on the step that just became current
 *       (the approver, the group members, the role holders, or the requester for confirmation), never to the
 *       person who moved it.</li>
 * </ul>
 * SMTP: spring.mail.* (company relay 10.65.8.64:25, no login). On/off and sender: itsm.mail.*.
 */
@Service
public class TicketEmailService {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailService.class);
    private static final DateTimeFormatter IST = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final JavaMailSender mailSender;
    private final TicketRepository ticketRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkflowInstanceStageRepository stageRepository;
    private final ItsmProperties properties;

    public TicketEmailService(JavaMailSender mailSender, TicketRepository ticketRepository,
                              EmployeeRepository employeeRepository, WorkflowInstanceStageRepository stageRepository,
                              ItsmProperties properties) {
        this.mailSender = mailSender;
        this.ticketRepository = ticketRepository;
        this.employeeRepository = employeeRepository;
        this.stageRepository = stageRepository;
        this.properties = properties;
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void onTicketEvent(TicketEmailEvent event) {
        if (!properties.getMail().isEnabled()) {
            return;
        }
        try {
            if (event.getKind() == TicketEmailEvent.Kind.WAITING) {
                sendWaiting(event.getTicketId(), event.getStageId(), event.getRecipientIds());
            } else {
                send(event.getTicketId(), event.getKind());
            }
        } catch (Exception ex) {
            log.warn("Ticket e-mail {} could not be sent: {}", event, ex.toString());
        }
    }

    /** CREATED / CLOSED mail to the requester; returns false when there is nobody to send to. */
    @Transactional(readOnly = true)
    public boolean send(Long ticketId, TicketEmailEvent.Kind kind) throws Exception {
        Ticket t = ticketRepository.findById(ticketId).orElse(null);
        if (t == null) {
            return false;
        }
        Employee requester = t.getRequester();
        String to = requester == null ? null : requester.getEmail();
        if (!StringUtils.hasText(to)) {
            log.info("Ticket e-mail {} {} skipped: requester has no e-mail address", kind, t.getPublicNumber());
            return false;
        }
        mail(to, subject(t, kind), body(t, kind));
        log.info("Ticket e-mail {} {} sent to {}", kind, t.getPublicNumber(), to);
        return true;
    }

    /**
     * "Now in your queue" to each recipient (one mail each). Skips people without an e-mail address or
     * portal access; one failing address does not stop the others. Returns how many were sent.
     */
    @Transactional(readOnly = true)
    public int sendWaiting(Long ticketId, Long stageId, List<Long> recipientIds) {
        Ticket t = ticketRepository.findById(ticketId).orElse(null);
        WorkflowInstanceStage step = stageId == null ? null : stageRepository.findById(stageId).orElse(null);
        if (t == null || step == null) {
            return 0;
        }
        int sent = 0;
        for (Long id : recipientIds) {
            Employee e = employeeRepository.findById(id).orElse(null);
            if (e == null || !e.isPortalActive() || !StringUtils.hasText(e.getEmail())) {
                log.info("Queue e-mail {} skipped for employee {}: no e-mail address or portal access",
                        t.getPublicNumber(), e == null ? id : e.getEmployeeNo());
                continue;
            }
            try {
                mail(e.getEmail(), waitingSubject(t), waitingBody(t, step, e));
                sent++;
                log.info("Queue e-mail {} ({}) sent to {}", t.getPublicNumber(), step.getStageType(), e.getEmail());
            } catch (Exception ex) {
                log.warn("Queue e-mail {} to {} could not be sent: {}", t.getPublicNumber(), e.getEmail(), ex.toString());
            }
        }
        return sent;
    }

    // ------------------------------------------------------------------ content

    String subject(Ticket t, TicketEmailEvent.Kind kind) {
        return kind == TicketEmailEvent.Kind.CREATED
                ? typeName(t) + " " + t.getPublicNumber() + " created: " + t.getSubject()
                : typeName(t) + " " + t.getPublicNumber() + " closed: " + t.getSubject();
    }

    String body(Ticket t, TicketEmailEvent.Kind kind) {
        StringBuilder b = open(t.getRequester() == null ? "" : t.getRequester().getDisplayName());
        if (kind == TicketEmailEvent.Kind.CREATED) {
            b.append("<p>Your ").append(esc(typeName(t))).append(" has been created in the ITSM Portal and is now being processed.</p>");
        } else {
            b.append("<p>Your ").append(esc(typeName(t))).append(" has been <b>closed</b>.</p>");
        }
        details(b, t, false, null);
        return close(b, t);
    }

    String waitingSubject(Ticket t) {
        return "Action needed: " + typeName(t) + " " + t.getPublicNumber() + " is in your queue - " + t.getSubject();
    }

    String waitingBody(Ticket t, WorkflowInstanceStage step, Employee to) {
        StringBuilder b = open(to.getDisplayName());
        b.append("<p>").append(esc(typeName(t))).append(" <b>").append(esc(t.getPublicNumber()))
                .append("</b> is now in your queue. ").append(esc(whatToDo(step))).append("</p>");
        details(b, t, true, queueName(step));
        return close(b, t);
    }

    /** One sentence telling the recipient what is expected of them at this step. */
    static String whatToDo(WorkflowInstanceStage step) {
        String type = step.getStageType();
        if ("APPROVAL".equals(type)) {
            return "It is waiting for your approval (approve, reject or send back).";
        }
        if ("ASSIGNMENT".equals(type)) {
            return "It is in the " + groupName(step) + " queue; please assign it to an implementor.";
        }
        if ("FULFILMENT".equals(type)) {
            return step.getResolvedEmployee() != null
                    ? "It is assigned to you; please start work on it."
                    : "It is ready for an implementor in " + groupName(step) + ".";
        }
        if ("CONFIRMATION".equals(type)) {
            return "It has been resolved; please check and confirm it, or send it back.";
        }
        return "Please open it in the ITSM Portal.";
    }

    private static String queueName(WorkflowInstanceStage step) {
        String type = step.getStageType();
        if ("APPROVAL".equals(type)) {
            return "Approvals";
        }
        if ("ASSIGNMENT".equals(type)) {
            return groupName(step) + " (Ticket Queue)";
        }
        if ("FULFILMENT".equals(type)) {
            return step.getResolvedEmployee() != null ? "My Assigned Tickets" : groupName(step) + " (Work Queue)";
        }
        if ("CONFIRMATION".equals(type)) {
            return "My Tickets (confirmation)";
        }
        return step.getLabel();
    }

    private static String groupName(WorkflowInstanceStage step) {
        return step.getResolvedGroup() != null ? step.getResolvedGroup().getName() : "your team's";
    }

    private static String typeName(Ticket t) {
        return t.getTicketType() == null ? "Ticket" : t.getTicketType().getName();
    }

    private static StringBuilder open(String name) {
        StringBuilder b = new StringBuilder();
        b.append("<div style=\"font-family:Segoe UI,Arial,sans-serif;font-size:14px;color:#1f2937;\">");
        b.append("<p>Dear ").append(esc(name)).append(",</p>");
        return b;
    }

    private static void details(StringBuilder b, Ticket t, boolean withRequester, String queue) {
        b.append("<table cellpadding=\"6\" style=\"border-collapse:collapse;border:1px solid #e5e7eb;\">");
        row(b, "Ticket number", t.getPublicNumber());
        row(b, "Subject", t.getSubject());
        row(b, "Type", typeName(t));
        if (withRequester && t.getRequester() != null) {
            row(b, "Raised by", t.getRequester().getDisplayName());
        }
        if (t.getCategory() != null) {
            row(b, "Category", t.getCategory().getName()
                    + (t.getSubCategory() == null ? "" : " / " + t.getSubCategory().getName()));
        }
        row(b, "Priority", t.getPriorityCode());
        row(b, "Status", t.getStatusCode());
        if (queue != null) {
            row(b, "Your queue", queue);
        }
        if (t.getCreatedAtUtc() != null) {
            row(b, "Created (IST)", IST.format(t.getCreatedAtUtc()));
        }
        b.append("</table>");
    }

    private String close(StringBuilder b, Ticket t) {
        String url = properties.getMail().getPortalUrl();
        if (StringUtils.hasText(url)) {
            String link = url.replaceAll("/+$", "") + "/tickets/" + t.getTicketId();
            b.append("<p><a href=\"").append(esc(link)).append("\">Open the ticket in the ITSM Portal</a></p>");
        }
        b.append("<p style=\"color:#6b7280;font-size:12px;\">This is an automatic message from the ITSM Portal. "
                + "Please do not reply to this e-mail.</p></div>");
        return b.toString();
    }

    private void mail(String to, String subject, String html) throws Exception {
        ItsmProperties.Mail cfg = properties.getMail();
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(cfg.getFrom(), cfg.getFromName());
        helper.setTo(to.trim());
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }

    private static void row(StringBuilder b, String label, String value) {
        b.append("<tr><td style=\"border:1px solid #e5e7eb;background:#f9fafb;\"><b>").append(esc(label))
                .append("</b></td><td style=\"border:1px solid #e5e7eb;\">").append(esc(value == null ? "—" : value))
                .append("</td></tr>");
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
