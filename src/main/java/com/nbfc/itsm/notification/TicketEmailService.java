package com.nbfc.itsm.notification;

import com.nbfc.itsm.config.AsyncConfig;
import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
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

/**
 * E-mails the requester when their request / incident is submitted and when it is closed (nothing in
 * between). Runs after the ticket transaction commits, in the background ({@link AsyncConfig#MAIL_EXECUTOR}),
 * so a slow or unreachable SMTP server never delays or fails the user's action; failures are only logged.
 * SMTP: spring.mail.* (company relay 10.65.8.64:25, no login). On/off and sender: itsm.mail.*.
 */
@Service
public class TicketEmailService {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailService.class);
    private static final DateTimeFormatter IST = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final JavaMailSender mailSender;
    private final TicketRepository ticketRepository;
    private final ItsmProperties properties;

    public TicketEmailService(JavaMailSender mailSender, TicketRepository ticketRepository, ItsmProperties properties) {
        this.mailSender = mailSender;
        this.ticketRepository = ticketRepository;
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
            send(event.getTicketId(), event.getKind());
        } catch (Exception ex) {
            log.warn("Ticket e-mail {} could not be sent: {}", event, ex.toString());
        }
    }

    /** Builds and sends the mail; returns false when there is nobody to send to. */
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
        ItsmProperties.Mail cfg = properties.getMail();
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(cfg.getFrom(), cfg.getFromName());
        helper.setTo(to.trim());
        helper.setSubject(subject(t, kind));
        helper.setText(body(t, kind), true);
        mailSender.send(message);
        log.info("Ticket e-mail {} {} sent to {}", kind, t.getPublicNumber(), to);
        return true;
    }

    String subject(Ticket t, TicketEmailEvent.Kind kind) {
        String type = t.getTicketType() == null ? "Ticket" : t.getTicketType().getName();
        return kind == TicketEmailEvent.Kind.CREATED
                ? type + " " + t.getPublicNumber() + " created: " + t.getSubject()
                : type + " " + t.getPublicNumber() + " closed: " + t.getSubject();
    }

    String body(Ticket t, TicketEmailEvent.Kind kind) {
        String name = t.getRequester() == null ? "" : t.getRequester().getDisplayName();
        String type = t.getTicketType() == null ? "ticket" : t.getTicketType().getName();
        StringBuilder b = new StringBuilder();
        b.append("<div style=\"font-family:Segoe UI,Arial,sans-serif;font-size:14px;color:#1f2937;\">");
        b.append("<p>Dear ").append(esc(name)).append(",</p>");
        if (kind == TicketEmailEvent.Kind.CREATED) {
            b.append("<p>Your ").append(esc(type)).append(" has been created in the ITSM Portal and is now being processed.</p>");
        } else {
            b.append("<p>Your ").append(esc(type)).append(" has been <b>closed</b>.</p>");
        }
        b.append("<table cellpadding=\"6\" style=\"border-collapse:collapse;border:1px solid #e5e7eb;\">");
        row(b, "Ticket number", t.getPublicNumber());
        row(b, "Subject", t.getSubject());
        row(b, "Type", type);
        if (t.getCategory() != null) {
            row(b, "Category", t.getCategory().getName()
                    + (t.getSubCategory() == null ? "" : " / " + t.getSubCategory().getName()));
        }
        row(b, "Priority", t.getPriorityCode());
        row(b, "Status", t.getStatusCode());
        if (t.getCreatedAtUtc() != null) {
            row(b, "Created (IST)", IST.format(t.getCreatedAtUtc()));
        }
        b.append("</table>");
        String url = properties.getMail().getPortalUrl();
        if (StringUtils.hasText(url)) {
            String link = url.replaceAll("/+$", "") + "/tickets/" + t.getTicketId();
            b.append("<p><a href=\"").append(esc(link)).append("\">Open the ticket in the ITSM Portal</a></p>");
        }
        b.append("<p style=\"color:#6b7280;font-size:12px;\">This is an automatic message from the ITSM Portal. "
                + "Please do not reply to this e-mail.</p></div>");
        return b.toString();
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
