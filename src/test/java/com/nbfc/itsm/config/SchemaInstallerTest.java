package com.nbfc.itsm.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaInstallerTest {

    @Test
    void splitsOnGoLinesOnly() {
        List<String> b = SchemaInstaller.batches("SELECT 1;\r\nGO\r\n  go  \nSELECT 'GO here';\nGOTO x\nGO\n\n");
        assertEquals(2, b.size());
        assertTrue(b.get(1).contains("'GO here'") && b.get(1).contains("GOTO x"), "GO inside text / GOTO is not a separator");
    }

    @Test
    void bundledScriptHasEveryMigrationAndNoIsjsonOutsideDynamicSql() throws Exception {
        String script = SchemaInstaller.readScript();
        for (String v : new String[] {"V1__core_tables", "V2__", "V3__", "V4__", "V5__", "V7__", "V8__", "V9__"}) {
            assertTrue(script.contains(v), v + " included");
        }
        List<String> batches = SchemaInstaller.batches(script);
        assertTrue(batches.size() > 50, "split into batches");
        for (String batch : batches) {
            for (String line : batch.split("\n")) {
                if (line.contains("ISJSON(") && !line.contains("sp_executesql")) {
                    assertFalse(true, "ISJSON must only appear inside dynamic SQL (SQL Server 2012): " + line.trim());
                }
            }
        }
    }
}
