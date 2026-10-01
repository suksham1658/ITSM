package com.nbfc.itsm.workflow;

import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import com.nbfc.itsm.util.TimeUtc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Every 15 minutes: resolved tickets that waited longer than the confirmation window (System Configuration,
 * default 48 hours) without an answer from the requester are closed, and the requester gets the "closed"
 * e-mail with a re-open link. Each ticket is closed in its own transaction; a failure is logged and retried
 * on the next run.
 */
@Component
public class ConfirmationAutoCloseJob {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationAutoCloseJob.class);

    private final WorkflowInstanceStageRepository stageRepository;
    private final WorkflowEngine workflowEngine;

    public ConfirmationAutoCloseJob(WorkflowInstanceStageRepository stageRepository, WorkflowEngine workflowEngine) {
        this.stageRepository = stageRepository;
        this.workflowEngine = workflowEngine;
    }

    @Scheduled(initialDelayString = "${itsm.jobs.auto-close-initial-delay-ms:120000}",
            fixedDelayString = "${itsm.jobs.auto-close-interval-ms:900000}")
    public void scheduled() {
        run(TimeUtc.now());
    }

    /** Closes every confirmation that is overdue at {@code now}; returns how many were closed. */
    public int run(Instant now) {
        List<Long> ids = new ArrayList<Long>();
        for (WorkflowInstanceStage s : stageRepository.findByStatusCodeAndStageType("Current", "CONFIRMATION")) {
            ids.add(s.getWorkflowInstanceStageId());
        }
        int closed = 0;
        for (Long id : ids) {
            try {
                if (workflowEngine.autoCloseIfDue(id, now)) {
                    closed++;
                }
            } catch (RuntimeException ex) {
                log.warn("Automatic closure of confirmation step {} failed: {}", id, ex.toString());
            }
        }
        if (closed > 0) {
            log.info("Closed {} resolved ticket(s) that were not confirmed in time", closed);
        }
        return closed;
    }
}
