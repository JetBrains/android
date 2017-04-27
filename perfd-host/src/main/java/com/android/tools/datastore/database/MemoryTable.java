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
package com.android.tools.datastore.database;

import com.android.tools.adtui.model.DurationData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.google.protobuf3jarjar.ByteString;
import com.google.protobuf3jarjar.GeneratedMessageV3;
import com.google.protobuf3jarjar.InvalidProtocolBufferException;
import com.google.protobuf3jarjar.Message;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.datastore.database.MemoryTable.MemoryStatements.*;

public class MemoryTable extends DatastoreTable<MemoryTable.MemoryStatements> {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryTable.class);
  }

  public enum MemoryStatements {
    // TODO: during process switch the initial start request time is reset in the poller, which would lead to duplicated data
    // being inserted if the user switches back and forth within the same app. For now, we ignore the duplicates but we can
    // preset the start time based on existing data to avoid dups altogether.
    INSERT_SAMPLE("INSERT OR IGNORE INTO Memory_Samples (Pid, Session, Timestamp, Type, Data) VALUES (?, ?, ?, ?, ?)"),
    QUERY_MEMORY(
      String.format("SELECT Data FROM Memory_Samples WHERE Pid = ? AND Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                    MemorySamplesType.MEMORY.ordinal())),
    QUERY_ALLOC_STATS(
      String.format("SELECT Data FROM Memory_Samples WHERE Pid = ? AND Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                    MemorySamplesType.ALLOC_STATS.ordinal())),

    // TODO: gc stats are duration data so we should account for end time. In reality this is usually sub-ms so it might not matter?
    QUERY_GC_STATS(
      String.format("SELECT Data FROM Memory_Samples WHERE Pid = ? AND Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                    MemorySamplesType.GC_STATS.ordinal())),

    INSERT_OR_REPLACE_HEAP_INFO(
      "INSERT OR REPLACE INTO Memory_HeapDump (Pid, Session, StartTime, EndTime, Status, InfoData) VALUES (?, ?, ?, ?, ?, ?)"),
    UPDATE_HEAP_DUMP("UPDATE Memory_HeapDump SET DumpData = ?, Status = ? WHERE Pid = ? AND Session = ? AND StartTime = ?"),
    // EndTime = UNSPECIFIED_DURATION checks for the special case where we have an ongoing duration sample
    QUERY_HEAP_INFO_BY_TIME(String.format(
      "SELECT InfoData FROM Memory_HeapDump where Pid = ? AND Session = ? AND (EndTime = %d OR EndTime > ?) AND StartTime <= ?",
      DurationData.UNSPECIFIED_DURATION)),
    QUERY_HEAP_DUMP_BY_ID("SELECT DumpData FROM Memory_HeapDump where Pid = ? AND Session = ? AND StartTime = ?"),
    QUERY_HEAP_STATUS_BY_ID("SELECT Status FROM Memory_HeapDump where Pid = ? AND Session = ? AND StartTime = ?"),

    INSERT_OR_REPLACE_ALLOCATIONS_INFO(
      "INSERT OR REPLACE INTO Memory_AllocationInfo (Pid, Session, StartTime, EndTime, InfoData) VALUES (?, ?, ?, ?, ?)"),
    UPDATE_LEGACY_ALLOCATIONS_INFO_EVENTS(
      "UPDATE Memory_AllocationInfo SET LegacyEventsData = ? WHERE Pid = ? AND Session = ? AND StartTime = ?"),
    UPDATE_LEGACY_ALLOCATIONS_INFO_DUMP(
      "UPDATE Memory_AllocationInfo SET LegacyDumpData = ? WHERE Pid = ? AND Session = ? AND StartTime = ?"),
    // EndTime = UNSPECIFIED_DURATION checks for the special case where we have an ongoing duration sample
    QUERY_ALLOCATION_INFO_BY_TIME(String.format(
      "SELECT InfoData FROM Memory_AllocationInfo WHERE Pid = ? AND Session = ? AND (EndTime = %d OR EndTime > ?) AND StartTime <= ?",
      DurationData.UNSPECIFIED_DURATION)),
    QUERY_ALLOCATION_INFO_BY_ID("SELECT InfoData from Memory_AllocationInfo WHERE Pid = ? AND Session = ? AND StartTime = ?"),
    QUERY_LEGACY_ALLOCATION_EVENTS_BY_ID(
      "SELECT LegacyEventsData from Memory_AllocationInfo WHERE Pid = ? AND Session = ? AND StartTime = ?"),
    QUERY_LEGACY_ALLOCATION_DUMP_BY_ID("SELECT LegacyDumpData from Memory_AllocationInfo WHERE Pid = ? AND Session = ? AND StartTime = ?"),

    INSERT_LEGACY_ALLOCATION_STACK("INSERT OR IGNORE INTO Memory_LegacyAllocationStack (Id, Data) VALUES (?, ?)"),
    INSERT_LEGACY_ALLOCATED_CLASS("INSERT OR IGNORE INTO Memory_LegacyAllocatedClass (Id, Data) VALUES (?, ?)"),
    QUERY_LEGACY_ALLOCATION_STACK("Select Data FROM Memory_LegacyAllocationStack WHERE Id = ?"),
    QUERY_LEGACY_ALLOCATED_CLASS("Select Data FROM Memory_LegacyAllocatedClass WHERE Id = ?"),

    // O+ Allocation Tracking
    INSERT_CLASS("INSERT OR IGNORE INTO Memory_AllocatedClass (Pid, Session, CaptureTime, Tag, AllocTime, Name) VALUES (?, ?, ?, ?, ?, ?)"),
    INSERT_ALLOC(
      "INSERT INTO Memory_AllocationEvents (Pid, Session, CaptureTime, Tag, ClassTag, AllocTime, FreeTime, Size, Length) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"),
    UPDATE_ALLOC(
      "UPDATE Memory_AllocationEvents SET FreeTime = ? WHERE Pid = ? AND Session = ? AND CaptureTime = ? AND Tag = ?"),
    QUERY_CLASS("SELECT Tag, AllocTime, Name FROM Memory_AllocatedClass where Pid = ? AND Session = ? AND CaptureTime = ?"),
    // XORing (AllocTime >= startTime AND AllocTime < endTime) and (FreeTime >= startTime AND FreeTime < endTime)
    QUERY_ALLOC_SNAPSHOT("SELECT Tag, ClassTag, AllocTime, FreeTime, Size, Length FROM Memory_AllocationEvents " +
                         "WHERE Pid = ? AND Session = ? AND CaptureTime = ? AND AllocTime < ? AND FreeTime >= ? AND " +
                         "((AllocTime >= ? OR FreeTime < ?) AND " +
                         "NOT (AllocTime >= ? AND FreeTime < ?))");

    @NotNull private final String mySqlStatement;

    MemoryStatements(@NotNull String sqlStatement) {
      mySqlStatement = sqlStatement;
    }

    @NotNull
    public String getStatement() {
      return mySqlStatement;
    }
  }

  private enum MemorySamplesType {
    MEMORY,
    ALLOC_STATS,
    GC_STATS
  }

  @Override
  public void initialize(Connection connection) {
    super.initialize(connection);
    try {
      createTable("Memory_Samples", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "Timestamp INTEGER", "Type INTEGER",
                  "Data BLOB", "PRIMARY KEY(Pid, Session, Timestamp, Type)");
      createTable("Memory_AllocationInfo", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "StartTime INTEGER",
                  "EndTime INTEGER", "InfoData BLOB", "LegacyEventsData BLOB", "LegacyDumpData BLOB",
                  "PRIMARY KEY(Pid, Session, StartTime)");
      createTable("Memory_LegacyAllocationStack", "Id BLOB", "Data BLOB", "PRIMARY KEY(Id)");
      createTable("Memory_LegacyAllocatedClass", "Id INTEGER", "Data BLOB", "PRIMARY KEY(Id)");
      createTable("Memory_HeapDump", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "StartTime INTEGER",
                  "EndTime INTEGER", "Status INTEGER", "InfoData BLOB", "DumpData BLOB", "PRIMARY KEY(Pid, Session, StartTime)");

      // O+ Allocation Tracking
      createTable("Memory_AllocatedClass", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "CaptureTime INTEGER",
                  "Tag INTEGER", "AllocTime INTEGER", "Name TEXT",
                  "PRIMARY KEY(Pid, Session, CaptureTime, Tag)");
      createTable("Memory_AllocationEvents", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "CaptureTime INTEGER",
                  "Tag INTEGER", "ClassTag INTEGER", "AllocTime INTEGER", "FreeTime INTERGER", "Size INTEGER", "Length INTEGER",
                  "PRIMARY KEY(Pid, Session, CaptureTime, Tag)");
      createIndex("Memory_AllocationEvents", "Pid", "Session", "CaptureTime", "AllocTime", "FreeTime");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements(Connection connection) {
    try {
      MemoryStatements[] statements = values();
      for (int i = 0; i < statements.length; i++) {
        createStatement(statements[i], statements[i].getStatement());
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @NotNull
  public MemoryData getData(MemoryRequest request) {
    int pid = request.getProcessId();
    long startTime = request.getStartTime();
    long endTime = request.getEndTime();
    List<MemoryData.MemorySample> memorySamples =
      getResultsInfo(QUERY_MEMORY, pid, request.getSession(), startTime, endTime,
                     MemoryData.MemorySample.getDefaultInstance());
    List<MemoryData.AllocStatsSample> allocStatsSamples =
      getResultsInfo(QUERY_ALLOC_STATS, pid, request.getSession(), startTime, endTime,
                     MemoryData.AllocStatsSample.getDefaultInstance());
    List<MemoryData.GcStatsSample> gcStatsSamples =
      getResultsInfo(QUERY_GC_STATS, pid, request.getSession(), startTime, endTime,
                     MemoryData.GcStatsSample.getDefaultInstance());
    List<HeapDumpInfo> heapDumpSamples =
      getResultsInfo(QUERY_HEAP_INFO_BY_TIME, pid, request.getSession(), startTime, endTime,
                     HeapDumpInfo.getDefaultInstance());
    List<AllocationsInfo> allocationSamples =
      getResultsInfo(QUERY_ALLOCATION_INFO_BY_TIME, pid, request.getSession(), startTime, endTime,
                     AllocationsInfo.getDefaultInstance());
    MemoryData.Builder response = MemoryData.newBuilder()
      .addAllMemSamples(memorySamples)
      .addAllAllocStatsSamples(allocStatsSamples)
      .addAllGcStatsSamples(gcStatsSamples)
      .addAllHeapDumpInfos(heapDumpSamples)
      .addAllAllocationsInfo(allocationSamples);
    return response.build();
  }

  public void insertMemory(int pid, Common.Session session, List<MemoryData.MemorySample> samples) {
    for (MemoryData.MemorySample sample : samples) {
      execute(INSERT_SAMPLE, pid, session, sample.getTimestamp(), MemorySamplesType.MEMORY.ordinal(),
              sample.toByteArray());
    }
  }

  public void insertAllocStats(int pid, Common.Session session, List<MemoryData.AllocStatsSample> samples) {
    for (MemoryData.AllocStatsSample sample : samples) {
      execute(INSERT_SAMPLE, pid, session, sample.getTimestamp(), MemorySamplesType.ALLOC_STATS.ordinal(),
              sample.toByteArray());
    }
  }

  public void insertGcStats(int pid, Common.Session session, List<MemoryData.GcStatsSample> samples) {
    for (MemoryData.GcStatsSample sample : samples) {
      execute(INSERT_SAMPLE, pid, session, sample.getStartTime(), MemorySamplesType.GC_STATS.ordinal(),
              sample.toByteArray());
    }
  }

  /**
   * Note: this will reset the row's Status and DumpData to NOT_READY and null respectively, if an info with the same DumpId already exist.
   */
  public void insertOrReplaceHeapInfo(int pid, Common.Session session, HeapDumpInfo info) {
    execute(INSERT_OR_REPLACE_HEAP_INFO, pid, session, info.getStartTime(), info.getEndTime(),
            DumpDataResponse.Status.NOT_READY.ordinal(), info.toByteArray());
  }

  /**
   * @return the dump status corresponding to a particular dump. If the entry does not exist, NOT_FOUND is returned.
   */
  public DumpDataResponse.Status getHeapDumpStatus(int pid, Common.Session session, long dumpTime) {
    try {
      ResultSet result = executeQuery(QUERY_HEAP_STATUS_BY_ID, pid, session, dumpTime);
      if (result.next()) {
        return DumpDataResponse.Status.forNumber(result.getInt(1));
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return DumpDataResponse.Status.NOT_FOUND;
  }


  public List<HeapDumpInfo> getHeapDumpInfoByRequest(int pid, Common.Session session, ListDumpInfosRequest request) {
    return getResultsInfo(QUERY_HEAP_INFO_BY_TIME, pid, session, request.getStartTime(), request.getEndTime(),
                          HeapDumpInfo.getDefaultInstance());
  }

  /**
   * Adds/updates the status and raw dump data associated with a dump sample's id.
   */
  public void insertHeapDumpData(int pid, Common.Session session, long dumpTime, DumpDataResponse.Status status, ByteString data) {
    execute(UPDATE_HEAP_DUMP, data.toByteArray(), status.getNumber(), pid, session, dumpTime);
  }

  /**
   * @return the raw dump byte content assocaited with a dump time. Null if an entry does not exist in the database.
   */
  @Nullable
  public byte[] getHeapDumpData(int pid, Common.Session session, long dumpTime) {
    try {
      ResultSet resultSet = executeQuery(QUERY_HEAP_DUMP_BY_ID, pid, session, dumpTime);
      if (resultSet.next()) {
        return resultSet.getBytes(1);
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }


  /**
   * Note: this will reset the allocation events and its raw dump byte content associated with a tracking start time if an entry already exists.
   */
  public void insertOrReplaceAllocationsInfo(int pid, Common.Session session, AllocationsInfo info) {
    execute(INSERT_OR_REPLACE_ALLOCATIONS_INFO, pid, session, info.getStartTime(), info.getEndTime(), info.toByteArray());
  }

  public void updateLegacyAllocationEvents(int pid,
                                           Common.Session session,
                                           long trackingStartTime,
                                           @NotNull LegacyAllocationEventsResponse allocationData) {
    execute(UPDATE_LEGACY_ALLOCATIONS_INFO_EVENTS, allocationData.toByteArray(), pid, session, trackingStartTime);
  }


  public void updateLegacyAllocationDump(int pid, Common.Session session, long trackingStartTime, byte[] data) {

    execute(UPDATE_LEGACY_ALLOCATIONS_INFO_DUMP, data, pid, session, trackingStartTime);
  }

  /**
   * @return the AllocationsInfo associated with the tracking start time. Null if an entry does not exist.
   */
  @Nullable
  public AllocationsInfo getAllocationsInfo(int pid, Common.Session session, long trackingStartTime) {
    try {
      ResultSet results = executeQuery(QUERY_ALLOCATION_INFO_BY_ID, pid, session, trackingStartTime);
      if (results.next()) {
        byte[] bytes = results.getBytes(1);
        if (bytes != null) {
          return AllocationsInfo.parseFrom(bytes);
        }
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }

    return null;
  }


  /**
   * @return the AllocationEventsResponse associated with the tracking start time. Null if an entry does not exist.
   */
  @Nullable
  public LegacyAllocationEventsResponse getLegacyAllocationData(int pid, Common.Session session, long trackingStartTime) {

    try {
      ResultSet resultSet = executeQuery(QUERY_LEGACY_ALLOCATION_EVENTS_BY_ID, pid, session, trackingStartTime);
      if (resultSet.next()) {
        byte[] bytes = resultSet.getBytes(1);
        if (bytes != null) {
          return LegacyAllocationEventsResponse.parseFrom(resultSet.getBytes(1));
        }
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  /**
   * @return the raw legacy allocation tracking byte data associated with the tracking start time. Null if an entry does not exist.
   */
  @Nullable
  public byte[] getLegacyAllocationDumpData(int pid, Common.Session session, long trackingStartTime) {

    try {
      ResultSet resultSet = executeQuery(QUERY_LEGACY_ALLOCATION_DUMP_BY_ID, pid, session, trackingStartTime);
      if (resultSet.next()) {
        return resultSet.getBytes(1);
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  public void insertLegacyAllocationContext(@NotNull List<AllocatedClass> classes, @NotNull List<AllocationStack> stacks) {

    // TODO: batch insert
    classes.forEach(klass -> execute(INSERT_LEGACY_ALLOCATED_CLASS, klass.getClassId(), klass.toByteArray()));
    stacks
      .forEach(stack -> execute(INSERT_LEGACY_ALLOCATION_STACK, stack.getStackId().toByteArray(), stack.toByteArray()));
  }


  public LegacyAllocationContextsResponse listAllocationContexts(@NotNull LegacyAllocationContextsRequest request) {

    LegacyAllocationContextsResponse.Builder builder = LegacyAllocationContextsResponse.newBuilder();
    // TODO optimize queries
    try {
      for (int i = 0; i < request.getClassIdsCount(); i++) {
        ResultSet classResultSet = executeQuery(QUERY_LEGACY_ALLOCATED_CLASS, request.getClassIds(i));
        if (classResultSet.next()) {
          AllocatedClass data = AllocatedClass.newBuilder().mergeFrom(classResultSet.getBytes(1)).build();
          builder.addAllocatedClasses(data);
        }
      }

      for (int i = 0; i < request.getStackIdsCount(); i++) {
        ResultSet stackResultSet = executeQuery(QUERY_LEGACY_ALLOCATION_STACK, request.getStackIds(i).toByteArray());
        if (stackResultSet.next()) {
          AllocationStack data = AllocationStack.newBuilder().mergeFrom(stackResultSet.getBytes(1)).build();
          builder.addAllocationStacks(data);
        }
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return builder.build();
  }

  public BatchAllocationSample getAllocationSnapshot(int pid,
                                                     Common.Session session,
                                                     long captureTime,
                                                     long startTime,
                                                     long endTime) {
    BatchAllocationSample.Builder sampleBuilder = BatchAllocationSample.newBuilder();
    try {
      // Query all the classes
      // TODO: only return classes that are valid for current snapshot?
      ResultSet klassResult = executeQuery(QUERY_CLASS, pid, session, captureTime);
      while (klassResult.next()) {
        long allocTime = klassResult.getLong(2);
        AllocationEvent event = AllocationEvent.newBuilder()
          .setClassData(AllocationEvent.Klass.newBuilder().setTag(klassResult.getLong(1)).setName(klassResult.getString(3)))
          .setTimestamp(allocTime).build();
        sampleBuilder.addEvents(event);
      }

      // Then get all allocation events that are valid for requestTime.
      ResultSet allocResult =
        executeQuery(QUERY_ALLOC_SNAPSHOT, pid, session, captureTime, endTime, startTime, startTime, endTime, startTime, endTime);
      while (allocResult.next()) {
        long allocTime = allocResult.getLong(3);
        long freeTime = allocResult.getLong(4);
        AllocationEvent event = AllocationEvent.newBuilder()
          .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(allocResult.getLong(1)).setClassTag(allocResult.getLong(2))
                          .setFreeTimestamp(freeTime).setSize(allocResult.getLong(5)).setLength(allocResult.getInt(6)).build())
          .setTimestamp(allocTime).build();
        sampleBuilder.addEvents(event);
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }

    return sampleBuilder.build();
  }

  public void insertAllocationData(int pid, Common.Session session, BatchAllocationSample sample) {
    AllocationEvent.EventCase currentCase = null;
    PreparedStatement currentStatement = null;
    try {
      for (AllocationEvent event : sample.getEventsList()) {
        if (currentCase != event.getEventCase()) {
          if (currentCase != null) {
            assert currentStatement != null;
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
            AllocationEvent.Klass klass = event.getClassData();
            applyParams(currentStatement, pid, session, event.getCaptureTime(), klass.getTag(), event.getTimestamp(),
                        jniToJavaName(klass.getName()));
            break;
          case ALLOC_DATA:
            AllocationEvent.Allocation allocation = event.getAllocData();
            applyParams(currentStatement, pid, session, event.getCaptureTime(), allocation.getTag(), allocation.getClassTag(),
                        event.getTimestamp(), Long.MAX_VALUE, allocation.getSize(), allocation.getLength());
            break;
          case FREE_DATA:
            AllocationEvent.Deallocation free = event.getFreeData();
            applyParams(currentStatement, event.getTimestamp(), pid, session, event.getCaptureTime(), free.getTag());
            break;
          default:
            assert false;
        }
        currentStatement.addBatch();
      }

      // Handles last batch after exiting from for-loop.
      currentStatement.executeBatch();
    }
    catch (SQLException ex) {
      getLogger().error(ex);
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

  /**
   * A helper method for querying samples for MemorySample, AllocStatsSample, GcStatsSample, HeapDumpInfo and AllocationsInfo
   */
  private <T extends GeneratedMessageV3> List<T> getResultsInfo(MemoryStatements query,
                                                                int pid,
                                                                Common.Session session,
                                                                long startTime,
                                                                long endTime,
                                                                T defaultInstance) {
    List<T> datas = new ArrayList<>();
    try {
      ResultSet resultSet = executeQuery(query, pid, session, startTime, endTime);
      while (resultSet.next()) {
        Message data = defaultInstance.toBuilder().mergeFrom(resultSet.getBytes(1)).build();
        datas.add((T)data);
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return datas;
  }
}
