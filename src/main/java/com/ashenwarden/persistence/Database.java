package com.ashenwarden.persistence;

import com.ashenwarden.common.PersistenceException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC connection factory. Configuration comes from environment variables only:
 *   DB_URL    (default: safe localhost URL for database ashen_warden)
 *   DB_USER   (default: root)
 *   DB_PASSWORD (no default — never commit credentials; never logged)
 * A missing/unreachable MySQL shows friendly recovery guidance, not a stack trace.
 */
public final class Database {
    public static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/ashen_warden"
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                    + "&characterEncoding=utf-8&useUnicode=true&connectTimeout=3000";

    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static Database fromEnv() {
        String url = firstNonBlank(System.getenv("DB_URL"), DEFAULT_URL);
        String user = firstNonBlank(System.getenv("DB_USER"), "root");
        String password = System.getenv("DB_PASSWORD");
        if (password == null) {
            password = "";
        }
        return new Database(url, user, password);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public Connection open() throws PersistenceException {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new PersistenceException("Could not connect to MySQL: " + safeMessage(e), e);
        }
    }

    /** True when a connection can be opened right now. */
    public boolean isAvailable() {
        try (Connection c = open()) {
            return c.isValid(3);
        } catch (PersistenceException | SQLException e) {
            return false;
        }
    }

    /** Friendly guidance shown whenever persistence is unavailable. */
    public String recoveryGuidance() {
        return "Save features need the local MySQL service.\n"
                + "  - Start MySQL 8+ (e.g. 'net start MySQL80' on Windows)\n"
                + "  - Create the database: CREATE DATABASE ashen_warden CHARACTER SET utf8mb4;\n"
                + "  - Check DB_URL / DB_USER / DB_PASSWORD environment variables\n"
                + "  - Run the game once with the correct credentials; migrations apply automatically.\n"
                + "  The game itself keeps running — saving and loading are simply disabled for now.";
    }

    /** SQLState-aware message that never echoes the password. */
    private static String safeMessage(SQLException e) {
        String msg = e.getMessage() == null ? "unknown error" : e.getMessage();
        // strip anything after credentials just in case a driver includes them
        msg = msg.replaceAll("(?i)(password\\s*=\\s*)\\S+", "$1***");
        if (msg.toLowerCase(java.util.Locale.ROOT).contains("unknown database")) {
            return "database does not exist. Create it with: "
                    + "CREATE DATABASE ashen_warden CHARACTER SET utf8mb4; then restart the game.";
        }
        if (msg.contains("Access denied")) {
            return "access denied for the configured user. Check DB_USER / DB_PASSWORD.";
        }
        if (msg.contains("Communications link failure") || msg.contains("Connection refused")) {
            return "no MySQL server is listening on the configured host/port.";
        }
        return msg;
    }

    public String urlWithoutCredentials() {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }
}
