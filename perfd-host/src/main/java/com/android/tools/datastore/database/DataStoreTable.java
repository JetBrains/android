/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.datastore.database;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Interface a {@link com.android.tools.datastore.ServicePassThrough} object returns to indicate this object is
 * storing results in a database.
 */
public abstract class DataStoreTable<T extends Enum> {
  private static final Logger LOG = Logger.getInstance(DataStoreTable.class.getCanonicalName());
  private static final Set<DataStoreTableErrorCallback> ERROR_CALLBACKS = new HashSet();

  private Connection myConnection;
  private final ThreadLocal<Map<T, PreparedStatement>> myStatementMap = new ThreadLocal<>();

  public interface DataStoreTableErrorCallback {
    void onDataStoreError(Throwable t);
  }

  /**
   * Initialization function to create tables for the Database.
   *
   * @param connection an open connection to the database.
   */
  public void initialize(@NotNull Connection connection) {
    myConnection = connection;
  }

  /**
   * Helper function called after initialize to create {@link PreparedStatement} the implementor should cache
   * the statements for later use.
   */
  public abstract void prepareStatements();

  public static void addDataStoreErrorCallback(@NotNull DataStoreTableErrorCallback callback) {
    ERROR_CALLBACKS.add(callback);
  }

  public static void removeDataStoreErrorCallback(@NotNull DataStoreTableErrorCallback callback) {
    ERROR_CALLBACKS.remove(callback);
  }

  /**
   * @return true if the underlying connection is closed, false otherwise.
   */
  public boolean isClosed() {
    try {
      return myConnection.isClosed();
    }
    catch (SQLException ex) {
      return true;
    }
  }
  /**
   * Error handling is handled in the callbacks. One of the callbacks is
   * expected in the {@link DataStoreService}. One of the callbacks will log all errors
   * without being elided.
   * @param t A throwable object that contains information about the error encountered.
   */
  protected static void onError(Throwable t) {
    for (DataStoreTableErrorCallback callback : ERROR_CALLBACKS) {
      callback.onDataStoreError(t);
    }
  }

  @NotNull
  protected Map<T, PreparedStatement> getStatementMap() {
    if (myStatementMap.get() == null) {
      myStatementMap.set(new HashMap<>());
      prepareStatements();
    }
    return myStatementMap.get();
  }

  protected void createTable(@NotNull String table, String... columns) throws SQLException {
    myConnection.createStatement().execute(String.format("DROP TABLE IF EXISTS %s ", table));
    StringBuilder statement = new StringBuilder();
    statement.append(String.format("CREATE TABLE %s", table));
    executeUniqueStatement(statement, columns);
  }

  protected void createUniqueIndex(@NotNull String table, String... indexList) throws SQLException {
    StringBuilder statement = new StringBuilder();
    statement.append(String.format("CREATE UNIQUE INDEX IF NOT EXISTS idx_%s_pk ON %s", table, table));
    executeUniqueStatement(statement, indexList);
  }

  protected void createIndex(@NotNull String table, int indexId, String... indexList) throws SQLException {
    StringBuilder statement = new StringBuilder();
    statement.append(String.format("CREATE INDEX IF NOT EXISTS idx_%s_%d_pk ON %s", table, indexId, table));
    executeUniqueStatement(statement, indexList);
  }

  private void executeUniqueStatement(@NotNull StringBuilder statement, @NotNull String[] params) throws SQLException {
    myConnection.createStatement().execute(String.format("%s ( %s )", statement, String.join(",", params)));
  }

  protected void createStatement(@NotNull T statement, @NotNull String stmt) throws SQLException {
    getStatementMap().put(statement, myConnection.prepareStatement(stmt));
  }

  protected void createStatement(@NotNull T statement, @NotNull String stmt, int statementFlags) throws SQLException {
    getStatementMap().put(statement, myConnection.prepareStatement(stmt, statementFlags));
  }

  protected void execute(@NotNull T statement, Object... params) {
    try {
      if (isClosed()) {
        return;
      }
      PreparedStatement stmt = getStatementMap().get(statement);
      applyParams(stmt, params);
      stmt.execute();
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  protected ResultSet executeQuery(@NotNull T statement, Object... params) throws SQLException {
    PreparedStatement stmt = getStatementMap().get(statement);
    if (isClosed() || stmt.isClosed()) {
      return new EmptyResultSet();
    }
    applyParams(stmt, params);
    return stmt.executeQuery();
  }

  protected void applyParams(@NotNull PreparedStatement statement, Object... params) throws SQLException {
    for (int i = 0; params != null && i < params.length; i++) {
      if (params[i] == null) {
        continue;
      }
      else if (params[i] instanceof String) {
        statement.setString(i + 1, (String)params[i]);
      }
      else if (params[i] instanceof Boolean) {
        statement.setBoolean(i + 1, (boolean)params[i]);
      }
      else if (params[i] instanceof Integer) {
        statement.setLong(i + 1, (int)params[i]);
      }
      else if (params[i] instanceof Long) {
        statement.setLong(i + 1, (long)params[i]);
      }
      else if (params[i] instanceof byte[]) {
        statement.setBytes(i + 1, (byte[])params[i]);
      }
      else {
        //Not implemented type cast
        assert false;
      }
    }
  }
}
