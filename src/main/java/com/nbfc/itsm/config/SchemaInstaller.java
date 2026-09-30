package com.nbfc.itsm.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Creates the ITSM schema on first start when Flyway is off (SQL Server 2012, where Flyway 9
 * Community refuses to run): if the SQL Server database has no {@code dbo.employee} table, runs
 * {@code db/install/install-itsm-portal.sql} (migrations V1-V9) in ONE transaction, so a failure
 * leaves the database empty and the next start retries. Runs before Hibernate validates the schema.
 * Does nothing when the tables exist, on non-SQL Server databases (H2 tests), or when Flyway is on.
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "false")
public class SchemaInstaller implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SchemaInstaller.class);

    static final String SCRIPT = "db/install/install-itsm-portal.sql";
    static final String UPGRADES = "classpath:db/install/upgrades/U*.sql";
    /** sqlcmd / SSMS batch separator: a line holding only GO. */
    private static final Pattern GO = Pattern.compile("(?im)^\\s*GO\\s*$");

    private final DataSource dataSource;

    public SchemaInstaller(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Hibernate's schema validation must wait until the tables exist. */
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnSchemaInstaller() {
        return new EntityManagerFactoryDependsOnPostProcessor("schemaInstaller");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Connection con = dataSource.getConnection();
        try {
            String product = con.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("sql server")) {
                return;
            }
            if (tablesExist(con)) {
                log.info("Schema check: ITSM tables present in database '{}'; no install needed.", con.getCatalog());
            } else {
                install(con);
            }
            upgrade(con);
        } finally {
            release(con);
        }
    }

    private static boolean tablesExist(Connection con) throws SQLException {
        Statement st = con.createStatement();
        try {
            ResultSet rs = st.executeQuery("SELECT CASE WHEN OBJECT_ID(N'dbo.employee', N'U') IS NULL THEN 0 ELSE 1 END");
            rs.next();
            return rs.getInt(1) == 1;
        } finally {
            st.close();
        }
    }

    private void install(Connection con) throws SQLException, IOException {
        String database = con.getCatalog();
        List<String> batches = batches(readScript());
        log.warn("Schema install: database '{}' has no ITSM tables; creating them now ({} batches from {}).",
                database, batches.size(), SCRIPT);
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        int n = 0;
        try {
            Statement st = con.createStatement();
            try {
                for (String batch : batches) {
                    n++;
                    st.execute(batch);
                    // Drain update counts / result sets so every statement of the batch runs.
                    while (st.getMoreResults() || st.getUpdateCount() != -1) {
                        // nothing to read
                    }
                }
            } finally {
                st.close();
            }
            con.commit();
            log.warn("Schema install: finished; all ITSM tables and master data created in database '{}'.", database);
        } catch (SQLException ex) {
            rollbackQuietly(con);
            log.error("Schema install FAILED at batch {} of {} in database '{}'; everything was rolled back, "
                    + "the database is unchanged. Fix the cause and restart. Error: {}", n, batches.size(), database,
                    ex.getMessage());
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    /**
     * Schema changes after the install script (V10+): each {@code db/install/upgrades/U*.sql} is idempotent
     * (checks before it adds) and runs at every start, in file-name order, each in its own transaction.
     */
    private void upgrade(Connection con) throws SQLException, IOException {
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources(UPGRADES);
        Arrays.sort(scripts, Comparator.comparing(Resource::getFilename));
        for (Resource r : scripts) {
            List<String> batches;
            InputStream in = r.getInputStream();
            try {
                batches = batches(StreamUtils.copyToString(in, StandardCharsets.UTF_8));
            } finally {
                in.close();
            }
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                Statement st = con.createStatement();
                try {
                    for (String batch : batches) {
                        st.execute(batch);
                        while (st.getMoreResults() || st.getUpdateCount() != -1) {
                            // drain
                        }
                    }
                } finally {
                    st.close();
                }
                con.commit();
                log.info("Schema upgrade {} checked/applied in database '{}'.", r.getFilename(), con.getCatalog());
            } catch (SQLException ex) {
                rollbackQuietly(con);
                log.error("Schema upgrade {} FAILED and was rolled back: {}", r.getFilename(), ex.getMessage());
                throw ex;
            } finally {
                con.setAutoCommit(autoCommit);
            }
        }
    }

    static String readScript() throws IOException {
        InputStream in = new ClassPathResource(SCRIPT).getInputStream();
        try {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    /** Splits on GO lines and drops batches that hold only whitespace. */
    static List<String> batches(String script) {
        List<String> out = new ArrayList<String>();
        for (String part : GO.split(script.replace("\r", ""))) {
            if (!part.trim().isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    /**
     * Resets the session options the script sets and removes the connection from the pool, so
     * nothing (e.g. SET NOCOUNT ON, which breaks Hibernate's optimistic locking) leaks into the app.
     */
    private void release(Connection con) {
        try {
            Statement st = con.createStatement();
            try {
                st.execute("SET NOEXEC OFF; SET NOCOUNT OFF;");
            } finally {
                st.close();
            }
        } catch (SQLException ignored) {
            // connection is evicted below anyway
        }
        try {
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).evictConnection(con);
            } else {
                con.close();
            }
        } catch (SQLException ignored) {
            // closing a broken connection
        }
    }

    private static void rollbackQuietly(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignored) {
            // original error is reported
        }
    }
}
