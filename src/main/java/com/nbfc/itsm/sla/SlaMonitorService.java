package com.nbfc.itsm.sla;

import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.SystemSettingRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.domain.TicketSlaRepository;
import com.nbfc.itsm.domain.WorkflowInstance;
import com.nbfc.itsm.domain.WorkflowInstanceRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import com.nbfc.itsm.notification.NotificationService;
import com.nbfc.itsm.util.TimeUtc;
import com.nbfc.itsm.workflow.GroupMembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every 5 minutes: brings every open SLA clock up to date and saves it (so Escalations, the dashboard and
 * reports are always current), flags late first responses, and alerts once per level:
 * <ul>
 *   <li>NEAR (at risk): the people on the current step and the assigned implementor;</li>
 *   <li>BREACHED: the same people plus the IT Service Desk.</li>
 * </ul>
 * Alerts can be switched off in System Configuration (sla.alerts). Closed, rejected and draft tickets are ignored.
 */
@Service
public class SlaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorService.class);
    private static final List<String> FINISHED = Arrays.asList("Closed", "Rejected", "Draft");

    private final TicketSlaRepository slaRepository;
    private final SlaService slaService;
    private final NotificationService notifications;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowInstanceStageRepository stageRepository;
    private final AssignmentGroupRepository groupRepository;
    private final GroupMembershipService groupMembership;
    private final SystemSettingRepository settingRepository;
    private final TransactionTemplate tx;

    public SlaMonitorService(TicketSlaRepository slaRepository, SlaService slaService, NotificationService notifications,
                             WorkflowInstanceRepository instanceRepository, WorkflowInstanceStageRepository stageRepository,
                             AssignmentGroupRepository groupRepository, GroupMembershipService groupMembership,
                             SystemSettingRepository settingRepository, TransactionTemplate tx) {
        this.slaRepository = slaRepository;
        this.slaService = slaService;
        this.notifications = notifications;
        this.instanceRepository = instanceRepository;
        this.stageRepository = stageRepository;
        this.groupRepository = groupRepository;
        this.groupMembership = groupMembership;
        this.settingRepository = settingRepository;
        this.tx = tx;
    }

    @Scheduled(initialDelayString = "${itsm.jobs.sla-initial-delay-ms:90000}",
            fixedDelayString = "${itsm.jobs.sla-interval-ms:300000}")
    public void scheduled() {
        run(TimeUtc.now());
    }

    /** Checks every open clock at {@code now}; returns how many alerts were raised. */
    public int run(Instant now) {
        List<Long> ids = new ArrayList<Long>();
        for (TicketSla s : slaRepository.findByResolvedUtcIsNull()) {
            ids.add(s.getTicketSlaId());
        }
        int alerts = 0;
        for (Long id : ids) {
            try {
                Boolean alerted = tx.execute(status -> check(id, now));
                if (Boolean.TRUE.equals(alerted)) {
                    alerts++;
                }
            } catch (RuntimeException ex) {
                log.warn("SLA check of clock {} failed: {}", id, ex.toString());
            }
        }
        return alerts;
    }

    /** One clock, in its own transaction. True when an alert was raised. */
    @Transactional
    public boolean check(Long ticketSlaId, Instant now) {
        TicketSla sla = slaRepository.findById(ticketSlaId).orElse(null);
        if (sla == null || sla.getResolvedUtc() != null || sla.getTicket() == null
                || FINISHED.contains(sla.getTicket().getStatusCode())) {
            return false;
        }
        slaService.refreshState(sla, now);
        boolean alerted = false;
        boolean breached = "BREACHED".equals(sla.getStateCode());
        boolean near = "NEAR".equals(sla.getStateCode());
        if (breached && sla.getBreachAlertedUtc() == null) {
            sla.setBreachAlertedUtc(now);
            alerted = alert(sla, true);
        } else if (near && sla.getNearAlertedUtc() == null && sla.getBreachAlertedUtc() == null) {
            sla.setNearAlertedUtc(now);
            alerted = alert(sla, false);
        }
        slaRepository.save(sla);
        return alerted;
    }

    private boolean alert(TicketSla sla, boolean breached) {
        if (!alertsOn()) {
            return false;
        }
        Ticket t = sla.getTicket();
        List<Employee> to = new ArrayList<Employee>();
        WorkflowInstance instance = instanceRepository.findByTicketId(t.getTicketId()).orElse(null);
        if (instance != null) {
            for (WorkflowInstanceStage s : stageRepository.findByWorkflowInstanceOrderByStageOrderAsc(instance)) {
                if ("Current".equals(s.getStatusCode())) {
                    to.addAll(notifications.actorsOf(t, s));
                }
            }
        }
        if (t.getAssignedImplementor() != null) {
            to.add(t.getAssignedImplementor());
        }
        if (breached) {
            groupRepository.findByCode("IT_SERVICE_DESK").ifPresent(g -> to.addAll(groupMembership.activeMembers(g)));
        }
        notifications.slaAlert(t, breached, to, sla.getResolveDueUtc());
        log.info("SLA {} alert for {}", breached ? "BREACHED" : "NEAR", t.getPublicNumber());
        return true;
    }

    private boolean alertsOn() {
        return settingRepository.findById("sla.alerts").map(s -> !"false".equalsIgnoreCase(s.getSettingValue())).orElse(true);
    }
}
