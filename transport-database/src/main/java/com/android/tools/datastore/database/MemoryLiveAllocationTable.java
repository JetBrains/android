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

import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.COUNT_JNI_REF_RECORDS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.INSERT_ALLOC_CONTEXTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.INSERT_ALLOC_EVENTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.INSERT_JNI_REF;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.INSERT_NATIVE_FRAME;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.INSERT_OR_REPLACE_ALLOCATION_SAMPLING_RATE_EVENT;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.PRUNE_JNI_REF_RECORDS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_ALLOCATION_SAMPLING_RATE_EVENTS_BY_TIME;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_ALLOC_CONTEXTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_ALLOC_EVENTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_JNI_REF_CREATE_EVENTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_JNI_REF_DELETE_EVENTS;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.QUERY_NATIVE_FRAME;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.UPDATE_JNI_REF;
import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.values;

import com.android.tools.datastore.LogService;
import com.android.tools.idea.protobuf.InvalidProtocolBufferException;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.BatchJNIGlobalRefEvent;
import com.android.tools.profiler.proto.Memory.JNIGlobalReferenceEvent;
import com.android.tools.profiler.proto.Memory.MemoryMap;
import com.android.tools.profiler.proto.Memory.NativeBacktrace;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import com.android.tools.profiler.proto.MemoryProfiler.NativeCallStack;
import com.google.common.annotations.VisibleForTesting;
import gnu.trove.TLongHashSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
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

    INSERT_JNI_REF(
      "INSERT OR IGNORE INTO Memory_JniGlobalReferences " +
      "(Session, Tag, RefValue, AllocTime, AllocThreadId, AllocBacktrace, FreeThreadId, FreeTime) " +
      "VALUES (?, ?, ?, ?, ?, ?, 0, " + Long.MAX_VALUE + ")"),

    UPDATE_JNI_REF(
      "UPDATE Memory_JniGlobalReferences " +
      "SET FreeTime = ?, FreeBacktrace = ?, FreeThreadId = ?" +
      "WHERE Session = ? AND Tag = ? AND RefValue = ?"),

    COUNT_JNI_REF_RECORDS("SELECT COUNT(1) FROM Memory_JniGlobalReferences"),
    PRUNE_JNI_REF_RECORDS("DELETE FROM Memory_JniGlobalReferences WHERE Session = ? AND FreeTime <= (" +
                          " SELECT MAX(FreeTime)" +
                          " FROM Memory_JniGlobalReferences" +
                          " WHERE Session = ? AND FreeTime < " + Long.MAX_VALUE +
                          " ORDER BY FreeTime" +
                          " LIMIT ?" +
                          ")"),

    QUERY_JNI_REF_CREATE_EVENTS(
      "SELECT Tag, RefValue, AllocTime AS Timestamp, AllocThreadId AS ThreadId, AllocBacktrace AS Backtrace " +
      "FROM Memory_JniGlobalReferences " +
      "WHERE Session = ? AND AllocTime >= ? AND AllocTime <= ? AND FreeTime >= ? AND FreeTime <= ? " +
      "ORDER BY AllocTime"),

    QUERY_JNI_REF_DELETE_EVENTS(
      "SELECT Tag, RefValue, FreeTime AS Timestamp, FreeThreadId AS ThreadId, FreeBacktrace AS Backtrace " +
      "FROM Memory_JniGlobalReferences " +
      "WHERE Session = ? AND AllocTime >= ? AND AllocTime <= ? AND FreeTime >= ? AND FreeTime <= ? " +
      "ORDER BY FreeTime"),

    INSERT_NATIVE_FRAME("INSERT OR IGNORE INTO Memory_NativeFrames (Session, Address, Offset, Module) VALUES (?, ?, ?, ?)"),
    QUERY_NATIVE_FRAME("SELECT Offset, Module FROM Memory_NativeFrames WHERE (Session = ?) AND (Address = ?)"),

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

  // 5M ought to be enough for anybody (~300MB of data)
  // Note - Google Search app can easily allocate 100k+ temporary objects in an relatively short amount of time (e.g. one search query)
  private int myAllocationCountLimit = 5000000;
  private final static byte[] EMPTY_BYTE_ARRAY = new byte[0];

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
      createTable("Memory_NativeFrames", "Session INTEGER NOT NULL", "Address INTEGER NOT NULL",
                  "Offset INTEGER", "Module TEXT", "PRIMARY KEY(Session, Address)");
      createTable("Memory_JniGlobalReferences", "Session INTEGER NOT NULL", "Tag INTEGER",
                  "RefValue INTEGER", "AllocTime INTEGER", "FreeTime INTEGER", "AllocThreadId INTEGER", "FreeThreadId INTEGER",
                  "AllocBacktrace BLOB", "FreeBacktrace BLOB", "PRIMARY KEY(Session, Tag, RefValue)");
      createTable("Memory_AllocationSamplingRateEvent", "Session INTEGER NOT NULL", "Timestamp INTEGER", "Data BLOB",
                  "PRIMARY KEY(Session, Timestamp)");

      createIndex("Memory_NativeFrames", 0, "Session", "Address");
      createIndex("Memory_JniGlobalReferences", 0, "Session", "AllocTime");
      createIndex("Memory_JniGlobalReferences", 1, "Session", "FreeTime");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @VisibleForTesting
  void setAllocationCountLimit(int allocationCountLimit) {
    myAllocationCountLimit = allocationCountLimit;
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

  public NativeCallStack resolveNativeBacktrace(@NotNull Common.Session session, @NotNull NativeBacktrace backtrace) {
    NativeCallStack.Builder resultBuilder = NativeCallStack.newBuilder();
    try {
      for (Long address : backtrace.getAddressesList()) {
        NativeCallStack.NativeFrame frame = NativeCallStack.NativeFrame.getDefaultInstance();
        ResultSet result = executeQuery(QUERY_NATIVE_FRAME, session.getSessionId(), address);
        if (result.next()) {
          frame = NativeCallStack.NativeFrame.newBuilder().setAddress(address).setModuleOffset(result.getLong(1))
            .setModuleName(result.getString(2)).build();
        }
        resultBuilder.addFrames(frame);
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return resultBuilder.build();
  }

  public BatchJNIGlobalRefEvent getJniReferencesSnapshot(Common.Session session, long endTime) {
    BatchJNIGlobalRefEvent.Builder resultBuilder = BatchJNIGlobalRefEvent.newBuilder();
    long timestamp = 0;
    try {
      ResultSet allocResultset = executeQuery(QUERY_JNI_REF_CREATE_EVENTS, session.getSessionId(), 0, endTime, endTime, Long.MAX_VALUE);
      while (allocResultset.next()) {
        JNIGlobalReferenceEvent event = readJniEventFromResultSet(allocResultset, JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF);
        timestamp = Math.max(timestamp, event.getTimestamp());
        resultBuilder.addEvents(event);
      }
      resultBuilder.setTimestamp(timestamp);
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return resultBuilder.build();
  }

  public BatchJNIGlobalRefEvent getJniReferencesEventsFromRange(Common.Session session, long startTime, long endTime) {
    BatchJNIGlobalRefEvent.Builder resultBuilder = BatchJNIGlobalRefEvent.newBuilder();
    long timestamp = 0;
    try {
      ResultSet allocResultset =
        executeQuery(QUERY_JNI_REF_CREATE_EVENTS, session.getSessionId(), startTime, endTime - 1, startTime, Long.MAX_VALUE);
      while (allocResultset.next()) {
        JNIGlobalReferenceEvent event = readJniEventFromResultSet(allocResultset, JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF);
        timestamp = Math.max(timestamp, event.getTimestamp());
        resultBuilder.addEvents(event);
      }

      ResultSet freeResultset = executeQuery(QUERY_JNI_REF_DELETE_EVENTS, session.getSessionId(), 0, endTime - 1, startTime, endTime - 1);
      while (freeResultset.next()) {
        JNIGlobalReferenceEvent event = readJniEventFromResultSet(freeResultset, JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF);
        timestamp = Math.max(timestamp, event.getTimestamp());
        resultBuilder.addEvents(event);
      }
      resultBuilder.setTimestamp(timestamp);
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return resultBuilder.build();
  }

  private static TreeMap<Long, MemoryMap.MemoryRegion> buildAddressMap(MemoryMap map) {
    TreeMap<Long, MemoryMap.MemoryRegion> result = new TreeMap<>();
    if (map == null) {
      return result;
    }
    for (MemoryMap.MemoryRegion region : map.getRegionsList()) {
      result.put(region.getStartAddress(), region);
    }
    return result;
  }

  private static MemoryMap.MemoryRegion getRegionByAddress(TreeMap<Long, MemoryMap.MemoryRegion> addressMap, Long address) {
    Map.Entry<Long, MemoryMap.MemoryRegion> entry = addressMap.floorEntry(address);
    if (entry == null) {
      return null;
    }
    MemoryMap.MemoryRegion region = entry.getValue();
    if (address >= region.getStartAddress() && address < region.getEndAddress()) {
      return region;
    }
    return null;
  }

  public void insertJniReferenceData(@NotNull Common.Session session, @NotNull BatchJNIGlobalRefEvent batch) {
    PreparedStatement insertRefStatement = null;
    PreparedStatement updateRefStatement = null;
    PreparedStatement insertFrameStatement = null;
    if (isClosed()) {
      return;
    }
    try {
      TreeMap<Long, MemoryMap.MemoryRegion> addressMap = buildAddressMap(batch.getMemoryMap());
      TLongHashSet insertedAddresses = new TLongHashSet();
      for (JNIGlobalReferenceEvent event : batch.getEventsList()) {
        long refValue = event.getRefValue();
        int objectTag = event.getObjectTag();
        long timestamp = event.getTimestamp();
        int threadId = event.getThreadId();
        byte[] backtrace = EMPTY_BYTE_ARRAY;

        if (event.hasBacktrace()) {
          backtrace = event.getBacktrace().toByteArray();
          for (Long address : event.getBacktrace().getAddressesList()) {
            if (!insertedAddresses.contains(address)) {
              String module = "";
              long offset = 0;
              MemoryMap.MemoryRegion region = getRegionByAddress(addressMap, address);
              if (region != null) {
                module = region.getName();
                // Adjust address to represent module offset.
                offset = address + region.getFileOffset() - region.getStartAddress();
              }
              if (insertFrameStatement == null) {
                insertFrameStatement = getStatementMap().get(INSERT_NATIVE_FRAME);
              }
              applyParams(insertFrameStatement, session.getSessionId(), address, offset, module);
              insertFrameStatement.addBatch();
              insertedAddresses.add(address);
            }
          }
        }

        switch (event.getEventType()) {
          case CREATE_GLOBAL_REF:
            if (insertRefStatement == null) {
              insertRefStatement = getStatementMap().get(INSERT_JNI_REF);
            }
            applyParams(insertRefStatement, session.getSessionId(), objectTag, refValue, timestamp, threadId, backtrace);
            insertRefStatement.addBatch();
            break;
          case DELETE_GLOBAL_REF:
            if (updateRefStatement == null) {
              updateRefStatement = getStatementMap().get(UPDATE_JNI_REF);
            }
            applyParams(updateRefStatement, timestamp, backtrace, threadId, session.getSessionId(), objectTag, refValue);
            updateRefStatement.addBatch();
            break;
          default:
            assert false;
        }
      }

      if (insertFrameStatement != null) {
        insertFrameStatement.executeBatch();
      }
      if (insertRefStatement != null) {
        insertRefStatement.executeBatch();
      }
      if (updateRefStatement != null) {
        updateRefStatement.executeBatch();
      }
      if (batch.getEventsCount() > 0) {
        pruneJniRefRecords(session);
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
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

  private void pruneJniRefRecords(@NotNull Common.Session session) {
    try {
      // TODO save data to disk
      ResultSet result = executeQuery(COUNT_JNI_REF_RECORDS);
      result.next();
      int rowCount = result.getInt(1);
      if (rowCount > myAllocationCountLimit) {
        int pruneCount = rowCount - myAllocationCountLimit;
        execute(PRUNE_JNI_REF_RECORDS, session.getSessionId(), session.getSessionId(), pruneCount);
        getLogger()
          .info(String.format(Locale.US, "JNI ref records have exceed %d entries. Attempting to prune %d.", myAllocationCountLimit,
                              pruneCount));
      }
    }
    catch (SQLException e) {
      onError(e);
    }
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
      javaName = jniName.substring(classNameIndex, jniName.length());
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
