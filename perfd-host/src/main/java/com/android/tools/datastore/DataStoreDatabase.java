/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.datastore;

import com.android.tools.datastore.database.DatastoreTable;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DataStoreDatabase {

  private static Logger getLogger() {
    return Logger.getInstance(DataStoreDatabase.class);
  }

  private final Connection myConnection;

  public DataStoreDatabase(String database) {
    myConnection = initConnection(new File(database));
  }

  private Connection initConnection(File dbFile) {
    try {

      // For older versions of the JDBC we need to force load the sqlite.JDBC driver to trigger static initializer's and register
      // the JDBC driver with the java DriverMangaer.
      Class.forName("org.sqlite.JDBC");
      final Connection connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbFile.getPath()));

      // Performance optimization.
      // TODO: Create a timer and commit the database transaction every X seconds.
      connection.setAutoCommit(false);
      return connection;
    }
    catch (ClassNotFoundException | SQLException e) {
      getLogger().error(e);
    }
    return null;
  }

  public void disconnect() {
    try {
      myConnection.commit();
    }
    catch (SQLException e) {
      getLogger().error(e);
    }
    finally {
      try {
        myConnection.close();
      }
      catch (SQLException e) {
        getLogger().error(e);
      }
    }
  }

  public void registerTable(DatastoreTable table) {
    if (table != null) {
      table.initialize(myConnection);
      table.prepareStatements(myConnection);
    }
  }
}
