/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class takes {@link PerfettoTrace.FtraceEventBundle} converts it to an Atrace line then adds that line to a database.
 * This is done because the bundles come in out of order, as well as the lines within a bundle are out of order. A clean, efficient way to
 * sort these lines is to use a database.
 * The class is then reset for iteration after all bundles have been added and the iterator returns individual lines.
 * For a 30 second capture 1gb memory for studio didn't OOM and took 235975ms to parse (including trebuchet time), 11596ms in trebuchet.
 * A 5 second capture took 7377ms for parse including trebuchet (2353ms).
 */
public class PerfettoPacketDBSorter implements Iterator<String> {
  private Connection myConnection;
  private PreparedStatement myInsertStmt;
  private ResultSet myQueryResults;
  private boolean myLastResult = false;
  private boolean myReturnedCurrentResults = true;

  private static Logger getLogger() {
    return Logger.getInstance(PerfettoPacketDBSorter.class);
  }

  public PerfettoPacketDBSorter() {
    try {
      File dbFile = FileUtil.createTempFile("perfetto", ".db", true);
      myConnection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbFile.getPath()));
      myConnection.setAutoCommit(false);

      try (Statement stmt = myConnection.createStatement()) {
        stmt.execute("CREATE TABLE Events (Timestamp INTEGER, Line TEXT);");
      }
      myInsertStmt = myConnection.prepareStatement("INSERT INTO Events (Timestamp, Line) VALUES (?, ?)");
    }
    catch (IOException | SQLException e) {
      getLogger().error(e);
    }
  }

  /**
   * As a trace file is loaded each FtraceEventBundle should be added to the sorter. The sorter will then
   * parse each event and call line formatter on each event.
   */
  public void addLine(long timestamp, @NotNull String line) {
    // If our connection somehow closed in the middle of a capture return instead of spamming the output.
    try {
      if (myConnection.isClosed()) {
        return;
      }
      myInsertStmt.setLong(1, timestamp);
      myInsertStmt.setString(2, line);
      myInsertStmt.execute();
    }
    catch (SQLException ex) {
      getLogger().warn(ex);
    }
  }

  /**
   * This function should be called when we want to finalize this class for writing and enable it for reading.
   * A ResultSet is created and the database is queried for all events.
   */
  public void resetForIterator() {
    try {
      myQueryResults = myConnection.createStatement().executeQuery("SELECT Line FROM Events ORDER BY Timestamp asc");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  /**
   * Close the underlying connection to the local DB and free resources.
   */
  public void close() {
    // We need to close in the reverse order we opened them: rs -> stmt -> conn
    try {
      myQueryResults.close();
    }
    catch (SQLException ignored) { }
    finally { myQueryResults = null; }

    try {
      myInsertStmt.close();
    }
    catch (SQLException ignored) { }
    finally { myInsertStmt = null; }

    try {
      myConnection.close();
    }
    catch (SQLException ignored) { }
    finally { myConnection = null; }

    // Reset to the initial state.
    myLastResult = false;
    myReturnedCurrentResults = true;

  }

  @Override
  public boolean hasNext() {
    try {
      // Next moves the cursor to the next row, and ResultSet has no hasNext function.
      if (myReturnedCurrentResults && myQueryResults != null) {
        myLastResult = myQueryResults.next();
        myReturnedCurrentResults = false;
      }
      return myLastResult;
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return false;
  }

  @Override
  @Nullable
  public String next() {
    try {
      myReturnedCurrentResults = true;
      // ResultSet is 1 based so we get the first index.
      return myQueryResults.getString(1);
    }
    catch (Exception ex) {
      getLogger().error(ex);
    }
    return null;
  }
}
