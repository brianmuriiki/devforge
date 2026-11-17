package com.devforge.core.db;

import java.sql.Connection;
import java.sql.SQLException;

/** Source of database connections. The caller owns the connection and must close it. */
public interface ConnectionProvider {

    Connection open() throws SQLException;
}
