package com.nbfc.itsm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * application.yml holds no passwords: they come from ITSM_CONFIG_DIR/itsm-secrets.yml (or env vars).
 * Only the configuration is loaded here; nothing connects to a database or LDAP.
 */
class ExternalSecretsConfigTest {

    @Configuration
    static class Empty {
    }

    @TempDir
    Path dir;

    @Test
    void secretsComeFromTheExternalFile() throws Exception {
        Files.write(dir.resolve("itsm-secrets.yml"), (
                "spring:\n  datasource:\n    username: app_user\n    password: 'S3cret!@x'\n"
                + "itsm:\n  ldap:\n    bind-password: 'Ld@p!pw'\n").getBytes(StandardCharsets.UTF_8));
        try (ConfigurableApplicationContext ctx = load(dir)) {
            Environment env = ctx.getEnvironment();
            assertEquals("app_user", env.getProperty("spring.datasource.username"));
            assertEquals("S3cret!@x", env.getProperty("spring.datasource.password"));
            assertEquals("Ld@p!pw", env.getProperty("itsm.ldap.bind-password"));
            assertEquals("S3cret!@x", env.getProperty("spring.flyway.password"), "Flyway reuses the datasource password");
        }
    }

    @Test
    void missingSecretsFailWithTheVariableName() {
        try (ConfigurableApplicationContext ctx = load(dir.resolve("no-such-folder"))) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ctx.getEnvironment().getProperty("spring.datasource.password"));
            assertTrue(ex.getMessage().contains("DB_PASSWORD"), ex.getMessage());
        }
    }

    @Test
    void applicationYmlHasNoLiteralPasswords() throws Exception {
        String yml = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/application.yml")),
                StandardCharsets.UTF_8);
        assertFalse(java.util.regex.Pattern.compile("(?m)^\\s+(password|bind-password):\\s*'").matcher(yml).find(),
                "no quoted literal password in application.yml");
        assertFalse(java.util.regex.Pattern.compile("\\$\\{(DB_PASSWORD|LDAP_BIND_PASSWORD):").matcher(yml).find(),
                "no default value after DB_PASSWORD / LDAP_BIND_PASSWORD");
        assertTrue(yml.contains("password: ${DB_PASSWORD}"));
        assertTrue(yml.contains("bind-password: ${LDAP_BIND_PASSWORD}"));
    }

    private static ConfigurableApplicationContext load(Path configDir) {
        return new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run("--ITSM_CONFIG_DIR=" + configDir.toString().replace('\\', '/'),
                        "--spring.main.banner-mode=off");
    }
}
