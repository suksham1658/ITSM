package com.nbfc.itsm.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Field rules shared by the Java validation and the Thymeleaf forms (exposed to templates as
 * {@code limits}), so the browser and the server enforce the same limits. Maximums follow the
 * database column sizes in V1__core_tables.sql; allowed values follow its CHECK constraints.
 */
public final class FieldLimits {

    public static final int SUBJECT_MIN = 5;
    public static final int SUBJECT_MAX = 256;
    public static final int DESCRIPTION_MIN = 10;
    public static final int DESCRIPTION_MAX = 8000;
    public static final int LOCATION_MAX = 128;
    public static final int APPLICATION_MAX = 128;
    public static final int COMMENT_MIN = 2;
    public static final int COMMENT_MAX = 4000;
    public static final int REMARKS_MIN = 10;
    public static final int REMARKS_MAX = 2000;
    public static final int REJECT_REASON_MIN = 10;
    public static final int REJECT_REASON_MAX = 1000;
    public static final int RULE_NAME_MIN = 3;
    public static final int RULE_NAME_MAX = 128;
    public static final int RULE_JSON_MAX = 4000;
    public static final int SEARCH_MAX = 100;
    public static final int USERNAME_MAX = 256;
    public static final int PASSWORD_MAX = 256;

    public static final List<String> PRIORITIES =
            Collections.unmodifiableList(Arrays.asList("Critical", "High", "Medium", "Low"));
    public static final List<String> IMPACTS = Collections.unmodifiableList(
            Arrays.asList("Individual", "Department", "Multiple Departments", "Organization-wide"));
    public static final List<String> CONFIDENTIALITY =
            Collections.unmodifiableList(Arrays.asList("Normal", "Confidential", "Highly Confidential"));

    private static final FieldLimits INSTANCE = new FieldLimits();

    private FieldLimits() {
    }

    /** Bean-style access for templates: {@code ${limits.subjectMax}}. */
    public static FieldLimits get() {
        return INSTANCE;
    }

    public int getSubjectMin() { return SUBJECT_MIN; }
    public int getSubjectMax() { return SUBJECT_MAX; }
    public int getDescriptionMin() { return DESCRIPTION_MIN; }
    public int getDescriptionMax() { return DESCRIPTION_MAX; }
    public int getLocationMax() { return LOCATION_MAX; }
    public int getCommentMin() { return COMMENT_MIN; }
    public int getCommentMax() { return COMMENT_MAX; }
    public int getRemarksMin() { return REMARKS_MIN; }
    public int getRemarksMax() { return REMARKS_MAX; }
    public int getRejectReasonMin() { return REJECT_REASON_MIN; }
    public int getRejectReasonMax() { return REJECT_REASON_MAX; }
    public int getRuleNameMin() { return RULE_NAME_MIN; }
    public int getRuleNameMax() { return RULE_NAME_MAX; }
    public int getRuleJsonMax() { return RULE_JSON_MAX; }
    public int getSearchMax() { return SEARCH_MAX; }
    public int getUsernameMax() { return USERNAME_MAX; }
    public int getPasswordMax() { return PASSWORD_MAX; }
    public List<String> getPriorities() { return PRIORITIES; }
    public List<String> getImpacts() { return IMPACTS; }
    public List<String> getConfidentiality() { return CONFIDENTIALITY; }
}
