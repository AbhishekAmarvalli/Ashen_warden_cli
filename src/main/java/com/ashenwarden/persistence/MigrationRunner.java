package com.ashenwarden.persistence;

import com.ashenwarden.common.PersistenceException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies versioned migrations from src/main/resources/db/migration in order,
 * tracked in a schema_version table. SQL files must not contain stored
 * procedures/DELIMITER blocks (statements are split on ';').
 * Adding a migration = add the file AND list it below (reviewed together).
 */
public final class MigrationRunner {
    private static final String[] MIGRATIONS = {
            "db/migration/V1__initial_schema.sql"
    };

    private MigrationRunner() {
    }

    public static int run(Connection connection) throws java.sql.SQLException {
        ensureVersionTable(connection);
        List<Integer> applied = appliedVersions(connection);
        int runCount = 0;
        for (int i = 0; i < MIGRATIONS.length; i++) {
            int version = i + 1;
            if (applied.contains(version)) {
                continue;
            }
            String resource = MIGRATIONS[i];
            String description = resource.substring(resource.indexOf("__") + 2, resource.length() - 4);
            String sql = readResource(resource);
            boolean previousAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    for (String stmt : splitStatements(sql)) {
                        statement.execute(stmt);
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version (version, description) VALUES (?, ?)")) {
                    insert.setInt(1, version);
                    insert.setString(2, description);
                    insert.executeUpdate();
                }
                connection.commit();
                runCount++;
            } catch (SQLException e) {
                rollbackQuietly(connection);
                throw new PersistenceException("Migration V" + version + " failed: " + e.getMessage(), e);
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // connection is being discarded anyway
                }
            }
        }
        return runCount;
    }

    private static void ensureVersionTable(Connection connection) throws PersistenceException {
        String ddl = "CREATE TABLE IF NOT EXISTS schema_version ("
                + "version INT PRIMARY KEY,"
                + "description VARCHAR(255) NOT NULL,"
                + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";
        try (Statement st = connection.createStatement()) {
            st.execute(ddl);
        } catch (SQLException e) {
            throw new PersistenceException("Could not create schema_version: " + e.getMessage(), e);
        }
    }

    private static List<Integer> appliedVersions(Connection connection) throws PersistenceException {
        List<Integer> versions = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version")) {
            while (rs.next()) {
                versions.add(rs.getInt(1));
            }
            return versions;
        } catch (SQLException e) {
            throw new PersistenceException("Could not read schema_version: " + e.getMessage(), e);
        }
    }

    private static String readResource(String path) throws PersistenceException {
        try (InputStream in = MigrationRunner.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new PersistenceException("Missing migration resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException("Could not read migration " + path + ": " +e.getMessage(), e);
        }
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : sql.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            current.append(rawLine).append('\n');
            if (line.endsWith(";")) {
                String stmt = current.toString().trim();
                if (stmt.endsWith(";")) {
                    stmt = stmt.substring(0, stmt.length() - 1);
                }
                if (!stmt.isBlank()) {
                    statements.add(stmt);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isBlank()) {
            statements.add(tail);
        }
        return statements;
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }
}
