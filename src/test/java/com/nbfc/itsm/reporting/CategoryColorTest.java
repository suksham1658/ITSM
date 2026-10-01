package com.nbfc.itsm.reporting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Dashboard "Tickets by category" uses one fixed colour per category. */
class CategoryColorTest {

    @Test
    void requestedColours() {
        assertEquals("#F57C00", ReportingService.categoryColor("Application"));
        assertEquals("#9E9E9E", ReportingService.categoryColor("Cloud"));
        assertEquals("#00BCD4", ReportingService.categoryColor("Database"));
        assertEquals("#C41E3A", ReportingService.categoryColor("Email"));
        assertEquals("#111111", ReportingService.categoryColor("Hardware"));
        assertEquals("#2E7D32", ReportingService.categoryColor("Network"));
        assertEquals("#F3EFE4", ReportingService.categoryColor(" software "));
        assertEquals(ReportingService.COLOR_SLATE, ReportingService.categoryColor("Something new"));
    }
}
