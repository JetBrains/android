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
      "INSERT OR IGNORE INTO Memory_AllocationEvents (Pid, Session, Tag, ClassTag, AllocTime, FreeTime, Size, Length, StackId) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"),
    INSERT_METHOD("INSERT OR IGNORE  INTO Memory_MethodInfos (Pid, Session, MethodId, MethodName, ClassName) VALUES (?, ?, ?, ?, ?)"),
    INSERT_ENCODED_STACK("INSERT OR IGNORE  INTO Memory_StackInfos (Pid, Session, StackId, AllocTime, MethodIdData) VALUES (?, ?, ?, ?, ?)"),
    UPDATE_ALLOC(
      "UPDATE Memory_AllocationEvents SET FreeTime = ? WHERE Pid = ? AND Session = ? AND Tag = ?"),
    QUERY_CLASS(
      "SELECT Tag, AllocTime, Name FROM Memory_AllocatedClass where Pid = ? AND Session = ? AND AllocTime >= ? AND AllocTime < ?"),
    QUERY_ALLOC_BY_ALLOC_TIME("SELECT Tag, ClassTag, AllocTime, FreeTime, Size, Length, StackId FROM Memory_AllocationEvents " +
                              "WHERE Pid = ? AND Session = ? AND AllocTime >= ? AND AllocTime < ?"),
    QUERY_ALLOC_BY_FREE_TIME("SELECT Tag, ClassTag, AllocTime, FreeTime, Size, Length FROM Memory_AllocationEvents " +
                             "WHERE Pid = ? AND Session = ? AND FreeTime >= ? AND FreeTime < ?"),
    QUERY_METHOD_INFO("Select MethodName, ClassName FROM Memory_MethodInfos WHERE Pid = ? AND Session = ? AND MethodId = ?"),
    QUERY_ENCODED_STACK_INFO_BY_TIME(
      "Select MethodIdData FROM Memory_StackInfos WHERE Pid = ? AND Session = ? AND AllocTime >= ? AND AllocTime < ?"),

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

  private int myAllocationCountLimit = 500000; // 500k ought to be enough for anybody (~30MB of data)

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
                  "ClassTag INTEGER", "AllocTime INTEGER", "FreeTime INTEGER", "Size INTEGER", "Length INTEGER", "StackId INTEGER",
                  "PRIMARY KEY(Pid, Session, Tag)");
      createTable("Memory_MethodInfos", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "MethodId INTEGER",
                  "MethodName TEXT", "ClassName TEXT", "PRIMARY KEY(Pid, Session, MethodId)");
      createTable("Memory_StackInfos", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "StackId INTEGER", "AllocTime INTEGER",
                  "MethodIdData BLOB", "PRIMARY KEY(Pid, Session, StackId)");
      createIndex("Memory_AllocationEvents", 0, "Pid", "Session", "AllocTime");
      createIndex("Memory_AllocationEvents", 1, "Pid", "Session", "FreeTime");
      createIndex("Memory_AllocatedClass", 0, "Pid", "Session", "AllocTime");
      createIndex("Memory_StackInfos", 0, "Pid", "Session", "AllocTime");
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
              MemoryProfiler.AllocationEvent.Allocation.newBuilder().setTag(allocResult.getLong(1)).setClassTag(allocResult.getLong(2))
                .setSize(allocResult.getLong(5)).setLength(allocResult.getInt(6)).setStackId(allocResult.getInt(7)).build())
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
              MemoryProfiler.AllocationEvent.Deallocation.newBuilder().setTag(freeResult.getLong(1)).setClassTag(freeResult.getLong(2))
                .setSize(freeResult.getLong(5)).setLength(freeResult.getInt(6)).build())
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
          AllocatedClass.newBuilder().setClassId(klassResult.getLong(1)).setClassName(klassResult.getString(3)).build();
        resultBuilder.addAllocatedClasses(klass);
        timestamp = Math.max(timestamp, allocTime);
      }

      ResultSet stackResult = executeQuery(QUERY_ENCODED_STACK_INFO_BY_TIME, pid, session, startTime, endTime);
      while (stackResult.next()) {
        AllocationStack.Builder stackBuilder = AllocationStack.newBuilder();

        // First retrieve the encoded allocation stack proto.
        // Note that we are not accounting for the timestamp recorded in the stack, as stack entries from each batched allocation sample
        // are inserted first into the database. So class data with an earlier timestamp can be inserted later.
        EncodedAllocationStack encodedStack = EncodedAllocationStack.parseFrom(stackResult.getBytes(1));

        // Then retrieve the full method info for each method id in the encoded stack.
        // TODO: The current SQLite JDBC driver in IJ does not support the createArrayOf operation which prevents this to do a bulk
        // select and only hit the database once. Alternative is ugly (e.g. manually formatting a string and pass to the IN clause).
        // We can revisit at a later time.
        stackBuilder.setStackId(encodedStack.getStackId());
        for (long methodId : encodedStack.getMethodIdsList()) {
          stackBuilder.addStackFrames(queryMethodInfo(pid, session, methodId));
        }

        resultBuilder.addAllocationStacks(stackBuilder);
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
                        event.getTimestamp(), Long.MAX_VALUE, allocation.getSize(), allocation.getLength(), allocation.getStackId());
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
  AllocationStack.StackFrame queryMethodInfo(int pid, Common.Session session, long methodId) {
    AllocationStack.StackFrame.Builder methodBuilder = AllocationStack.StackFrame.newBuilder();
    try {
      ResultSet result = executeQuery(QUERY_METHOD_INFO, pid, session, methodId);
      if (result.next()) {
        methodBuilder.setMethodId(methodId).setMethodName(result.getString(1)).setClassName(result.getString(2));
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
        execute(PRUNE_ALLOC, pid, session, pid, session, rowCount - myAllocationCountLimit);
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
   * TODO convert primitive types too?
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
    }

    while (arrayDimension > 0) {
      javaName += "[]";
      arrayDimension--;
    }
    return javaName;
  }
}
