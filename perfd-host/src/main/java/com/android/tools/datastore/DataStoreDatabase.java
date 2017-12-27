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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DataStoreDatabase {
  public enum Characteristic {
    // TODO handle potential db file name clashes
    DURABLE,
    PERFORMANT
  }

  private static Logger getLogger() {
    return Logger.getInstance(DataStoreDatabase.class);
  }

  private final Connection myConnection;

  /**
   * @param dbPath the path to the backing DB file, if {@link Characteristic#DURABLE}.
   */
  public DataStoreDatabase(@NotNull String dbPath, @NotNull Characteristic characteristic) {
    Connection connection = null;
    try {
      // For older versions of the JDBC we need to force load the sqlite.JDBC driver to trigger static initializer's and register
      // the JDBC driver with the java DriverMangaer.
      Class.forName("org.sqlite.JDBC");

      switch (characteristic) {
        case PERFORMANT:
          connection = DriverManager.getConnection("jdbc:sqlite::memory:");
          break;
        case DURABLE:
          File dbFile = new File(dbPath);
          File parent = dbFile.getParentFile();
          if (parent != null) {
            if (!parent.mkdirs() && !parent.exists()) {
              getLogger().error("Unable to create parent directory");
            }
          }
          connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbFile.getPath()));
          break;
        default:
          throw new RuntimeException("Characteristic not handled!");
      }

      // Performance optimization.
      // TODO: Create a timer and commit the database transaction every X seconds.
      connection.setAutoCommit(false);
    }
    catch (ClassNotFoundException | SQLException e) {
      getLogger().error(e);
    }
    myConnection = connection;
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
        if (!myConnection.isClosed()) {
          myConnection.close();
        }
      }
      catch (SQLException e) {
        getLogger().error(e);
      }
    }
  }

  public Connection getConnection() {
    return myConnection;
  }
}
