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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MemoryTable extends DatastoreTable<MemoryTable.MemoryStatements> {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryTable.class);
  }

  public enum MemoryStatements {
    // TODO: during process switch the initial start request time is reset in the poller, which would lead to duplicated data
    // being inserted if the user switches back and forth within the same app. For now, we ignore the duplicates but we can
    // preset the start time based on existing data to avoid dups altogether.
    INSERT_SAMPLE("INSERT OR IGNORE INTO Memory_Samples (Pid, Session, Timestamp, Type, Data) VALUES (?, ?, ?, ?, ?)"),
    QUERY_MEMORY(String.format("SELECT Data FROM Memory_Samples WHERE Pid = ? AND Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                               MemorySamplesType.MEMORY.ordinal())),
    QUERY_VMSTATS(String.format("SELECT Data FROM Memory_Samples WHERE Pid = ? AND Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                                MemorySamplesType.VMSTATS.ordinal())),

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
    UPDATE_ALLOCATIONS_INFO_EVENTS("UPDATE Memory_AllocationInfo SET EventsData = ? WHERE Pid = ? AND Session = ? AND StartTime = ?"),
    UPDATE_ALLOCATIONS_INFO_DUMP("UPDATE Memory_AllocationInfo SET DumpData = ? WHERE Pid = ? AND Session = ? AND StartTime = ?"),
    // EndTime = UNSPECIFIED_DURATION checks for the special case where we have an ongoing duration sample
    QUERY_ALLOCATION_INFO_BY_TIME(String.format(
      "SELECT InfoData FROM Memory_AllocationInfo WHERE Pid = ? AND Session = ? AND (EndTime = %d OR EndTime > ?) AND StartTime <= ?",
      DurationData.UNSPECIFIED_DURATION)),
    QUERY_ALLOCATION_INFO_BY_ID("SELECT InfoData from Memory_AllocationInfo WHERE Pid = ? AND Session = ? AND StartTime = ?"),
    QUERY_ALLOCATION_EVENTS_BY_ID("SELECT EventsData from Memory_AllocationInfo WHERE Pid = ? AND Session = ? AND StartTime = ?"),
    QUERY_ALLOCATION_DUMP_BY_ID("SELECT DumpData from Memory_AllocationInfo WHERE Pid = ? AND Session = ? AND StartTime = ?"),

    INSERT_ALLOCATION_STACK("INSERT OR IGNORE INTO Memory_AllocationStack (Id, Data) VALUES (?, ?)"),
    INSERT_ALLOCATED_CLASS("INSERT OR IGNORE INTO Memory_AllocatedClass (Id, Data) VALUES (?, ?)"),
    QUERY_ALLOCATION_STACK("Select Data FROM Memory_AllocationStack WHERE Id = ?"),
    QUERY_ALLOCATED_CLASS("Select Data FROM Memory_AllocatedClass WHERE Id = ?");

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
    VMSTATS,
  }

  @Override
  public void initialize(Connection connection) {
    super.initialize(connection);
    try {
      createTable("Memory_Samples", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "Timestamp INTEGER", "Type INTEGER", "Data BLOB",
                  "PRIMARY KEY(Pid, Session, Timestamp, Type)");
      createTable("Memory_AllocationInfo", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "StartTime INTEGER", "EndTime INTEGER", "InfoData BLOB",
                  "EventsData BLOB", "DumpData BLOB", "PRIMARY KEY(Pid, Session, StartTime)");
      createTable("Memory_AllocationStack", "Id BLOB", "Data BLOB", "PRIMARY KEY(Id)");
      createTable("Memory_AllocatedClass", "Id INTEGER", "Data BLOB", "PRIMARY KEY(Id)");
      createTable("Memory_HeapDump", "Pid INTEGER NOT NULL", "Session INTEGER NOT NULL", "StartTime INTEGER", "EndTime INTEGER", "Status INTEGER",
                  "InfoData BLOB", "DumpData BLOB", "PRIMARY KEY(Pid, Session, StartTime)");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements(Connection connection) {
    try {
      MemoryStatements[] statements = MemoryStatements.values();
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
      getResultsInfo(MemoryStatements.QUERY_MEMORY, pid, request.getSession(), startTime, endTime, MemoryData.MemorySample.getDefaultInstance());
    List<MemoryData.VmStatsSample> vmStatsSamples =
      getResultsInfo(MemoryStatements.QUERY_VMSTATS, pid, request.getSession(), startTime, endTime, MemoryData.VmStatsSample.getDefaultInstance());
    List<HeapDumpInfo> heapDumpSamples =
      getResultsInfo(MemoryStatements.QUERY_HEAP_INFO_BY_TIME, pid, request.getSession(), startTime, endTime, HeapDumpInfo.getDefaultInstance());
    List<AllocationsInfo> allocationSamples =
      getResultsInfo(MemoryStatements.QUERY_ALLOCATION_INFO_BY_TIME, pid, request.getSession(), startTime, endTime, AllocationsInfo.getDefaultInstance());
    MemoryData.Builder response = MemoryData.newBuilder()
      .addAllMemSamples(memorySamples)
      .addAllVmStatsSamples(vmStatsSamples)
      .addAllHeapDumpInfos(heapDumpSamples)
      .addAllAllocationsInfo(allocationSamples);
    return response.build();
  }

  public void insertMemory(int pid, Common.Session session, List<MemoryData.MemorySample> samples) {
    for (MemoryData.MemorySample sample : samples) {
      execute(MemoryStatements.INSERT_SAMPLE, pid, session, sample.getTimestamp(), MemorySamplesType.MEMORY.ordinal(), sample.toByteArray());
    }
  }


  public void insertVmStats(int pid, Common.Session session, List<MemoryData.VmStatsSample> samples) {
    for (MemoryData.VmStatsSample sample : samples) {
      execute(MemoryStatements.INSERT_SAMPLE, pid, session, sample.getTimestamp(), MemorySamplesType.VMSTATS.ordinal(), sample.toByteArray());
    }
  }


  /**
   * Note: this will reset the row's Status and DumpData to NOT_READY and null respectively, if an info with the same DumpId already exist.
   */
  public void insertOrReplaceHeapInfo(int pid, Common.Session session, HeapDumpInfo info) {
    execute(MemoryStatements.INSERT_OR_REPLACE_HEAP_INFO, pid, session, info.getStartTime(), info.getEndTime(),
            DumpDataResponse.Status.NOT_READY.ordinal(), info.toByteArray());
  }

  /**
   * @return the dump status corresponding to a particular dump. If the entry does not exist, NOT_FOUND is returned.
   */
  public DumpDataResponse.Status getHeapDumpStatus(int pid, Common.Session session, long dumpTime) {
    try {
      ResultSet result = executeQuery(MemoryStatements.QUERY_HEAP_STATUS_BY_ID, pid, session, dumpTime);
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
    return getResultsInfo(MemoryStatements.QUERY_HEAP_INFO_BY_TIME, pid, session, request.getStartTime(), request.getEndTime(),
                          HeapDumpInfo.getDefaultInstance());
  }

  /**
   * Adds/updates the status and raw dump data associated with a dump sample's id.
   */
  public void insertHeapDumpData(int pid, Common.Session session, long dumpTime, DumpDataResponse.Status status, ByteString data) {
    execute(MemoryStatements.UPDATE_HEAP_DUMP, data.toByteArray(), status.getNumber(), pid, session, dumpTime);
  }

  /**
   * @return the raw dump byte content assocaited with a dump time. Null if an entry does not exist in the database.
   */
  @Nullable
  public byte[] getHeapDumpData(int pid, Common.Session session, long dumpTime) {
    try {
      ResultSet resultSet = executeQuery(MemoryStatements.QUERY_HEAP_DUMP_BY_ID, pid, session, dumpTime);
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
    execute(MemoryStatements.INSERT_OR_REPLACE_ALLOCATIONS_INFO, pid, session, info.getStartTime(), info.getEndTime(), info.toByteArray());
  }

  public void updateAllocationEvents(int pid, Common.Session session, long trackingStartTime, @NotNull AllocationEventsResponse allocationData) {

    execute(MemoryStatements.UPDATE_ALLOCATIONS_INFO_EVENTS, allocationData.toByteArray(), pid, session, trackingStartTime);
  }


  public void updateAllocationDump(int pid, Common.Session session, long trackingStartTime, byte[] data) {

    execute(MemoryStatements.UPDATE_ALLOCATIONS_INFO_DUMP, data, pid, session, trackingStartTime);
  }

  /**
   * @return the AllocationsInfo associated with the tracking start time. Null if an entry does not exist.
   */
  @Nullable
  public AllocationsInfo getAllocationsInfo(int pid, Common.Session session, long trackingStartTime) {
    try {
      ResultSet results = executeQuery(MemoryStatements.QUERY_ALLOCATION_INFO_BY_ID, pid, session, trackingStartTime);
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
  public AllocationEventsResponse getAllocationData(int pid, Common.Session session, long trackingStartTime) {

    try {
      ResultSet resultSet = executeQuery(MemoryStatements.QUERY_ALLOCATION_EVENTS_BY_ID, pid, session, trackingStartTime);
      if (resultSet.next()) {
        byte[] bytes = resultSet.getBytes(1);
        if (bytes != null) {
          return AllocationEventsResponse.parseFrom(resultSet.getBytes(1));
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
  public byte[] getAllocationDumpData(int pid, Common.Session session, long trackingStartTime) {

    try {
      ResultSet resultSet = executeQuery(MemoryStatements.QUERY_ALLOCATION_DUMP_BY_ID, pid, session, trackingStartTime);
      if (resultSet.next()) {
        return resultSet.getBytes(1);
      }
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  public void insertAllocationContext(@NotNull List<AllocatedClass> classes, @NotNull List<AllocationStack> stacks) {

    // TODO: batch insert
    classes.forEach(klass -> execute(MemoryStatements.INSERT_ALLOCATED_CLASS, klass.getClassId(), klass.toByteArray()));
    stacks.forEach(stack -> execute(MemoryStatements.INSERT_ALLOCATION_STACK, stack.getStackId().toByteArray(), stack.toByteArray()));
  }


  public AllocationContextsResponse listAllocationContexts(@NotNull AllocationContextsRequest request) {

    AllocationContextsResponse.Builder builder = AllocationContextsResponse.newBuilder();
    // TODO optimize queries
    try {
      for (int i = 0; i < request.getClassIdsCount(); i++) {
        ResultSet classResultSet = executeQuery(MemoryStatements.QUERY_ALLOCATED_CLASS, request.getClassIds(i));
        if (classResultSet.next()) {
          AllocatedClass data = AllocatedClass.newBuilder().mergeFrom(classResultSet.getBytes(1)).build();
          builder.addAllocatedClasses(data);
        }
      }

      for (int i = 0; i < request.getStackIdsCount(); i++) {
        ResultSet stackResultSet = executeQuery(MemoryStatements.QUERY_ALLOCATION_STACK, request.getStackIds(i).toByteArray());
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


  /**
   * A helper method for querying samples for MemorySample, VMStatsSample, HeapDumpInfo and AllocationsInfo
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
