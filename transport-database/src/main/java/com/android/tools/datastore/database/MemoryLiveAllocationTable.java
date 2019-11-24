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

import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.INSERT_ALLOC_CONTEXTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.INSERT_ALLOC_EVENTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.INSERT_JNI_REF;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.INSERT_OR_REPLACE_ALLOCATION_SAMPLING_RATE_EVENT;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_ALLOCATION_SAMPLING_RATE_EVENTS_BY_TIME;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_ALLOC_CONTEXTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_ALLOC_EVENTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_JNI_EVENTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.values;

import com.android.tools.datastore.LogService;
import com.android.tools.idea.protobuf.InvalidProtocolBufferException;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.BatchJNIGlobalRefEvent;
import com.android.tools.profiler.proto.Memory.JNIGlobalReferenceEvent;
import com.android.tools.profiler.proto.Memory.NativeBacktrace;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class MemoryLiveAllocationTable extends DataStoreTable<MemoryLiveAllocationTable.MemoryStatements> {
  @NotNull private final LogService myLogService;

  public enum MemoryStatements {
    INSERT_ALLOC_CONTEXTS("INSERT OR IGNORE INTO Memory_AllocationContexts (Session, Timestamp, Data) VALUES (?, ?, ?)"),
    INSERT_ALLOC_EVENTS("INSERT OR IGNORE INTO Memory_AllocationEvents (Session, Timestamp, Data) VALUES (?, ?, ?)"),
    QUERY_ALLOC_CONTEXTS(
      "SELECT Data FROM Memory_AllocationContexts WHERE Session = ? AND Timestamp > ? AND Timestamp <= ? ORDER BY Timestamp ASC"),
    QUERY_ALLOC_EVENTS(
      "SELECT Data FROM Memory_AllocationEvents WHERE Session = ? AND Timestamp > ? AND Timestamp <= ? ORDER BY Timestamp ASC"),

    INSERT_JNI_REF("INSERT OR IGNORE INTO Memory_JniGlobalReferences (Session, Timestamp, Data) VALUES (?, ?, ?)"),
    QUERY_JNI_EVENTS(
      "SELECT Data FROM Memory_JniGlobalReferences WHERE Session = ? AND Timestamp > ? AND Timestamp <= ? ORDER BY Timestamp ASC"),

    INSERT_OR_REPLACE_ALLOCATION_SAMPLING_RATE_EVENT(
      "INSERT OR REPLACE INTO Memory_AllocationSamplingRateEvent (Session, Timestamp, Data) VALUES (?, ?, ?)"),
    QUERY_ALLOCATION_SAMPLING_RATE_EVENTS_BY_TIME(
      "SELECT Data FROM Memory_AllocationSamplingRateEvent WHERE Session = ? AND Timestamp > ? AND Timestamp <= ? ORDER BY Timestamp ASC");


    @NotNull private final String mySqlStatement;

    MemoryStatements(@NotNull String sqlStatement) {
      mySqlStatement = sqlStatement;
    }

    @NotNull
    public String getStatement() {
      return mySqlStatement;
    }
  }

  @NotNull
  private LogService.Logger getLogger() {
    return myLogService.getLogger(MemoryLiveAllocationTable.class);
  }

  public MemoryLiveAllocationTable(@NotNull LogService logService) {
    myLogService = logService;
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      // O+ Allocation Tracking
      createTable("Memory_AllocationContexts", "Session INTEGER NOT NULL", "Timestamp INTEGER", "Data BLOB",
                  "PRIMARY KEY(Session, Timestamp)");
      createTable("Memory_AllocationEvents", "Session INTEGER NOT NULL", "Timestamp INTEGER", "Data BLOB",
                  "PRIMARY KEY(Session, Timestamp)");
      createTable("Memory_JniGlobalReferences", "Session INTEGER NOT NULL", "Timestamp INTEGER", "Data BLOB",
                  "PRIMARY KEY(Session, Timestamp)");
      createTable("Memory_AllocationSamplingRateEvent", "Session INTEGER NOT NULL", "Timestamp INTEGER", "Data BLOB",
                  "PRIMARY KEY(Session, Timestamp)");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      for (MemoryStatements statement : values()) {
        createStatement(statement, statement.getStatement());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  public List<Memory.BatchAllocationEvents> getAllocationEvents(Common.Session session, long startTime, long endTime) {
    List<Memory.BatchAllocationEvents> results = new ArrayList<>();
    try {
      ResultSet resultSet = executeQuery(QUERY_ALLOC_EVENTS, session.getSessionId(), startTime, endTime);
      while (resultSet.next()) {
        results.add(Memory.BatchAllocationEvents.newBuilder().mergeFrom(resultSet.getBytes(1)).build());
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
    return results;
  }

  @NotNull
  public List<Memory.BatchAllocationContexts> getAllocationContexts(Common.Session session, long startTime, long endTime) {
    List<Memory.BatchAllocationContexts> results = new ArrayList<>();
    try {
      ResultSet resultSet = executeQuery(QUERY_ALLOC_CONTEXTS, session.getSessionId(), startTime, endTime);
      while (resultSet.next()) {
        results.add(Memory.BatchAllocationContexts.newBuilder().mergeFrom(resultSet.getBytes(1)).build());
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
    return results;
  }

  private static JNIGlobalReferenceEvent readJniEventFromResultSet(ResultSet resultset, JNIGlobalReferenceEvent.Type type)
    throws SQLException {
    int objectTag = resultset.getInt("Tag");
    long refValue = resultset.getLong("RefValue");
    long timestamp = resultset.getLong("Timestamp");
    int threadId = resultset.getInt("ThreadId");
    byte[] backtrace = resultset.getBytes("Backtrace");

    JNIGlobalReferenceEvent.Builder event = JNIGlobalReferenceEvent.newBuilder();
    event.setEventType(type);
    event.setObjectTag(objectTag);
    event.setRefValue(refValue);
    event.setTimestamp(timestamp);
    event.setThreadId(threadId);
    if (backtrace != null && backtrace.length != 0) {
      try {
        event.setBacktrace(NativeBacktrace.parseFrom(backtrace));
      }
      catch (InvalidProtocolBufferException ex) {
        onError(ex);
      }
    }
    return event.build();
  }

  public List<BatchJNIGlobalRefEvent> getJniReferenceEvents(Common.Session session, long startTime, long endTime) {

    List<Memory.BatchJNIGlobalRefEvent> results = new ArrayList<>();
    try {
      ResultSet resultSet = executeQuery(QUERY_JNI_EVENTS, session.getSessionId(), startTime, endTime);
      while (resultSet.next()) {
        results.add(Memory.BatchJNIGlobalRefEvent.newBuilder().mergeFrom(resultSet.getBytes(1)).build());
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
    return results;
  }

  public void insertJniReferenceData(@NotNull Common.Session session, @NotNull Memory.BatchJNIGlobalRefEvent sample) {
    execute(INSERT_JNI_REF, session.getSessionId(), sample.getTimestamp(), sample.toByteArray());
  }

  public void insertAllocationContexts(Common.Session session, Memory.BatchAllocationContexts sample) {
    // Convert the class names from JNI to Java formats before inserting into the database.
    Memory.BatchAllocationContexts.Builder convertedSampleBuilder = sample.toBuilder();

    List<Memory.AllocatedClass> classes = convertedSampleBuilder.getClassesList();
    convertedSampleBuilder.clearClasses();
    List<Memory.AllocatedClass> convertedClasses = classes.stream()
      .map(klass -> klass.toBuilder().setClassName(jniToJavaName(klass.getClassName())).build())
      .collect(Collectors.toList());
    convertedSampleBuilder.addAllClasses(convertedClasses);
    execute(INSERT_ALLOC_CONTEXTS, session.getSessionId(), sample.getTimestamp(), convertedSampleBuilder.build().toByteArray());
  }

  public void insertAllocationEvents(Common.Session session, Memory.BatchAllocationEvents sample) {
    execute(INSERT_ALLOC_EVENTS, session.getSessionId(), sample.getTimestamp(), sample.toByteArray());
  }

  public void insertOrReplaceAllocationSamplingRateEvent(@NotNull Common.Session session, @NotNull AllocationSamplingRateEvent event) {
    execute(INSERT_OR_REPLACE_ALLOCATION_SAMPLING_RATE_EVENT, session.getSessionId(), event.getTimestamp(), event.toByteArray());
  }

  @NotNull
  public List<AllocationSamplingRateEvent> getAllocationSamplingRateEvents(long sessionId, long startTime, long endTime) {
    List<AllocationSamplingRateEvent> results = new ArrayList<>();
    try {
      ResultSet resultSet = executeQuery(QUERY_ALLOCATION_SAMPLING_RATE_EVENTS_BY_TIME, sessionId, startTime, endTime);
      while (resultSet.next()) {
        results.add(AllocationSamplingRateEvent.newBuilder().mergeFrom(resultSet.getBytes(1)).build());
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
    return results;
  }

  /**
   * Converts jni class names into java names
   * e.g. Ljava/lang/String; -> java.lang.String
   * e.g. [[Ljava/lang/Object; -> java.lang.Object[][]
   * <p>
   * JNI primitive type names are converted too
   * e.g. Z -> boolean
   */
  private static String jniToJavaName(String jniName) {
    if (jniName.isEmpty()) {
      return jniName;
    }

    int arrayDimension = 0;
    int classNameIndex = 0;
    while (jniName.charAt(classNameIndex) == '[') {
      arrayDimension++;
      classNameIndex++;
    }

    String javaName;
    if (jniName.charAt(classNameIndex) == 'L') {
      // Class - drop the prefix 'L' and postfix ';'
      javaName = jniName.substring(classNameIndex + 1, jniName.length() - 1).replace('/', '.');
    }
    else {
      javaName = jniName.substring(classNameIndex);
      switch (javaName) {
        case "Z":
          javaName = "boolean";
          break;
        case "B":
          javaName = "byte";
          break;
        case "C":
          javaName = "char";
          break;
        case "S":
          javaName = "short";
          break;
        case "I":
          javaName = "int";
          break;
        case "J":
          javaName = "long";
          break;
        case "F":
          javaName = "float";
          break;
        case "D":
          javaName = "double";
          break;
        default:
          break;
      }
    }

    while (arrayDimension > 0) {
      javaName += "[]";
      arrayDimension--;
    }
    return javaName;
  }
}
