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

import com.android.annotations.VisibleForTesting;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.protobuf3jarjar.InvalidProtocolBufferException;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.*;

public class MemoryLiveAllocationTable extends DataStoreTable<MemoryLiveAllocationTable.MemoryStatements> {
  public enum MemoryStatements {
    INSERT_CLASS("INSERT OR IGNORE INTO Memory_AllocatedClass (Session, Tag, AllocTime, Name) VALUES (?, ?, ?, ?)"),
    INSERT_ALLOC("INSERT OR IGNORE INTO Memory_AllocationEvents " +
                 "(Session, Tag, ClassTag, AllocTime, FreeTime, Size, Length, ThreadId, StackId, HeapId) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
    INSERT_METHOD("INSERT OR IGNORE INTO Memory_MethodInfos (Session, MethodId, MethodName, ClassName) VALUES (?, ?, ?, ?)"),
    INSERT_ENCODED_STACK("INSERT OR IGNORE INTO Memory_StackInfos (Session, StackId, AllocTime, StackData) VALUES (?, ?, ?, ?)"),
    INSERT_THREAD_INFO("INSERT OR IGNORE INTO Memory_ThreadInfos (Session, ThreadId, AllocTime, ThreadName) VALUES (?, ?, ?, ?)"),
    UPDATE_ALLOC("UPDATE Memory_AllocationEvents SET FreeTime = ? WHERE Session = ? AND Tag = ?"),
    QUERY_CLASS("SELECT Tag, AllocTime, Name FROM Memory_AllocatedClass where Session = ? AND AllocTime >= ? AND AllocTime < ?"),
    QUERY_LATEST_ALLOC_TIME("SELECT MAX(AllocTime) FROM Memory_AllocationEvents WHERE Session = ?"),
    QUERY_LATEST_FREE_TIME("SELECT MAX(FreeTime) FROM Memory_AllocationEvents WHERE Session = ? AND FreeTime < ?"),
    QUERY_SNAPSHOT(
      "SELECT Tag, ClassTag, AllocTime, Size, Length, ThreadId, StackId, HeapId FROM Memory_AllocationEvents " +
      "WHERE Session = ? AND AllocTime < ? AND FreeTime > ?"),
    QUERY_ALLOC_BY_ALLOC_TIME(
      "SELECT Tag, ClassTag, AllocTime, FreeTime, Size, Length, ThreadId, StackId, HeapId FROM Memory_AllocationEvents " +
      "WHERE Session = ? AND AllocTime >= ? AND AllocTime < ?"),
    QUERY_ALLOC_BY_FREE_TIME(
      "SELECT Tag, ClassTag, AllocTime, FreeTime, Size, Length, ThreadId, StackId, HeapId FROM Memory_AllocationEvents " +
      "WHERE Session = ? AND FreeTime >= ? AND FreeTime < ?"),
    QUERY_METHOD_INFO("Select MethodName, ClassName FROM Memory_MethodInfos WHERE Session = ? AND MethodId = ?"),
    QUERY_ENCODED_STACK_INFO_BY_TIME(
      "Select StackData FROM Memory_StackInfos WHERE Session = ? AND AllocTime >= ? AND AllocTime < ?"),
    QUERY_THREAD_INFO_BY_TIME(
      "Select ThreadId, ThreadName FROM Memory_ThreadInfos WHERE Session = ? AND AllocTime >= ? AND AllocTime < ?"),

    COUNT_ALLOC("SELECT count(*) FROM Memory_AllocationEvents"),
    PRUNE_ALLOC("DELETE FROM Memory_AllocationEvents WHERE Session = ? AND FreeTime <= (" +
                " SELECT MAX(FreeTime)" +
                " FROM Memory_AllocationEvents" +
                " WHERE Session = ? AND FreeTime < " + Long.MAX_VALUE +
                " ORDER BY FreeTime" +
                " LIMIT ?" +
                ")"),
    INSERT_JNI_REF(
      "INSERT OR IGNORE INTO Memory_JniGlobalReferences " +
      "(Session, Tag, RefValue, AllocTime, AllocThreadId, AllocStackHash, FreeThreadId, FreeStackHash, FreeTime) " +
      "VALUES (?, ?, ?, ?, ?, ?, 0, '', " + Long.MAX_VALUE + ")"),

    UPDATE_JNI_REF(
      "UPDATE Memory_JniGlobalReferences " +
      "SET FreeTime = ?, FreeStackHash = ?, FreeThreadId = ?" +
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
      "SELECT Refs.Tag, Refs.RefValue, Refs.AllocTime AS Timestamp, Refs.AllocThreadId AS ThreadId, AllockStack.Backtrace AS Backtrace " +
      "FROM Memory_JniGlobalReferences AS Refs " +
      "LEFT JOIN Memory_NativeStackInfos AS AllockStack ON Refs.Session = AllockStack.Session AND Refs.AllocStackHash = AllockStack.StackHash " +
      "WHERE Refs.Session = ? AND Refs.AllocTime >= ? AND Refs.AllocTime <= ? AND Refs.FreeTime >= ? AND Refs.FreeTime <= ? " +
      "ORDER BY Refs.AllocTime"),

    QUERY_JNI_REF_DELETE_EVENTS(
      "SELECT Refs.Tag, Refs.RefValue, Refs.FreeTime AS Timestamp, Refs.FreeThreadId AS ThreadId, FreeStack.Backtrace AS Backtrace " +
      "FROM Memory_JniGlobalReferences AS Refs " +
      "LEFT JOIN Memory_NativeStackInfos AS FreeStack ON Refs.Session = FreeStack.Session AND Refs.FreeStackHash = FreeStack.StackHash " +
      "WHERE Refs.Session = ? AND Refs.AllocTime >= ? AND Refs.AllocTime <= ? AND Refs.FreeTime >= ? AND Refs.FreeTime <= ? " +
      "ORDER BY Refs.FreeTime"),

    INSERT_NATIVE_STACK("INSERT OR IGNORE INTO Memory_NativeStackInfos (Session, StackHash, Backtrace) VALUES (?, ?, ?)");


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

  private static Logger getLogger() {
    return Logger.getInstance(MemoryLiveAllocationTable.class);
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      // O+ Allocation Tracking
      createTable("Memory_AllocatedClass", "Session INTEGER NOT NULL", "Tag INTEGER",
                  "AllocTime INTEGER", "Name TEXT", "PRIMARY KEY(Session, Tag)");
      createTable("Memory_AllocationEvents", "Session INTEGER NOT NULL", "Tag INTEGER",
                  "ClassTag INTEGER", "AllocTime INTEGER", "FreeTime INTEGER", "Size INTEGER", "Length INTEGER", "ThreadId INTEGER",
                  "StackId INTEGER", "HeapId INTEGER", "PRIMARY KEY(Session, Tag)");
      createTable("Memory_MethodInfos", "Session INTEGER NOT NULL", "MethodId INTEGER",
                  "MethodName TEXT", "ClassName TEXT", "PRIMARY KEY(Session, MethodId)");
      createTable("Memory_StackInfos", "Session INTEGER NOT NULL", "StackId INTEGER", "AllocTime INTEGER",
                  "StackData BLOB", "PRIMARY KEY(Session, StackId)");
      createTable("Memory_ThreadInfos", "Session INTEGER NOT NULL", "ThreadId INTEGER", "AllocTime INTEGER",
                  "ThreadName TEXT", "PRIMARY KEY(Session, ThreadId)");
      createTable("Memory_NativeStackInfos", "Session INTEGER NOT NULL", "StackHash TEXT",
                  "Backtrace BLOB", "PRIMARY KEY(Session, StackHash)");
      createTable("Memory_JniGlobalReferences", "Session INTEGER NOT NULL", "Tag INTEGER",
                  "RefValue INTEGER", "AllocTime INTEGER", "FreeTime INTEGER", "AllocThreadId INTEGER", "FreeThreadId INTEGER",
                  "AllocStackHash INTEGER", "FreeStackHash INTEGER", "PRIMARY KEY(Session, Tag, RefValue)");

      createIndex("Memory_AllocationEvents", 0, "Session", "AllocTime");
      createIndex("Memory_AllocationEvents", 1, "Session", "FreeTime");
      createIndex("Memory_AllocatedClass", 0, "Session", "AllocTime");
      createIndex("Memory_StackInfos", 0, "Session", "AllocTime");
      createIndex("Memory_ThreadInfos", 0, "Session", "AllocTime");
      createIndex("Memory_NativeStackInfos", 0, "Session", "StackHash");
      createIndex("Memory_JniGlobalReferences", 0, "Session", "AllocTime");
      createIndex("Memory_JniGlobalReferences", 1, "Session", "FreeTime");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
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
      getLogger().error(ex);
    }
  }

  public MemoryProfiler.BatchAllocationSample getSnapshot(Common.Session session, long endTime) {
    MemoryProfiler.BatchAllocationSample.Builder sampleBuilder = MemoryProfiler.BatchAllocationSample.newBuilder();
    try {
      ResultSet allocResult = executeQuery(QUERY_SNAPSHOT, session.getSessionId(), endTime, endTime);
      long timestamp = Long.MIN_VALUE;
      while (allocResult.next()) {
        long allocTime = allocResult.getLong(3);
        MemoryProfiler.AllocationEvent event = MemoryProfiler.AllocationEvent.newBuilder()
          .setAllocData(
            MemoryProfiler.AllocationEvent.Allocation.newBuilder().setTag(allocResult.getInt(1)).setClassTag(allocResult.getInt(2))
              .setSize(allocResult.getLong(4)).setLength(allocResult.getInt(5)).setThreadId(allocResult.getInt(6))
              .setStackId(allocResult.getInt(7)).setHeapId(allocResult.getInt(8)).build())
          .setTimestamp(allocTime).build();
        sampleBuilder.addEvents(event);
        timestamp = Math.max(timestamp, allocTime);
      }
      sampleBuilder.setTimestamp(timestamp);
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }

    return sampleBuilder.build();
  }

  public MemoryProfiler.BatchAllocationSample getAllocations(Common.Session session, long startTime, long endTime) {
    MemoryProfiler.BatchAllocationSample.Builder sampleBuilder = MemoryProfiler.BatchAllocationSample.newBuilder();
    try {
      // Then get all allocation events that are valid for requestTime.
      ResultSet allocResult = executeQuery(QUERY_ALLOC_BY_ALLOC_TIME, session.getSessionId(), startTime, endTime);
      long timestamp = Long.MIN_VALUE;

      while (allocResult.next()) {
        long allocTime = allocResult.getLong(3);
        MemoryProfiler.AllocationEvent event = MemoryProfiler.AllocationEvent.newBuilder()
          .setAllocData(
            MemoryProfiler.AllocationEvent.Allocation.newBuilder().setTag(allocResult.getInt(1)).setClassTag(allocResult.getInt(2))
              .setSize(allocResult.getLong(5)).setLength(allocResult.getInt(6)).setThreadId(allocResult.getInt(7))
              .setStackId(allocResult.getInt(8)).setHeapId(allocResult.getInt(9)).build())
          .setTimestamp(allocTime).build();
        sampleBuilder.addEvents(event);
        timestamp = Math.max(timestamp, allocTime);
      }

      ResultSet freeResult = executeQuery(QUERY_ALLOC_BY_FREE_TIME, session.getSessionId(), startTime, endTime);
      while (freeResult.next()) {
        long freeTime = freeResult.getLong(4);
        MemoryProfiler.AllocationEvent event = MemoryProfiler.AllocationEvent.newBuilder()
          .setFreeData(
            MemoryProfiler.AllocationEvent.Deallocation.newBuilder().setTag(freeResult.getInt(1)).setClassTag(freeResult.getInt(2))
              .setSize(freeResult.getLong(5)).setLength(freeResult.getInt(6)).setThreadId(freeResult.getInt(7))
              .setStackId(freeResult.getInt(8)).setHeapId(freeResult.getInt(9)).build())
          .setTimestamp(freeTime).build();
        sampleBuilder.addEvents(event);
        timestamp = Math.max(timestamp, freeTime);
      }

      sampleBuilder.setTimestamp(timestamp);
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return sampleBuilder.build();
  }

  @NotNull
  public LatestAllocationTimeResponse getLatestDataTimestamp(Common.Session session) {
    LatestAllocationTimeResponse.Builder builder = LatestAllocationTimeResponse.newBuilder();
    try {
      long latest = 0;
      ResultSet result = executeQuery(QUERY_LATEST_ALLOC_TIME, session.getSessionId());
      if (result.next()) {
        latest = Math.max(latest, result.getLong(1));
      }
      result = executeQuery(QUERY_LATEST_FREE_TIME, session.getSessionId(), Long.MAX_VALUE);
      if (result.next()) {
        latest = Math.max(latest, result.getLong(1));
      }
      builder.setTimestamp(latest);
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return builder.build();
  }

  @NotNull
  public AllocationContextsResponse getAllocationContexts(Common.Session session, long startTime, long endTime) {
    AllocationContextsResponse.Builder resultBuilder = AllocationContextsResponse.newBuilder();
    try {
      // Query all the classes
      // TODO: only return classes that are valid for current snapshot?
      ResultSet klassResult = executeQuery(QUERY_CLASS, session.getSessionId(), startTime, endTime);
      long timestamp = Long.MIN_VALUE;

      while (klassResult.next()) {
        long allocTime = klassResult.getLong(2);
        AllocatedClass klass =
          AllocatedClass.newBuilder().setClassId(klassResult.getInt(1)).setClassName(klassResult.getString(3)).build();
        resultBuilder.addAllocatedClasses(klass);
        timestamp = Math.max(timestamp, allocTime);
      }

      ResultSet stackResult = executeQuery(QUERY_ENCODED_STACK_INFO_BY_TIME, session.getSessionId(), startTime, endTime);
      while (stackResult.next()) {
        AllocationStack.Builder stackBuilder = AllocationStack.newBuilder();

        // Retrieve the EncodedAllocationStack proto and convert it into the AllocationStack format.
        // Note that we are not accounting for the timestamp recorded in the stack, as stack entries from each batched allocation sample
        // are inserted first into the database. So class data with an earlier timestamp can be inserted later.
        EncodedAllocationStack encodedStack = EncodedAllocationStack.parseFrom(stackResult.getBytes(1));
        stackBuilder.setStackId(encodedStack.getStackId());
        AllocationStack.SmallFrameWrapper.Builder frameBuilder = AllocationStack.SmallFrameWrapper.newBuilder();
        assert encodedStack.getMethodIdsCount() == encodedStack.getLineNumbersCount();
        for (int i = 0; i < encodedStack.getMethodIdsCount(); i++) {
          // Note that we don't return the class + method names here, as they are expensive to query and can incur huge memory footprint.
          // Instead, they will be fetched on demand as needed by the UI.
          AllocationStack.SmallFrame frame =
            AllocationStack.SmallFrame.newBuilder().setMethodId(encodedStack.getMethodIds(i)).setLineNumber(encodedStack.getLineNumbers(i))
              .build();
          frameBuilder.addFrames(frame);
        }
        stackBuilder.setSmallStack(frameBuilder);
        resultBuilder.addAllocationStacks(stackBuilder);
      }

      ResultSet threadResult = executeQuery(QUERY_THREAD_INFO_BY_TIME, session.getSessionId(), startTime, endTime);
      while (threadResult.next()) {
        ThreadInfo thread =
          ThreadInfo.newBuilder().setThreadId(threadResult.getInt(1)).setThreadName(threadResult.getString(2)).build();
        resultBuilder.addAllocationThreads(thread);
      }

      resultBuilder.setTimestamp(timestamp);
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      getLogger().error(ex);
    }

    return resultBuilder.build();
  }

  private JNIGlobalReferenceEvent readJniEventFromResultSet(ResultSet resultset, JNIGlobalReferenceEvent.Type type) throws SQLException {
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
    if (backtrace != null) {
      try {
        event.setBacktrace(NativeBacktrace.parseFrom(backtrace));
      }
      catch (InvalidProtocolBufferException ex) {
        getLogger().error(ex);
      }
    }
    return event.build();
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
      getLogger().error(ex);
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
      getLogger().error(ex);
    }
    return resultBuilder.build();
  }

  public void insertJniReferenceData(@NotNull Common.Session session, @NotNull BatchJNIGlobalRefEvent batch) {
    PreparedStatement insertRefStatement = null;
    PreparedStatement updateRefStatement = null;
    PreparedStatement insertStackStatement = null;
    try {
      for (JNIGlobalReferenceEvent event : batch.getEventsList()) {
        long refValue = event.getRefValue();
        int objectTag = event.getObjectTag();
        long timestamp = event.getTimestamp();
        int threadId = event.getThreadId();
        String stackHash = "";
        if (event.hasBacktrace()) {
          byte[] backtrace = event.getBacktrace().toByteArray();
          stackHash = DigestUtils.md5Hex(backtrace);
          if (insertStackStatement == null) {
            insertStackStatement = getStatementMap().get(INSERT_NATIVE_STACK);
          }
          applyParams(insertStackStatement, session.getSessionId(), stackHash, backtrace);
          insertStackStatement.addBatch();
        }
        switch (event.getEventType()) {
          case CREATE_GLOBAL_REF:
            if (insertRefStatement == null) {
              insertRefStatement = getStatementMap().get(INSERT_JNI_REF);
            }
            applyParams(insertRefStatement, session.getSessionId(), objectTag, refValue, timestamp, threadId, stackHash);
            insertRefStatement.addBatch();
            break;
          case DELETE_GLOBAL_REF:
            if (updateRefStatement == null) {
              updateRefStatement = getStatementMap().get(UPDATE_JNI_REF);
            }
            applyParams(updateRefStatement, timestamp, stackHash, threadId, session.getSessionId(), objectTag, refValue);
            updateRefStatement.addBatch();
            break;
          default:
            assert false;
        }
      }

      if (insertStackStatement != null) {
        insertStackStatement.executeBatch();
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
      getLogger().error(ex);
    }
  }

  public void insertAllocationData(Common.Session session, MemoryProfiler.BatchAllocationSample sample) {
    MemoryProfiler.AllocationEvent.EventCase currentCase = null;
    PreparedStatement currentStatement = null;
    int allocAndFreeCount = 0;
    try {
      for (MemoryProfiler.AllocationEvent event : sample.getEventsList()) {
        if (currentCase != event.getEventCase()) {
          if (currentCase != null) {
            currentStatement.executeBatch();
          }

          currentCase = event.getEventCase();
          switch (currentCase) {
            case CLASS_DATA:
              currentStatement = getStatementMap().get(INSERT_CLASS);
              break;
            case ALLOC_DATA:
              currentStatement = getStatementMap().get(INSERT_ALLOC);
              break;
            case FREE_DATA:
              currentStatement = getStatementMap().get(UPDATE_ALLOC);
              break;
            default:
              assert false;
          }
        }

        switch (currentCase) {
          case CLASS_DATA:
            assert currentStatement != null;
            AllocatedClass klass = event.getClassData();
            applyParams(currentStatement, session.getSessionId(), klass.getClassId(), event.getTimestamp(),
                        jniToJavaName(klass.getClassName()));
            break;
          case ALLOC_DATA:
            assert currentStatement != null;
            allocAndFreeCount++;
            AllocationEvent.Allocation allocation = event.getAllocData();
            applyParams(currentStatement, session.getSessionId(), allocation.getTag(), allocation.getClassTag(),
                        event.getTimestamp(), Long.MAX_VALUE, allocation.getSize(), allocation.getLength(), allocation.getThreadId(),
                        allocation.getStackId(), allocation.getHeapId());
            break;
          case FREE_DATA:
            assert currentStatement != null;
            allocAndFreeCount++;
            AllocationEvent.Deallocation free = event.getFreeData();
            applyParams(currentStatement, event.getTimestamp(), session.getSessionId(), free.getTag());
            break;
          default:
            assert false;
        }
        currentStatement.addBatch();
      }

      // Handles last batch after exiting from for-loop.
      currentStatement.executeBatch();

      if (allocAndFreeCount > 0) {
        pruneAllocations(session);
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public void insertMethodInfo(Common.Session session, List<AllocationStack.StackFrame> methods) {
    try {
      PreparedStatement statement = getStatementMap().get(INSERT_METHOD);
      assert statement != null;
      for (AllocationStack.StackFrame method : methods) {
        applyParams(statement, session.getSessionId(), method.getMethodId(), method.getMethodName(), jniToJavaName(method.getClassName()));
        statement.addBatch();
      }
      statement.executeBatch();
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @NotNull
  public StackFrameInfoResponse getStackFrameInfo(Common.Session session, long methodId) {
    StackFrameInfoResponse.Builder methodBuilder = StackFrameInfoResponse.newBuilder();
    try {
      ResultSet result = executeQuery(QUERY_METHOD_INFO, session.getSessionId(), methodId);
      if (result.next()) {
        methodBuilder.setMethodName(result.getString(1)).setClassName(result.getString(2));
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }

    return methodBuilder.build();
  }

  public void insertStackInfo(Common.Session session, List<EncodedAllocationStack> stacks) {
    try {
      PreparedStatement statement = getStatementMap().get(INSERT_ENCODED_STACK);
      assert statement != null;
      for (EncodedAllocationStack stack : stacks) {
        applyParams(statement, session.getSessionId(), stack.getStackId(), stack.getTimestamp(), stack.toByteArray());
        statement.addBatch();
      }
      statement.executeBatch();
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public void insertThreadInfo(Common.Session session, List<ThreadInfo> threads) {
    try {
      PreparedStatement statement = getStatementMap().get(INSERT_THREAD_INFO);
      assert statement != null;
      for (ThreadInfo thread : threads) {
        applyParams(statement, session.getSessionId(), thread.getThreadId(), thread.getTimestamp(), thread.getThreadName());
        statement.addBatch();
      }
      statement.executeBatch();
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  /**
   * Removes entries from the allocations table so the process (in-memory DB) doesn't run out of memory.
   */
  private void pruneAllocations(@NotNull Common.Session session) {
    try {
      // TODO save data to disk
      ResultSet result = executeQuery(COUNT_ALLOC);
      result.next();
      int rowCount = result.getInt(1);
      if (rowCount > myAllocationCountLimit) {
        int pruneCount = rowCount - myAllocationCountLimit;
        execute(PRUNE_ALLOC, session.getSessionId(), session.getSessionId(), pruneCount);
        getLogger().info(String.format("Allocations have exceed %d entries. Attempting to prune %d.", myAllocationCountLimit, pruneCount));
      }
    }
    catch (SQLException e) {
      getLogger().error(e);
    }
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
          .info(String.format("JNI ref records have exceed %d entries. Attempting to prune %d.", myAllocationCountLimit, pruneCount));
      }
    }
    catch (SQLException e) {
      getLogger().error(e);
    }
  }

  /**
   * Converts jni class names into java names
   * e.g. Ljava/lang/String; -> java.lang.String
   * e.g. [[Ljava/lang/Object; -> java.lang.Object[][]
   *
   * JNI primitive type names are converted too
   * e.g. Z -> boolean
   */
  private static String jniToJavaName(String jniName) {
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
