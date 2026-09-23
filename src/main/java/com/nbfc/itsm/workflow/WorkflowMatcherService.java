package com.nbfc.itsm.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.domain.WorkflowDefinition;
import com.nbfc.itsm.domain.WorkflowRule;
import com.nbfc.itsm.domain.WorkflowRuleRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.ticket.TicketMatchContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * First Active rule by priority ASC whose condition_json matches. Fail closed if none.
 * Matching is data-driven — no ticket-type if/else.
 */
@Service
public class WorkflowMatcherService {

    private final WorkflowRuleRepository workflowRuleRepository;
    private final ObjectMapper objectMapper;

    public WorkflowMatcherService(WorkflowRuleRepository workflowRuleRepository, ObjectMapper objectMapper) {
        this.workflowRuleRepository = workflowRuleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WorkflowRule match(TicketMatchContext ctx) {
        List<WorkflowRule> rules = workflowRuleRepository.findByStatusCodeOrderByPriorityAsc("Active");
        for (WorkflowRule rule : rules) {
            if (matches(rule.getConditionJson(), ctx)) {
                WorkflowDefinition def = rule.getWorkflowDefinition();
                if (def == null || !"Active".equals(def.getStatusCode())) {
                    continue;
                }
                return rule;
            }
        }
        throw new ItsmException("WORKFLOW_NO_MATCH",
                "No active workflow rule matches this request. Submit is blocked (fail closed).");
    }

    boolean matches(String conditionJson, TicketMatchContext ctx) {
        if (!StringUtils.hasText(conditionJson) || "{}".equals(conditionJson.trim())) {
            return true;
        }
        Map<String, Object> map;
        try {
            map = objectMapper.readValue(conditionJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return false;
        }
        if (map == null || map.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            List<String> wanted = asStringList(entry.getValue());
            if (wanted.isEmpty()) {
                continue;
            }
            String actual = ctx.value(entry.getKey());
            if (!containsIgnoreCase(wanted, actual)) {
                if ("ticket_type".equals(entry.getKey()) && containsIgnoreCase(wanted, ctx.getTicketTypeCode())) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    private static boolean containsIgnoreCase(List<String> wanted, String actual) {
        if (actual == null) {
            return false;
        }
        for (String w : wanted) {
            if (w != null && w.equalsIgnoreCase(actual)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            List<String> out = new ArrayList<String>();
            for (Object o : (List<Object>) value) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        return Collections.singletonList(String.valueOf(value));
    }
}
