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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.profiler.proto.Common;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class TaskDatabaseManagerTest {

  @Rule public TemporaryFolder myTempFolder = new TemporaryFolder();
  private TaskDatabaseManager myManager;
  private File myTempDir;
  private AtomicReference<Throwable> myExceptionHandler;

  @Before
  public void setUp() throws Exception {
    // Mock ApplicationInfo for writeTaskDbMetadata
    ApplicationInfo appInfo = Mockito.mock(ApplicationInfo.class);
    Mockito.when(appInfo.getMajorVersion()).thenReturn("2025");
    Mockito.when(appInfo.getMinorVersion()).thenReturn("1.1");
    Application app = Mockito.mock(Application.class);
    Mockito.when(app.getService(ApplicationInfo.class)).thenReturn(appInfo);
    ApplicationManager.setApplication(app, () -> {});

    myTempDir = myTempFolder.newFolder();
    myExceptionHandler = new AtomicReference<>();
    myManager = new TaskDatabaseManager(new FakeLogService(),
                                        t -> myExceptionHandler.set(t),
                                        t -> myExceptionHandler.set(t));
  }

  @After
  public void tearDown() {
    myManager.shutdown();
  }

  @Test
  public void initialStateIsNull() {
    assertThat(myManager.getTaskEventsTable()).isNull();
  }

  @Test
  public void setAndUnsetTaskDb() throws Exception {
    assertThat(myManager.getTaskEventsTable()).isNull();

    String taskDbPath = new File(myTempDir, "task.db").getAbsolutePath();
    long sessionId = 1234;
    Common.ProfilerTaskType taskType = Common.ProfilerTaskType.SYSTEM_TRACE;
    long streamId = 1L;
    int pid = 100;

    // Set a task DB and verify we get a table.
    myManager.setTaskDb(sessionId, taskDbPath, taskType, streamId, pid);
    UnifiedEventsTable table = myManager.getTaskEventsTable();
    assertThat(table).isNotNull();
    assertThat(myExceptionHandler.get()).isNull();

    // Verify we can use the table.
    Common.Event event = Common.Event.newBuilder().setTimestamp(1).build();
    table.insertUnifiedEvent(sessionId, event);
    assertThat(table.queryUnifiedEvents()).containsExactly(event);

    // Verify the database file was created and contains metadata.
    File dbFile = new File(taskDbPath);
    assertThat(dbFile.exists()).isTrue();

    Map<String, String> metadata = new HashMap<>();
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + taskDbPath);
         Statement stmt = c.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT key, value FROM _metadata")) {
      while (rs.next()) {
        metadata.put(rs.getString("key"), rs.getString("value"));
      }
    }
    assertThat(metadata.get(TaskDatabaseManager.METADATA_KEY_TASK_TYPE)).isEqualTo(taskType.toString());
    assertThat(metadata.get(TaskDatabaseManager.METADATA_KEY_ORIGINAL_STREAM_ID)).isEqualTo(String.valueOf(streamId));
    assertThat(metadata.get(TaskDatabaseManager.METADATA_KEY_ORIGINAL_PID)).isEqualTo(String.valueOf(pid));
    assertThat(metadata.get(TaskDatabaseManager.METADATA_KEY_STUDIO_VERSION)).isEqualTo("2025.1.1");
    assertThat(metadata.get(TaskDatabaseManager.METADATA_KEY_CREATED_AT)).isNotEmpty();

    // Setting the same DB should be a no-op.
    myManager.setTaskDb(sessionId, taskDbPath, taskType, streamId, pid);
    assertThat(myManager.getTaskEventsTable()).isSameAs(table);

    // Unsetting with a different session ID should be a no-op.
    long otherSessionId = 5678;
    myManager.unsetTaskDb(otherSessionId);
    assertThat(myManager.getTaskEventsTable()).isNotNull();

    // Unset the task DB with the correct session ID and verify the table is gone.
    myManager.unsetTaskDb(sessionId);
    assertThat(myManager.getTaskEventsTable()).isNull();
  }

  @Test
  public void switchingTaskDbShutsDownOldOne() {
    String taskDbPath1 = new File(myTempDir, "task1.db").getAbsolutePath();
    long sessionId1 = 1;
    myManager.setTaskDb(sessionId1, taskDbPath1, Common.ProfilerTaskType.SYSTEM_TRACE, 1L, 101);
    UnifiedEventsTable table1 = myManager.getTaskEventsTable();
    assertThat(table1).isNotNull();

    String taskDbPath2 = new File(myTempDir, "task2.db").getAbsolutePath();
    long sessionId2 = 2;
    myManager.setTaskDb(sessionId2, taskDbPath2, Common.ProfilerTaskType.HEAP_DUMP, 2L, 102);
    UnifiedEventsTable table2 = myManager.getTaskEventsTable();
    assertThat(table2).isNotNull();
    assertThat(table2).isNotSameAs(table1);

    // Check that the connection to the first DB is closed.
    try {
      table1.queryUnifiedEvents();
      // Should throw SQLException because the connection is closed.
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  public void importedSessionReadsMapping() throws Exception {
    String taskDbPath = new File(myTempDir, "imported.db").getAbsolutePath();
    long realStreamId = 99L;
    int realPid = 999;

    // Create a DB file with pre-existing metadata, simulating an imported trace.
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + taskDbPath);
         Statement stmt = c.createStatement()) {
      stmt.execute("CREATE TABLE IF NOT EXISTS _metadata (key TEXT PRIMARY KEY, value TEXT)");
      stmt.execute(String.format("INSERT INTO _metadata (key, value) VALUES ('%s', '%d')",
                                 TaskDatabaseManager.METADATA_KEY_ORIGINAL_STREAM_ID, realStreamId));
      stmt.execute(String.format("INSERT INTO _metadata (key, value) VALUES ('%s', '%d')",
                                 TaskDatabaseManager.METADATA_KEY_ORIGINAL_PID, realPid));
    }

    // Set the task DB with pid=0, simulating an import.
    long fakeSessionId = 12345;
    myManager.setTaskDb(fakeSessionId, taskDbPath, null, fakeSessionId, 0);

    // Verify that the mapping was correctly read.
    TaskDatabaseManager.ImportedSessionMapping mapping = myManager.getImportedSessionMapping();
    assertThat(mapping).isNotNull();
    assertThat(mapping.realStreamId()).isEqualTo(realStreamId);
    assertThat(mapping.realPid()).isEqualTo(realPid);
  }
}
