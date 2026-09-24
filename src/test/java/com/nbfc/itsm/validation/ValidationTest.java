package com.nbfc.itsm.validation;

import com.nbfc.itsm.exception.ItsmException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTest {

    @Test
    void textIsTrimmedAndLengthChecked() {
        Validation v = new Validation();
        assertEquals("Laptop broken", v.text("  Laptop broken  ", "Subject", 5, 20, true));
        assertFalse(v.hasErrors());

        assertNull(v.text("   ", "Subject", 5, 20, true));
        v.text("abc", "Subject", 5, 20, true);
        v.text(new String(new char[21]).replace('\0', 'x'), "Subject", 5, 20, true);
        assertEquals(3, v.errors().size());
        assertEquals("Subject is required.", v.errors().get(0));
        assertTrue(v.errors().get(1).contains("at least 5"));
        assertTrue(v.errors().get(2).contains("at most 20"));
    }

    @Test
    void optionalBlankIsNullAndControlCharactersAreRejected() {
        Validation v = new Validation();
        assertNull(v.text("  ", "Location", 0, 10, false));
        assertFalse(v.hasErrors());
        v.text("bad\u0000text", "Location", 0, 20, false);
        assertTrue(v.errors().get(0).contains("not allowed"));
        Validation ok = new Validation();
        ok.text("line one\nline two\ttab", "Description", 0, 100, true);
        assertFalse(ok.hasErrors(), "newlines and tabs are fine in multi-line text");
    }

    @Test
    void oneOfAndCombinedMessage() {
        Validation v = new Validation();
        assertEquals("High", v.oneOf("High", "Priority", FieldLimits.PRIORITIES));
        v.oneOf("Urgent!!", "Priority", FieldLimits.PRIORITIES);
        v.required(null, "Select a category.");
        ItsmException ex = assertThrows(ItsmException.class, () -> v.throwIfInvalid("TICKET_INVALID"));
        assertEquals("TICKET_INVALID", ex.getCode());
        assertTrue(ex.getMessage().startsWith("Please correct the following:"));
        assertTrue(ex.getMessage().contains("Priority must be one of: Critical, High, Medium, Low."));
        assertTrue(ex.getMessage().contains("Select a category."));
    }
}
