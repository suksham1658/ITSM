package com.nbfc.itsm.web;

import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * UI smoke without screenshots: the Thymeleaf templates used on the main
 * authenticated surfaces must be packaged. Layout/viewport live in fragments/head.
 */
class TemplatePresenceTest {

    @Test
    void keyTemplatesAreOnTheClasspath() {
        assertTemplate("templates/login.html");
        assertTemplate("templates/dashboard.html");
        assertTemplate("templates/reports.html");
        assertTemplate("templates/reports/detail.html");
        assertTemplate("templates/tickets/raise.html");
        assertTemplate("templates/tickets/list.html");
        assertTemplate("templates/tickets/detail.html");
        assertTemplate("templates/fragments/head.html");
        assertTemplate("templates/fragments/header.html");
        assertTemplate("templates/fragments/sidebar.html");
        assertTemplate("templates/admin/users.html");
        assertTemplate("templates/error.html");
    }

    private static void assertTemplate(String path) {
        URL url = TemplatePresenceTest.class.getClassLoader().getResource(path);
        assertNotNull(url, "missing template " + path);
    }
}
