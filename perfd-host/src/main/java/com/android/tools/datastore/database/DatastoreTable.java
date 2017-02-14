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

import com.android.tools.profiler.proto.Common;
import com.intellij.openapi.diagnostic.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Interface a {@link ServicePassThrough} object returns to indicate this object is
 * storing results in a database.
 */
public abstract class DatastoreTable<T extends Enum> {

  private ThreadLocal<Map<T, PreparedStatement>> myStatementMap = new ThreadLocal<>();
  private Connection myConnection;
  private static final Logger LOG = Logger.getInstance(DatastoreTable.class.getCanonicalName());

  /**
   * Initialization function to create tables for the Database.
   *
   * @param connection an open connection to the database.
   */
  public void initialize(Connection connection) {
    myConnection = connection;
  }

  private Map<T, PreparedStatement> getStatementMap() {
    if (myStatementMap.get() == null) {
      myStatementMap.set(new HashMap());
      prepareStatements(myConnection);
    }
    return myStatementMap.get();
  }

  /**
   * Helper function called after initialize to create {@link PreparedStatement} the implementor should cache
   * the statements for later use.
   *
   * @param connection an open connection to the database that should be used in creating the statements
   */
  public abstract void prepareStatements(Connection connection);

  protected void createTable(String table, String... columns) throws SQLException {
    myConnection.createStatement().execute(String.format("DROP TABLE IF EXISTS %s ", table));
    StringBuilder statement = new StringBuilder();
    statement.append(String.format("CREATE TABLE %s", table));
    executeUniqueStatement(statement, columns);
  }

  protected void createIndex(String table, String... indexList) throws SQLException {
    StringBuilder statement = new StringBuilder();
    statement.append(String.format("CREATE UNIQUE INDEX IF NOT EXISTS idx_%s_pk ON %s", table, table));
    executeUniqueStatement(statement, indexList);
  }

  private void executeUniqueStatement(StringBuilder statement, String[] params) throws SQLException {
    myConnection.createStatement().execute(String.format("%s ( %s )", statement, String.join(",", params)));
  }

  protected void createStatement(T statement, String stmt) throws SQLException {
    getStatementMap().put(statement, myConnection.prepareStatement(stmt));
  }

  protected void execute(T statement, Object... params) {
    try {
      PreparedStatement stmt = getStatementMap().get(statement);
      applyParams(stmt, params);
      stmt.execute();
    }
    catch (SQLException ex) {
      LOG.error(ex);
    }
  }

  protected ResultSet executeQuery(T statement, Object... params) throws SQLException {
    PreparedStatement stmt = getStatementMap().get(statement);
    applyParams(stmt, params);
    return stmt.executeQuery();
  }

  private void applyParams(PreparedStatement statement, Object... params) throws SQLException {
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
      else if (params[i] instanceof Common.Session) {
        statement.setString(i + 1, ((Common.Session)params[i]).toString());
      }
      else {
        //Not implemented type cast
        assert false;
      }
    }
  }
}
