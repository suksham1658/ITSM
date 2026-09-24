package com.nbfc.itsm.web;

import com.nbfc.itsm.validation.FieldLimits;

/** Normalises free-text search boxes: trimmed, blank as null, capped at {@link FieldLimits#SEARCH_MAX}. */
final class SearchText {

    private SearchText() {
    }

    static String clean(String q) {
        if (q == null) {
            return null;
        }
        String t = q.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > FieldLimits.SEARCH_MAX ? t.substring(0, FieldLimits.SEARCH_MAX) : t;
    }
}
