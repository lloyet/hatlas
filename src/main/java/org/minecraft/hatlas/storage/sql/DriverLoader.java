package org.minecraft.hatlas.storage.sql;

import java.sql.Driver;
import java.sql.SQLException;

/**
 * Instantiates a JDBC driver from the plugin classloader. Avoids {@code DriverManager},
 * which refuses drivers that are not visible from the caller's classloader.
 */
final class DriverLoader {

    private DriverLoader() {}

    static Driver load(String className) throws SQLException {
        try {
            Class<?> cls = Class.forName(className, true, DriverLoader.class.getClassLoader());
            return (Driver) cls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new SQLException("JDBC driver " + className + " is not available", e);
        }
    }
}
