/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.datastore.database.DataStoreTable;
import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.profiler.proto.Common;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages a dedicated database for a profiler task. At most one such database can be active at a time.
 */
public class TaskDatabaseManager {
  /**
   * For an imported trace, maps the fake session IDs to the real ones for query translation.
   */
  public record ImportedSessionMapping(long realStreamId, int realPid) {
  }

  public static final String METADATA_KEY_ORIGINAL_STREAM_ID = "original_stream_id";
  public static final String METADATA_KEY_ORIGINAL_PID = "original_pid";
  public static final String METADATA_KEY_TASK_TYPE = "task_type";
  public static final String METADATA_KEY_STUDIO_VERSION = "studio_version";
  public static final String METADATA_KEY_CREATED_AT = "created_at";

  @NotNull private final LogService myLogService;
  @NotNull private final Consumer<Throwable> myNoPiiExceptionHandler;
  @NotNull private final DataStoreTable.DataStoreTableErrorCallback myErrorCallback;

  @Nullable private DataStoreDatabase myTaskDatabase;
  @Nullable private UnifiedEventsTable myTaskEventTable;
  @Nullable private ImportedSessionMapping myImportedSessionMapping;
  private long myTaskSessionId = -1;
  @Nullable private String myTaskDbPath;

  public TaskDatabaseManager(@NotNull LogService logService,
                             @NotNull Consumer<Throwable> noPiiExceptionHandler,
                             @NotNull DataStoreTable.DataStoreTableErrorCallback errorCallback) {
    myLogService = logService;
    myNoPiiExceptionHandler = noPiiExceptionHandler;
    myErrorCallback = errorCallback;
  }

  /**
   * @return The table for querying events from the currently active task database, if any.
   */
  @Nullable
  public synchronized UnifiedEventsTable getTaskEventsTable() {
    return myTaskEventTable;
  }

  /**
   * @return For an imported session, returns the mapping from the fake session IDs to the real ones.
   */
  @Nullable
  public synchronized ImportedSessionMapping getImportedSessionMapping() {
    return myImportedSessionMapping;
  }

  /**
   * Sets the active task database. This can be used to either create a new database for a new
   * recording, or to open an existing database file (e.g. an imported trace or re-opening a
   * recorded task).
   */
  public synchronized void setTaskDb(long sessionId,
                                     @Nullable String dbPath,
                                     @Nullable Common.ProfilerTaskType taskType,
                                     long streamId,
                                     int pid) {
    // If we are asked to set the same DB that is already active
    if (myTaskSessionId == sessionId && dbPath != null && dbPath.equals(myTaskDbPath)) {
      return;
    }

    shutdown();

    // If dbPath is null/empty, we are done (we just disconnected).
    if (dbPath == null || dbPath.isEmpty()) {
      return;
    }

    // If a new path is provided, connect to it.
    DataStoreDatabase db =
      new DataStoreDatabase(dbPath, DataStoreDatabase.Characteristic.DURABLE, myLogService, myNoPiiExceptionHandler, false, true);

    if (taskType != null && pid != 0) {
      // This is a live recording. Write the original identifiers to the metadata.
      writeTaskDbMetadata(db.getConnection(), taskType, streamId, pid);
    }
    else {
      // This is an imported or reopened session. Attempt to create the mapping from its metadata.
      myImportedSessionMapping = createImportedSessionMappingFromMetadata(db.getConnection());
    }

    UnifiedEventsTable table = new UnifiedEventsTable();
    table.initialize(db.getConnection());
    DataStoreTable.addDataStoreErrorCallback(myErrorCallback);
    myTaskDatabase = db;
    myTaskEventTable = table;
    myTaskSessionId = sessionId;
    myTaskDbPath = dbPath;
  }

  /**
   * Disconnects the task database for the given session ID.
   */
  public synchronized void unsetTaskDb(long sessionId) {
    // Only unset if the session ID matches. This prevents a mis-timed call from
    // unsetting a DB for a different, newer task.
    if (myTaskSessionId == sessionId) {
      shutdown();
    }
  }

  /**
   * Shuts down the connection to the currently active task database, if any.
   */
  public void shutdown() {
    if (myTaskDatabase != null) {
      myTaskDatabase.disconnect();
      myTaskDatabase = null;
      myImportedSessionMapping = null;
      myTaskEventTable = null;
      myTaskDbPath = null;
      myTaskSessionId = -1;
    }
  }

  /**
   * Reads the metadata from a task DB to create a mapping from the fake session ID (used by Studio)
   * to the real stream/process IDs (used in the DB file). This is necessary for querying imported
   * traces.
   */
  @Nullable
  private ImportedSessionMapping createImportedSessionMappingFromMetadata(@NotNull Connection connection) {
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery(
           "SELECT key, value FROM _metadata WHERE key = '" + METADATA_KEY_ORIGINAL_STREAM_ID + "' OR key = '" +
           METADATA_KEY_ORIGINAL_PID + "'")) {

      long streamId = -1;
      int pid = -1;
      while (rs.next()) {
        String key = rs.getString("key");
        if (METADATA_KEY_ORIGINAL_STREAM_ID.equals(key)) {
          streamId = Long.parseLong(rs.getString("value"));
        }
        else if (METADATA_KEY_ORIGINAL_PID.equals(key)) {
          pid = Integer.parseInt(rs.getString("value"));
        }
      }
      if (streamId != -1 && pid != -1) {
        return new ImportedSessionMapping(streamId, pid);
      }
    }
    catch (SQLException | NumberFormatException e) {
      myLogService.getLogger(TaskDatabaseManager.class).warn(e);
    }
    return null;
  }

  /**
   * Writes metadata to a new task DB, including original stream/process IDs and Studio version.
   */
  private void writeTaskDbMetadata(@NotNull Connection connection, @NotNull Common.ProfilerTaskType taskType, long streamId, int pid) {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(
        "CREATE TABLE IF NOT EXISTS _metadata (" +
        "key TEXT PRIMARY KEY," +
        "value TEXT" +
        ");");
    }
    catch (SQLException e) {
      myLogService.getLogger(TaskDatabaseManager.class).error(e);
      return;
    }

    // Using "INSERT OR IGNORE" prevents overwriting any existing data.
    try (PreparedStatement stmt = connection.prepareStatement(
      "INSERT OR IGNORE INTO _metadata (key, value) VALUES (?, ?)")) {
      stmt.setString(1, METADATA_KEY_TASK_TYPE);
      stmt.setString(2, taskType.toString());
      stmt.addBatch();

      stmt.setString(1, METADATA_KEY_ORIGINAL_STREAM_ID);
      stmt.setString(2, String.valueOf(streamId));
      stmt.addBatch();

      stmt.setString(1, METADATA_KEY_ORIGINAL_PID);
      stmt.setString(2, String.valueOf(pid));
      stmt.addBatch();

      if (ApplicationManager.getApplication() != null) {
        ApplicationInfo appInfo = ApplicationInfo.getInstance();
        stmt.setString(1, METADATA_KEY_STUDIO_VERSION);
        stmt.setString(2, appInfo.getMajorVersion() + "." + appInfo.getMinorVersion());
        stmt.addBatch();
      }

      stmt.setString(1, METADATA_KEY_CREATED_AT);
      stmt.setString(2, String.valueOf(System.currentTimeMillis()));
      stmt.addBatch();

      stmt.executeBatch();
    }
    catch (SQLException e) {
      myLogService.getLogger(TaskDatabaseManager.class).error(e);
    }
  }
}