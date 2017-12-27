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
import com.google.protobuf3jarjar.InvalidProtocolBufferException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static com.android.tools.datastore.database.MemoryLiveAllocationTable.MemoryStatements.*;

public class MemoryLiveAllocationTable extends DataStoreTable<MemoryLiveAllocationTable.MemoryStatements> {
  public enum MemoryStatements {
    // O+ Allocation Tracking
    INSERT_CLASS("INSERT OR IGNORE INTO Memory_AllocatedClass (Pid, Session, Tag, AllocTime, Name) VALUES (?, ?, ?, ?, ?)"),
    INSERT_ALLOC(
      "INSERT OR IGNORE INTO Memory_AllocationEvents " +
      "(Pid, Session, Tag, ClassTag, AllocTime, FreeTime, Size, Length, ThreadId, StackId, HeapId) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
    INSERT_METHOD("INSERT OR IGNORE INTO Memory_MethodInfos (Pid, Session, MethodId, MethodName, ClassName) VALUES (?, ?, ?, ?, ?)"),
    INSERT_ENCODED_STACK("INSERT OR IGNORE INTO Memory_StackInfos (Pid, Session, StackId, AllocTime, StackData) VALUES (?, ?, ?, ?, ?)"),
    INSERT_THREAD_INFO("INSERT OR IGNORE INTO Memory_ThreadInfos (Pid, Session, ThreadId, AllocTime, ThreadName) VALUES (?, ?, ?, ?, ?)"),
    UPDATE_ALLOC(
      "UPDATE Memory_AllocationEvents SET FreeTime = ? WHERE Pid = ? AND Session = ? AND Tag = ?"),
    QUERY_CLASS(
      "SELECT Tag, AllocTime, Name FROM Memory_AllocatedClass where Pid = ? AND Session = ? AND AllocTime >= ? AND AllocTime < ?"),
    QUERY_ALLOC_BY_ALLOC_TIME(
      "SELECT Tag, ClassTag, AllocTime, FreeTime, Size, Length, ThreadId, StackId, HeapId FROM Memory_AllocationEvents " +
      "WHERE Pid = ? AND Session = ? AND AllocTime >= ? AND AllocTime < ?"),
    QUERY_ALLOC_BY_FREE_TIME(
      "SELECT Tag, ClassTag, AllocTime, FreeTime, Size, Length, ThreadId, StackId, HeapId FROM Memory_AllocationEvents " +
      "WHERE Pid = ? AND Session = ? AND FreeTime >= ? AND FreeTime < ?"),
    QUERY_METHOD_INFO("Select MethodName, ClassName FROM Memory_MethodInfos WHERE Pid = ? AND Session = ? AND MethodId = ?"),
    QUERY_ENCODED_STACK_INFO_BY_TIME(
      "Select StackData FROM Memory_StackInfos WHERE Pid = ? AND Session = ? AND AllocTime >= ? AND AllocTime < ?"),
    QUERY_THREAD_INFO_BY_TIME(
      "Select ThreadId, ThreadName FROM Memory_ThreadInfos WHERE Pid = ? AND Session = ? AND AllocTime >= ? AND AllocTime < ?"),

    COUNT_ALLOC("SELECT count(*) FROM Memory_AllocationEvents"),
    PRUNE_ALLOC("DELETE FROM Memory_AllocationEvents WHERE Pid = ? AND Session = ? AND FreeTime <= (" +
                " SELECT MAX(FreeTime)" +
                " FROM Memory_AllocationEvents" +
                " WHERE Pid = ? AND Session = ? AND FreeTime < " + Long.MAX_VALUE +
                " ORDER BY FreeTime" +
                " LIMIT ?" +
                ")"),;

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

  public MemoryLiveAllocationTable(@NotNull Map<Common.Session, Long> sesstionIdLookup) {
    super(sesstionIdLookup);
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      // O+ Allocation Tracking
      createTable("Memory_AllocatedClass", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "Tag INTEGER",
                  "AllocTime INTEGER", "Name TEXT", "PRIMARY KEY(Pid, Session, Tag)");
      createTable("Memory_AllocationEvents", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "Tag INTEGER",
                  "ClassTag INTEGER", "AllocTime INTEGER", "FreeTime INTEGER", "Size INTEGER", "Length INTEGER", "ThreadId INTEGER",
                  "StackId INTEGER", "HeapId INTEGER", "PRIMARY KEY(Pid, Session, Tag)");
      createTable("Memory_MethodInfos", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "MethodId INTEGER",
                  "MethodName TEXT", "ClassName TEXT", "PRIMARY KEY(Pid, Session, MethodId)");
      createTable("Memory_StackInfos", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "StackId INTEGER", "AllocTime INTEGER",
                  "StackData BLOB", "PRIMARY KEY(Pid, Session, StackId)");
      createTable("Memory_ThreadInfos", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "ThreadId INTEGER", "AllocTime INTEGER",
                  "ThreadName TEXT", "PRIMARY KEY(Pid, Session, ThreadId)");
      createIndex("Memory_AllocationEvents", 0, "Pid", "Session", "AllocTime");
      createIndex("Memory_AllocationEvents", 1, "Pid", "Session", "FreeTime");
      createIndex("Memory_AllocatedClass", 0, "Pid", "Session", "AllocTime");
      createIndex("Memory_StackInfos", 0, "Pid", "Session", "AllocTime");
      createIndex("Memory_ThreadInfos", 0, "Pid", "Session", "AllocTime");
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

  public MemoryProfiler.BatchAllocationSample getAllocations(int pid, Common.Session session, long startTime, long endTime) {
    MemoryProfiler.BatchAllocationSample.Builder sampleBuilder = MemoryProfiler.BatchAllocationSample.newBuilder();
    try {
      // Then get all allocation events that are valid for requestTime.
      ResultSet allocResult = executeQuery(QUERY_ALLOC_BY_ALLOC_TIME, pid, session, startTime, endTime);
      long timestamp = Long.MIN_VALUE;

      while (allocResult.next()) {
        long allocTime = allocResult.getLong(3);
        if (allocTime >= startTime) {
          MemoryProfiler.AllocationEvent event = MemoryProfiler.AllocationEvent.newBuilder()
            .setAllocData(
              MemoryProfiler.AllocationEvent.Allocation.newBuilder().setTag(allocResult.getInt(1)).setClassTag(allocResult.getInt(2))
                .setSize(allocResult.getLong(5)).setLength(allocResult.getInt(6)).setThreadId(allocResult.getInt(7))
                .setStackId(allocResult.getInt(8)).setHeapId(allocResult.getInt(9)).build())
            .setTimestamp(allocTime).build();
          sampleBuilder.addEvents(event);
          timestamp = Math.max(timestamp, allocTime);
        }
      }

      ResultSet freeResult = executeQuery(QUERY_ALLOC_BY_FREE_TIME, pid, session, startTime, endTime);
      while (freeResult.next()) {
        long freeTime = freeResult.getLong(4);
        if (freeTime < endTime) {
          MemoryProfiler.AllocationEvent event = MemoryProfiler.AllocationEvent.newBuilder()
            .setFreeData(
              MemoryProfiler.AllocationEvent.Deallocation.newBuilder().setTag(freeResult.getInt(1)).setClassTag(freeResult.getInt(2))
                .setSize(freeResult.getLong(5)).setLength(freeResult.getInt(6)).setThreadId(freeResult.getInt(7))
                .setStackId(freeResult.getInt(8)).setHeapId(freeResult.getInt(9)).build())
            .setTimestamp(freeTime).build();
          sampleBuilder.addEvents(event);
          timestamp = Math.max(timestamp, freeTime);
        }
      }

      sampleBuilder.setTimestamp(timestamp);
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }

    return sampleBuilder.build();
  }

  @NotNull
  public AllocationContextsResponse getAllocationContexts(int pid, Common.Session session, long startTime, long endTime) {
    AllocationContextsResponse.Builder resultBuilder = AllocationContextsResponse.newBuilder();
    try {
      // Query all the classes
      // TODO: only return classes that are valid for current snapshot?
      ResultSet klassResult = executeQuery(QUERY_CLASS, pid, session, startTime, endTime);
      long timestamp = Long.MIN_VALUE;

      while (klassResult.next()) {
        long allocTime = klassResult.getLong(2);
        AllocatedClass klass =
          AllocatedClass.newBuilder().setClassId(klassResult.getInt(1)).setClassName(klassResult.getString(3)).build();
        resultBuilder.addAllocatedClasses(klass);
        timestamp = Math.max(timestamp, allocTime);
      }

      ResultSet stackResult = executeQuery(QUERY_ENCODED_STACK_INFO_BY_TIME, pid, session, startTime, endTime);
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

      ResultSet threadResult = executeQuery(QUERY_THREAD_INFO_BY_TIME, pid, session, startTime, endTime);
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

  public void insertAllocationData(int pid, Common.Session session, MemoryProfiler.BatchAllocationSample sample) {
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
            applyParams(currentStatement, pid, session, klass.getClassId(), event.getTimestamp(), jniToJavaName(klass.getClassName()));
            break;
          case ALLOC_DATA:
            assert currentStatement != null;
            allocAndFreeCount++;
            AllocationEvent.Allocation allocation = event.getAllocData();
            applyParams(currentStatement, pid, session, allocation.getTag(), allocation.getClassTag(),
                        event.getTimestamp(), Long.MAX_VALUE, allocation.getSize(), allocation.getLength(), allocation.getThreadId(),
                        allocation.getStackId(), allocation.getHeapId());
            break;
          case FREE_DATA:
            assert currentStatement != null;
            allocAndFreeCount++;
            AllocationEvent.Deallocation free = event.getFreeData();
            applyParams(currentStatement, event.getTimestamp(), pid, session, free.getTag());
            break;
          default:
            assert false;
        }
        currentStatement.addBatch();
      }

      // Handles last batch after exiting from for-loop.
      currentStatement.executeBatch();

      if (allocAndFreeCount > 0) {
        pruneAllocations(pid, session);
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public void insertMethodInfo(int pid, Common.Session session, List<AllocationStack.StackFrame> methods) {
    try {
      PreparedStatement statement = getStatementMap().get(INSERT_METHOD);
      assert statement != null;
      for (AllocationStack.StackFrame method : methods) {
        applyParams(statement, pid, session, method.getMethodId(), method.getMethodName(), jniToJavaName(method.getClassName()));
        statement.addBatch();
      }
      statement.executeBatch();
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @NotNull
  public StackFrameInfoResponse getStackFrameInfo(int pid, Common.Session session, long methodId) {
    StackFrameInfoResponse.Builder methodBuilder = StackFrameInfoResponse.newBuilder();
    try {
      ResultSet result = executeQuery(QUERY_METHOD_INFO, pid, session, methodId);
      if (result.next()) {
        methodBuilder.setMethodName(result.getString(1)).setClassName(result.getString(2));
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }

    return methodBuilder.build();
  }

  public void insertStackInfo(int pid, Common.Session session, List<EncodedAllocationStack> stacks) {
    try {
      PreparedStatement statement = getStatementMap().get(INSERT_ENCODED_STACK);
      assert statement != null;
      for (EncodedAllocationStack stack : stacks) {
        applyParams(statement, pid, session, stack.getStackId(), stack.getTimestamp(), stack.toByteArray());
        statement.addBatch();
      }
      statement.executeBatch();
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public void insertThreadInfo(int pid, Common.Session session, List<ThreadInfo> threads) {
    try {
      PreparedStatement statement = getStatementMap().get(INSERT_THREAD_INFO);
      assert statement != null;
      for (ThreadInfo thread : threads) {
        applyParams(statement, pid, session, thread.getThreadId(), thread.getTimestamp(), thread.getThreadName());
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
  private void pruneAllocations(int pid, @NotNull Common.Session session) {
    try {
      // TODO save data to disk
      ResultSet result = executeQuery(COUNT_ALLOC);
      result.next();
      int rowCount = result.getInt(1);
      if (rowCount > myAllocationCountLimit) {
        int pruneCount = rowCount - myAllocationCountLimit;
        execute(PRUNE_ALLOC, pid, session, pid, session, pruneCount);
        getLogger().info(String.format("Allocations have exceed %d entries. Attempting to prune %d.", myAllocationCountLimit, pruneCount));
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
