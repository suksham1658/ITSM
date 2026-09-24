package com.nbfc.itsm.validation;

import com.nbfc.itsm.exception.ItsmException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Collects field errors so a form reports every problem at once, then throws a single
 * {@link ItsmException} ("VALIDATION"). The limits used with it live in {@link FieldLimits} and
 * mirror the database column sizes and CHECK constraints, so bad input is refused with a clear
 * message instead of failing in the database.
 */
public final class Validation {

    private final List<String> errors = new ArrayList<String>();

    /**
     * Trims {@code value} and checks its length. Blank optional values return {@code null};
     * blank required values record "{label} is required."
     */
    public String text(String value, String label, int min, int max, boolean required) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            if (required) {
                errors.add(label + " is required.");
            }
            return null;
        }
        if (v.length() < min) {
            errors.add(label + " must be at least " + min + " characters.");
        } else if (v.length() > max) {
            errors.add(label + " must be at most " + max + " characters (currently " + v.length() + ").");
        }
        if (containsControlChars(v)) {
            errors.add(label + " contains characters that are not allowed.");
        }
        return v;
    }

    /** Records an error unless {@code value} is one of {@code allowed} (exact match). */
    public String oneOf(String value, String label, Collection<String> allowed) {
        if (value == null || !allowed.contains(value)) {
            errors.add(label + " must be one of: " + String.join(", ", allowed) + ".");
            return null;
        }
        return value;
    }

    /** Records {@code message} when {@code value} is null. */
    public <T> T required(T value, String message) {
        if (value == null) {
            errors.add(message);
        }
        return value;
    }

    /** Records {@code message} when {@code condition} is false. */
    public void check(boolean condition, String message) {
        if (!condition) {
            errors.add(message);
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    /** Throws one exception listing every recorded problem. */
    public void throwIfInvalid() {
        throwIfInvalid("VALIDATION");
    }

    /** As {@link #throwIfInvalid()} with a specific error code. */
    public void throwIfInvalid(String code) {
        if (!errors.isEmpty()) {
            throw new ItsmException(code, message(errors));
        }
    }

    /** One user-facing sentence for a list of problems. */
    public static String message(List<String> problems) {
        return problems.size() == 1 ? problems.get(0)
                : "Please correct the following: " + String.join(" ", problems);
    }

    /** Tabs, newlines and carriage returns are allowed (multi-line text); other control chars are not. */
    private static boolean containsControlChars(String v) {
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }
        return false;
    }
}
