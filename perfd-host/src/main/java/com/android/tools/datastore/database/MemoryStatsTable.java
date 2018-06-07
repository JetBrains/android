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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profiler.protobuf3jarjar.GeneratedMessageV3;
import com.android.tools.profiler.protobuf3jarjar.InvalidProtocolBufferException;
import com.android.tools.profiler.protobuf3jarjar.Message;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.datastore.database.MemoryStatsTable.MemoryStatements.*;

public class MemoryStatsTable extends DataStoreTable<MemoryStatsTable.MemoryStatements> {

  public enum MemoryStatements {
    INSERT_SAMPLE("INSERT OR IGNORE INTO Memory_Samples (Session, Timestamp, Type, Data) VALUES (?, ?, ?, ?)"),
    QUERY_MEMORY(String.format("SELECT Data FROM Memory_Samples WHERE Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                               MemorySamplesType.MEMORY.ordinal())),
    QUERY_ALLOC_STATS(String.format("SELECT Data FROM Memory_Samples WHERE Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                                    MemorySamplesType.ALLOC_STATS.ordinal())),

    // TODO: gc stats are duration data so we should account for end time. In reality this is usually sub-ms so it might not matter?
    QUERY_GC_STATS(String.format("SELECT Data FROM Memory_Samples WHERE Session = ? AND Type = %d AND TimeStamp > ? AND TimeStamp <= ?",
                                 MemorySamplesType.GC_STATS.ordinal())),

    INSERT_OR_REPLACE_HEAP_INFO(
      "INSERT OR REPLACE INTO Memory_HeapDump (Session, StartTime, EndTime, Status, InfoData) VALUES (?, ?, ?, ?, ?)"),
    UPDATE_HEAP_DUMP("UPDATE Memory_HeapDump SET DumpData = ?, Status = ? WHERE Session = ? AND StartTime = ?"),
    // EndTime = UNSPECIFIED_DURATION checks for the special case where we have an ongoing duration sample
    QUERY_HEAP_INFO_BY_TIME("SELECT InfoData FROM Memory_HeapDump where Session = ? AND EndTime > ? AND StartTime <= ?"),
    QUERY_HEAP_DUMP_BY_ID("SELECT DumpData FROM Memory_HeapDump where Session = ? AND StartTime = ?"),
    QUERY_HEAP_STATUS_BY_ID("SELECT Status FROM Memory_HeapDump where Session = ? AND StartTime = ?"),

    INSERT_OR_REPLACE_ALLOCATIONS_INFO(
      "INSERT OR REPLACE INTO Memory_AllocationInfo (Session, StartTime, EndTime, InfoData) VALUES (?, ?, ?, ?)"),
    UPDATE_LEGACY_ALLOCATIONS_INFO_EVENTS("UPDATE Memory_AllocationInfo SET LegacyEventsData = ? WHERE Session = ? AND StartTime = ?"),
    UPDATE_LEGACY_ALLOCATIONS_INFO_DUMP("UPDATE Memory_AllocationInfo SET LegacyDumpData = ? WHERE Session = ? AND StartTime = ?"),
    // EndTime = UNSPECIFIED_DURATION checks for the special case where we have an ongoing duration sample
    QUERY_ALLOCATION_INFO_BY_TIME("SELECT InfoData FROM Memory_AllocationInfo WHERE Session = ? AND EndTime > ? AND StartTime <= ?"),
    QUERY_ALLOCATION_INFO_BY_ID("SELECT InfoData from Memory_AllocationInfo WHERE Session = ? AND StartTime = ?"),
    QUERY_LEGACY_ALLOCATION_EVENTS_BY_ID("SELECT LegacyEventsData from Memory_AllocationInfo WHERE Session = ? AND StartTime = ?"),
    QUERY_LEGACY_ALLOCATION_DUMP_BY_ID("SELECT LegacyDumpData from Memory_AllocationInfo WHERE Session = ? AND StartTime = ?"),

    INSERT_LEGACY_ALLOCATION_STACK("INSERT OR IGNORE INTO Memory_LegacyAllocationStack (Session, Id, Data) VALUES (?, ?, ?)"),
    INSERT_LEGACY_ALLOCATED_CLASS("INSERT OR IGNORE INTO Memory_LegacyAllocatedClass (Session, Id, Data) VALUES (?, ?, ?)"),
    QUERY_LEGACY_ALLOCATION_STACK("Select Data FROM Memory_LegacyAllocationStack WHERE Session = ? AND Id = ?"),
    QUERY_LEGACY_ALLOCATED_CLASS("Select Data FROM Memory_LegacyAllocatedClass WHERE Session = ? AND Id = ?");

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
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Memory_Samples", "Session INTEGER NOT NULL", "Timestamp INTEGER", "Type INTEGER",
                  "Data BLOB", "PRIMARY KEY(Session, Timestamp, Type)");
      createTable("Memory_AllocationInfo", "Session INTEGER NOT NULL", "StartTime INTEGER",
                  "EndTime INTEGER", "InfoData BLOB", "LegacyEventsData BLOB", "LegacyDumpData BLOB",
                  "PRIMARY KEY(Session, StartTime)");
      createTable("Memory_LegacyAllocationStack", "Session INTEGER NOT NULL", "Id INTEGER", "Data BLOB",
                  "PRIMARY KEY(Session, Id)");
      createTable("Memory_LegacyAllocatedClass", "Session INTEGER NOT NULL", "Id INTEGER", "Data BLOB",
                  "PRIMARY KEY(Session, Id)");
      createTable("Memory_HeapDump", "Session INTEGER NOT NULL", "StartTime INTEGER",
                  "EndTime INTEGER", "Status INTEGER", "InfoData BLOB", "DumpData BLOB", "PRIMARY KEY(Session, StartTime)");
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

  @NotNull
  public MemoryData getData(MemoryRequest request) {
    long sessionId = request.getSession().getSessionId();
    long startTime = request.getStartTime();
    long endTime = request.getEndTime();
    List<MemoryData.MemorySample> memorySamples =
      getResultsInfo(QUERY_MEMORY, sessionId, startTime, endTime, MemoryData.MemorySample.getDefaultInstance());
    List<MemoryData.AllocStatsSample> allocStatsSamples =
      getResultsInfo(QUERY_ALLOC_STATS, sessionId, startTime, endTime, MemoryData.AllocStatsSample.getDefaultInstance());
    List<MemoryData.GcStatsSample> gcStatsSamples =
      getResultsInfo(QUERY_GC_STATS, sessionId, startTime, endTime, MemoryData.GcStatsSample.getDefaultInstance());
    List<HeapDumpInfo> heapDumpSamples =
      getResultsInfo(QUERY_HEAP_INFO_BY_TIME, sessionId, startTime, endTime, HeapDumpInfo.getDefaultInstance());
    List<AllocationsInfo> allocationSamples =
      getResultsInfo(QUERY_ALLOCATION_INFO_BY_TIME, sessionId, startTime, endTime, AllocationsInfo.getDefaultInstance());
    MemoryData.Builder response = MemoryData.newBuilder()
      .addAllMemSamples(memorySamples)
      .addAllAllocStatsSamples(allocStatsSamples)
      .addAllGcStatsSamples(gcStatsSamples)
      .addAllHeapDumpInfos(heapDumpSamples)
      .addAllAllocationsInfo(allocationSamples);
    return response.build();
  }

  public void insertMemory(Common.Session session, List<MemoryData.MemorySample> samples) {
    for (MemoryData.MemorySample sample : samples) {
      execute(INSERT_SAMPLE, session.getSessionId(), sample.getTimestamp(), MemorySamplesType.MEMORY.ordinal(),
              sample.toByteArray());
    }
  }

  public void insertAllocStats(Common.Session session, List<MemoryData.AllocStatsSample> samples) {
    for (MemoryData.AllocStatsSample sample : samples) {
      execute(INSERT_SAMPLE, session.getSessionId(), sample.getTimestamp(), MemorySamplesType.ALLOC_STATS.ordinal(),
              sample.toByteArray());
    }
  }

  public void insertGcStats(Common.Session session, List<MemoryData.GcStatsSample> samples) {
    for (MemoryData.GcStatsSample sample : samples) {
      execute(INSERT_SAMPLE, session.getSessionId(), sample.getStartTime(), MemorySamplesType.GC_STATS.ordinal(),
              sample.toByteArray());
    }
  }

  /**
   * Note: this will reset the row's Status and DumpData to NOT_READY and null respectively, if an info with the same DumpId already exist.
   */
  public void insertOrReplaceHeapInfo(Common.Session session, HeapDumpInfo info) {
    execute(INSERT_OR_REPLACE_HEAP_INFO, session.getSessionId(), info.getStartTime(), info.getEndTime(),
            DumpDataResponse.Status.NOT_READY.ordinal(), info.toByteArray());
  }

  /**
   * @return the dump status corresponding to a particular dump. If the entry does not exist, NOT_FOUND is returned.
   */
  public DumpDataResponse.Status getHeapDumpStatus(Common.Session session, long dumpTime) {
    try {
      ResultSet result = executeQuery(QUERY_HEAP_STATUS_BY_ID, session.getSessionId(), dumpTime);
      if (result.next()) {
        return DumpDataResponse.Status.forNumber(result.getInt(1));
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return DumpDataResponse.Status.NOT_FOUND;
  }


  public List<HeapDumpInfo> getHeapDumpInfoByRequest(Common.Session session, ListDumpInfosRequest request) {
    return getResultsInfo(QUERY_HEAP_INFO_BY_TIME, session.getSessionId(), request.getStartTime(), request.getEndTime(),
                          HeapDumpInfo.getDefaultInstance());
  }

  /**
   * Adds/updates the status and raw dump data associated with a dump sample's id.
   */
  public void insertHeapDumpData(Common.Session session, long dumpTime, DumpDataResponse.Status status, ByteString data) {
    execute(UPDATE_HEAP_DUMP, data.toByteArray(), status.getNumber(), session.getSessionId(), dumpTime);
  }

  /**
   * @return the raw dump byte content assocaited with a dump time. Null if an entry does not exist in the database.
   */
  @Nullable
  public byte[] getHeapDumpData(Common.Session session, long dumpTime) {
    try {
      ResultSet resultSet = executeQuery(QUERY_HEAP_DUMP_BY_ID, session.getSessionId(), dumpTime);
      if (resultSet.next()) {
        return resultSet.getBytes(1);
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return null;
  }


  /**
   * Note: this will reset the allocation events and its raw dump byte content associated with a tracking start time if an entry already exists.
   */
  public void insertOrReplaceAllocationsInfo(Common.Session session, AllocationsInfo info) {
    execute(INSERT_OR_REPLACE_ALLOCATIONS_INFO, session.getSessionId(), info.getStartTime(), info.getEndTime(), info.toByteArray());
  }

  public void updateLegacyAllocationEvents(Common.Session session,
                                           long trackingStartTime,
                                           @NotNull LegacyAllocationEventsResponse allocationData) {
    execute(UPDATE_LEGACY_ALLOCATIONS_INFO_EVENTS, allocationData.toByteArray(), session.getSessionId(), trackingStartTime);
  }


  public void updateLegacyAllocationDump(Common.Session session, long trackingStartTime, byte[] data) {

    execute(UPDATE_LEGACY_ALLOCATIONS_INFO_DUMP, data, session.getSessionId(), trackingStartTime);
  }

  /**
   * @return the AllocationsInfo associated with the tracking start time. Null if an entry does not exist.
   */
  @Nullable
  public AllocationsInfo getAllocationsInfo(Common.Session session, long trackingStartTime) {
    try {
      ResultSet results = executeQuery(QUERY_ALLOCATION_INFO_BY_ID, session.getSessionId(), trackingStartTime);
      if (results.next()) {
        byte[] bytes = results.getBytes(1);
        if (bytes != null) {
          return AllocationsInfo.parseFrom(bytes);
        }
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }

    return null;
  }


  /**
   * @return the AllocationEventsResponse associated with the tracking start time. Null if an entry does not exist.
   */
  @Nullable
  public LegacyAllocationEventsResponse getLegacyAllocationData(Common.Session session, long trackingStartTime) {

    try {
      ResultSet resultSet = executeQuery(QUERY_LEGACY_ALLOCATION_EVENTS_BY_ID, session.getSessionId(), trackingStartTime);
      if (resultSet.next()) {
        byte[] bytes = resultSet.getBytes(1);
        if (bytes != null) {
          return LegacyAllocationEventsResponse.parseFrom(resultSet.getBytes(1));
        }
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return null;
  }

  /**
   * @return the raw legacy allocation tracking byte data associated with the tracking start time. Null if an entry does not exist.
   */
  @Nullable
  public byte[] getLegacyAllocationDumpData(Common.Session session, long trackingStartTime) {

    try {
      ResultSet resultSet = executeQuery(QUERY_LEGACY_ALLOCATION_DUMP_BY_ID, session.getSessionId(), trackingStartTime);
      if (resultSet.next()) {
        return resultSet.getBytes(1);
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return null;
  }

  public void insertLegacyAllocationContext(@NotNull Common.Session session,
                                            @NotNull List<AllocatedClass> classes,
                                            @NotNull List<AllocationStack> stacks) {
    // TODO: batch insert
    classes.forEach(klass -> execute(INSERT_LEGACY_ALLOCATED_CLASS, session.getSessionId(), klass.getClassId(), klass.toByteArray()));
    stacks
      .forEach(stack -> execute(INSERT_LEGACY_ALLOCATION_STACK, session.getSessionId(), stack.getStackId(), stack.toByteArray()));
  }

  @NotNull
  public AllocationContextsResponse getLegacyAllocationContexts(@NotNull LegacyAllocationContextsRequest request) {

    AllocationContextsResponse.Builder builder = AllocationContextsResponse.newBuilder();
    // TODO optimize queries
    try {
      for (int i = 0; i < request.getClassIdsCount(); i++) {
        ResultSet classResultSet =
          executeQuery(QUERY_LEGACY_ALLOCATED_CLASS, request.getSession().getSessionId(), request.getClassIds(i));
        if (classResultSet.next()) {
          AllocatedClass data = AllocatedClass.newBuilder().mergeFrom(classResultSet.getBytes(1)).build();
          builder.addAllocatedClasses(data);
        }
      }

      for (int i = 0; i < request.getStackIdsCount(); i++) {
        ResultSet stackResultSet =
          executeQuery(QUERY_LEGACY_ALLOCATION_STACK, request.getSession().getSessionId(), request.getStackIds(i));
        if (stackResultSet.next()) {
          AllocationStack data = AllocationStack.newBuilder().mergeFrom(stackResultSet.getBytes(1)).build();
          builder.addAllocationStacks(data);
        }
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return builder.build();
  }

  /**
   * A helper method for querying samples for MemorySample, AllocStatsSample, GcStatsSample, HeapDumpInfo and AllocationsInfo
   */
  private <T extends GeneratedMessageV3> List<T> getResultsInfo(MemoryStatements query,
                                                                long sessionId,
                                                                long startTime,
                                                                long endTime,
                                                                T defaultInstance) {
    List<T> datas = new ArrayList<>();
    try {
      ResultSet resultSet = executeQuery(query, sessionId, startTime, endTime);
      while (resultSet.next()) {
        Message data = defaultInstance.toBuilder().mergeFrom(resultSet.getBytes(1)).build();
        datas.add((T)data);
      }
    }
    catch (ClassCastException | InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return datas;
  }
}
