package com.nbfc.itsm.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * SQL Server 2016 (compat 130) cannot use several 2017+ builtins.
 * LTRIM/RTRIM remain allowed; TRIM() is not.
 */
class SqlServer2016MigrationTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "STRING_AGG|CONCAT_WS|GENERATE_SERIES|CREATE\\s+OR\\s+ALTER|(?<![A-Z_])TRIM\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    @Test
    void flywayScriptsAvoidSql2017PlusBuiltins() throws IOException {
        Path dir = Paths.get("src/main/resources/db/migration");
        assertTrue(Files.isDirectory(dir), "migration directory missing");
        List<String> hits = new ArrayList<String>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".sql"))
                    .forEach(p -> scan(p, hits));
        }
        if (!hits.isEmpty()) {
            fail("SQL Server 2016-incompatible syntax:\n" + String.join("\n", hits));
        }
    }

    private static void scan(Path file, List<String> hits) {
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            hits.add(file + ": " + ex.getMessage());
            return;
        }
        Matcher m = FORBIDDEN.matcher(text);
        while (m.find()) {
            hits.add(file.getFileName() + " offset " + m.start() + ": " + m.group());
        }
    }
}
