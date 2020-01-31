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

import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Interface a {@link com.android.tools.datastore.ServicePassThrough} object returns to indicate this object is
 * storing results in a database.
 */
public abstract class DataStoreTable<T extends Enum> {
  private static final Set<DataStoreTableErrorCallback> ERROR_CALLBACKS = ConcurrentHashMap.newKeySet();

  private Connection myConnection;
  private final ThreadLocal<Map<T, PreparedStatement>> myStatementMap = new ThreadLocal<>();

  // Cache custom queries we have a limited number and we call the same query multiple times.
  private final ThreadLocal<Map<String, PreparedStatement>> myCustomQueryCache = new ThreadLocal<>();

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
   * A connection represents a link between code and the database layer. This link is accessed via multiple threads
   * as such means the only guarantee this function offers is the state of the connection at the time of the call.
   * If the connection is closed at any time this function will always return false since we never attempt to reestablish.
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
   *
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
    statement.append(String.format(Locale.US, "CREATE INDEX IF NOT EXISTS idx_%s_%d_pk ON %s", table, indexId, table));
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

  /**
   * Executes a bulk operation on the table. This is an optimization when inserting / deleting multiple items from
   * the database.
   * @param statement which statement to execute
   * @param batchParams a list of objects to be put into the database.
   * @param paramConverter a callback that converts each object to an array of data. The array of data will be applied to the input params
   *                       of the specified statement.
   */
  protected <K> void executeBatch(@NotNull T statement, @NotNull List<K> batchParams, @NotNull Function<K, Object[]> paramConverter) {
    if (isClosed()) {
      return;
    }
    try {
      PreparedStatement stmt = getStatementMap().get(statement);
      batchParams.forEach((object) -> {
        try {
          applyParams(stmt, paramConverter.apply(object));
          stmt.addBatch();
        } catch (SQLException ex) {
          onError(ex);
        }
      });
      int[] results = stmt.executeBatch();
      for(int i = 0; i < results.length; i++) {
        if (results[i] == Statement.EXECUTE_FAILED) {
          throw new SQLException(String.format("Failed to insert batch element %d with result %d", i, results[i]));
        }
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  protected void execute(@NotNull T statement, Object... params) {
    if (isClosed()) {
      return;
    }
    try {
      PreparedStatement stmt = getStatementMap().get(statement);
      applyParams(stmt, params);
      stmt.execute();
      // Clear parameters on exit so cached statements don't keep potentially large objects in memory.
      // Example: Inserting a payload into the database.
      stmt.clearParameters();
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  protected ResultSet executeQuery(@NotNull T statement, Object... params) throws SQLException {
    if (isClosed()) {
      return new EmptyResultSet();
    }
    PreparedStatement stmt = getStatementMap().get(statement);
    applyParams(stmt, params);
    return stmt.executeQuery();
  }

  protected ResultSet executeOneTimeQuery(@NotNull String sql, Object[] params) throws SQLException {
    if (isClosed()) {
      return new EmptyResultSet();
    }
    if (myCustomQueryCache.get() == null) {
      myCustomQueryCache.set(new HashMap<>());
    }

    Map<String, PreparedStatement> queryCache = myCustomQueryCache.get();
    if (!queryCache.containsKey(sql)) {
      queryCache.put(sql, myConnection.prepareStatement(sql));
    }

    PreparedStatement statement = queryCache.get(sql);
    applyParams(statement, params);
    return statement.executeQuery();
  }

  protected void applyParams(@NotNull PreparedStatement statement, Object... params) throws SQLException {
    for (int i = 0; params != null && i < params.length; i++) {
      if (params[i] == null) {
        continue;
      }
      else if (params[i] instanceof String) {
        statement.setString(i + 1, (String)params[i]);
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
      else if (params[i] instanceof Boolean) {
        statement.setBoolean(i + 1, (boolean)params[i]);
      }
      else {
        //Not implemented type cast
        assert false : "No DataStoreTable support for arguments of type: " + params[i].getClass();
      }
    }
  }
}
