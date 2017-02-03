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
    INSERT_SAMPLE,
    INSERT_OR_REPLACE_HEAP_INFO,
    INSERT_OR_REPLACE_ALLOCATIONS_INFO,
    FIND_HEAP_DATA,
    QUERY_HEAP_INFO,
    QUERY_MEMORY,
    QUERY_VMSTATS,
    QUERY_ALLOCATION_INFO,
    INSERT_ALLOCATION_STACK,
    INSERT_ALLOCATED_CLASS,
    QUERY_ALLOCATION_STACK,
    QUERY_ALLOCATED_CLASS,
    UPDATE_HEAP_INFO,
    UPDATE_ALLOCATIONS_INFO_EVENTS,
    UPDATE_ALLOCATIONS_INFO_DUMP,
    FIND_HEAP_STATUS,
    FIND_ALLOCATION_INFO,
    FIND_ALLOCATION_EVENTS,
    FIND_ALLOCATION_DUMP
  }

  private enum MemorySamplesType {
    MEMORY,
    VMSTATS,
  }

  /**
   * TODO: currently we are using the same PreparedStatements across different threads. This can lead to a ResultSet resetting/closing
   * while another thread is still iterating results. For now we use a lock to synchornize queries, but should we ensure each thread
   * execute its own unique PreparedStatements?
   */
  @NotNull
  private final Object myDataQueryLock = new Object();

  @Override
  public void initialize(Connection connection) {
    super.initialize(connection);
    try {
      createTable("Memory_Samples", "Type INTEGER", "Timestamp INTEGER", "Data BLOB");
      createTable("Memory_AllocationInfo", "DumpId INTEGER, StartTime INTEGER", "EndTime INTEGER", "InfoData BLOB",
                  "EventsData BLOB", "DumpData BLOB", "PRIMARY KEY(DumpId)");
      createTable("Memory_AllocationStack", "Id BLOB", "Data BLOB", "PRIMARY KEY(Id)");
      createTable("Memory_AllocatedClass", "Id INTEGER", "Data BLOB", "PRIMARY KEY(Id)");
      createTable("Memory_HeapDump", "DumpId, INTEGER", "StartTime INTEGER", "EndTime INTEGER", "Status INTEGER", "InfoData BLOB",
                  "DumpData BLOB", "PRIMARY KEY(DumpId)");
      createIndex("Memory_HeapDump", "DumpId");
      createIndex("Memory_AllocationInfo", "DumpId");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Override
  public void prepareStatements(Connection connection) {
    try {
      createStatement(MemoryStatements.INSERT_SAMPLE, "INSERT INTO Memory_Samples (Type, Timestamp, Data) VALUES (?, ?, ?)");
      String sampleQueryString =
        "SELECT Data FROM Memory_Samples WHERE Type = %d AND (TimeStamp = ? OR (TimeStamp > ? AND TimeStamp <= ?))";
      createStatement(MemoryStatements.QUERY_MEMORY, String.format(sampleQueryString, MemorySamplesType.MEMORY.ordinal()));
      createStatement(MemoryStatements.QUERY_VMSTATS, String.format(sampleQueryString, MemorySamplesType.VMSTATS.ordinal()));

      createStatement(MemoryStatements.INSERT_OR_REPLACE_HEAP_INFO,
                      "INSERT OR REPLACE INTO Memory_HeapDump (DumpId, StartTime, EndTime, Status, InfoData) VALUES (?, ?, ?, ?, ?)");
      createStatement(MemoryStatements.UPDATE_HEAP_INFO,
                      "UPDATE Memory_HeapDump SET DumpData = ?, Status = ? WHERE DumpId = ?");
      createStatement(MemoryStatements.FIND_HEAP_DATA, "SELECT DumpData FROM Memory_HeapDump where DumpId = ?");
      createStatement(MemoryStatements.FIND_HEAP_STATUS, "SELECT Status FROM Memory_HeapDump where DumpId = ?");
      createStatement(MemoryStatements.QUERY_HEAP_INFO,
                      "SELECT InfoData FROM Memory_HeapDump where (EndTime = ? OR EndTime > ?) AND StartTime <= ?");

      createStatement(MemoryStatements.INSERT_OR_REPLACE_ALLOCATIONS_INFO,
                      "INSERT OR REPLACE INTO Memory_AllocationInfo (DumpId, StartTime, EndTime, InfoData) VALUES (?, ?, ?, ?)");
      createStatement(MemoryStatements.UPDATE_ALLOCATIONS_INFO_EVENTS,
                      "UPDATE Memory_AllocationInfo SET EventsData = ? WHERE DumpId = ?");
      createStatement(MemoryStatements.UPDATE_ALLOCATIONS_INFO_DUMP,
                      "UPDATE Memory_AllocationInfo SET DumpData = ? WHERE DumpId = ?");
      createStatement(MemoryStatements.FIND_ALLOCATION_INFO, "SELECT InfoData from Memory_AllocationInfo WHERE DumpId = ?");
      createStatement(MemoryStatements.FIND_ALLOCATION_EVENTS, "SELECT EventsData from Memory_AllocationInfo WHERE DumpId = ?");
      createStatement(MemoryStatements.FIND_ALLOCATION_DUMP, "SELECT DumpData from Memory_AllocationInfo WHERE DumpId = ?");

      createStatement(MemoryStatements.INSERT_ALLOCATION_STACK, "INSERT OR IGNORE INTO Memory_AllocationStack (Id, Data) VALUES (?, ?)");
      createStatement(MemoryStatements.INSERT_ALLOCATED_CLASS, "INSERT OR IGNORE INTO Memory_AllocatedClass (Id, Data) VALUES (?, ?)");

      createStatement(MemoryStatements.QUERY_ALLOCATION_STACK, "Select Data FROM Memory_AllocationStack WHERE Id = ?");
      createStatement(MemoryStatements.QUERY_ALLOCATED_CLASS, "Select Data FROM Memory_AllocatedClass WHERE Id = ?");

      createStatement(MemoryStatements.QUERY_ALLOCATION_INFO,
                      "SELECT InfoData FROM Memory_AllocationInfo WHERE (EndTime = ? OR EndTime > ?) AND StartTime <= ?");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  @Nullable
  public byte[] getHeapDumpData(int dumpId) {
    synchronized (myDataQueryLock) {
      try {
        ResultSet resultSet = executeQuery(MemoryStatements.FIND_HEAP_DATA, dumpId);
        if (resultSet.next()) {
          return resultSet.getBytes(1);
        }
      }
      catch (SQLException ex) {
        getLogger().error(ex);
      }
      return null;
    }
  }

  @Nullable
  public byte[] getAllocationDumpData(int dumpId) {
    synchronized (myDataQueryLock) {
      try {
        ResultSet resultSet = executeQuery(MemoryStatements.FIND_ALLOCATION_DUMP, dumpId);
        if (resultSet.next()) {
          return resultSet.getBytes(1);
        }
      }
      catch (SQLException ex) {
        getLogger().error(ex);
      }
      return null;
    }
  }

  public MemoryData getData(MemoryRequest request) {
    synchronized (myDataQueryLock) {
      MemoryData.Builder response = MemoryData.newBuilder();
      response.addAllMemSamples(getMemoryDataByRequest(request));
      response.addAllVmStatsSamples(getVmStatsDataByRequest(request));
      response.addAllHeapDumpInfos(getHeapDumpInfoByRequest(request));
      response.addAllAllocationsInfo(getAllocationInfoByRequest(request));
      return response.build();
    }
  }

  public List<HeapDumpInfo> getHeapDumpInfoByRequest(ListDumpInfosRequest request) {
    synchronized (myDataQueryLock) {
      return getResultsInfo(MemoryStatements.QUERY_HEAP_INFO, true, request.getStartTime(), request.getEndTime(),
                            HeapDumpInfo.getDefaultInstance());
    }
  }

  private List<HeapDumpInfo> getHeapDumpInfoByRequest(MemoryRequest request) {
    return getResultsInfo(MemoryStatements.QUERY_HEAP_INFO, true, request.getStartTime(), request.getEndTime(),
                          HeapDumpInfo.getDefaultInstance());
  }

  private List<MemoryData.MemorySample> getMemoryDataByRequest(MemoryRequest request) {
    return getResultsInfo(MemoryStatements.QUERY_MEMORY, true, request.getStartTime(), request.getEndTime(),
                          MemoryData.MemorySample.getDefaultInstance());
  }

  private List<MemoryData.VmStatsSample> getVmStatsDataByRequest(MemoryRequest request) {
    return getResultsInfo(MemoryStatements.QUERY_VMSTATS, true, request.getStartTime(), request.getEndTime(),
                          MemoryData.VmStatsSample.getDefaultInstance());
  }

  private List<AllocationsInfo> getAllocationInfoByRequest(MemoryRequest request) {
    return getResultsInfo(MemoryStatements.QUERY_ALLOCATION_INFO, true, request.getStartTime(), request.getEndTime(),
                          AllocationsInfo.getDefaultInstance());
  }

  @NotNull
  public AllocationsInfo getAllocationsInfo(int infoId) {
    synchronized (myDataQueryLock) {
      ResultSet results = executeQuery(MemoryStatements.FIND_ALLOCATION_INFO, infoId);
      try {
        if (results.next()) {
          AllocationsInfo.Builder builder = AllocationsInfo.newBuilder();
          builder.mergeFrom(results.getBytes(1));
          return builder.build();
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }

      return null;
    }
  }

  public void insertMemory(List<MemoryData.MemorySample> samples) {
    synchronized (myDataQueryLock) {
      for (MemoryData.MemorySample sample : samples) {
        execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.MEMORY.ordinal(), sample.getTimestamp(), sample.toByteArray());
      }
    }
  }

  public void insertVmStats(List<MemoryData.VmStatsSample> samples) {
    synchronized (myDataQueryLock) {
      for (MemoryData.VmStatsSample sample : samples) {
        execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.VMSTATS.ordinal(), sample.getTimestamp(), sample.toByteArray());
      }
    }
  }

  public void insertOrReplaceAllocationsInfo(AllocationsInfo info) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.INSERT_OR_REPLACE_ALLOCATIONS_INFO, info.getInfoId(), info.getStartTime(), info.getEndTime(),
              info.toByteArray());
    }
  }

  public void updateAllocationEvents(int infoId, @NotNull AllocationEventsResponse allocationData) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.UPDATE_ALLOCATIONS_INFO_EVENTS, allocationData.toByteArray(), infoId);
    }
  }

  public void updateAllocationDump(int infoId, byte[] data) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.UPDATE_ALLOCATIONS_INFO_DUMP, data, infoId);
    }
  }

  @Nullable
  public AllocationEventsResponse getAllocationData(int infoId) {
    synchronized (myDataQueryLock) {
      try {
        ResultSet resultSet = executeQuery(MemoryStatements.FIND_ALLOCATION_EVENTS, infoId);
        if (resultSet.next()) {
          byte[] bytes = resultSet.getBytes(1);
          if (bytes != null) {
            AllocationEventsResponse.Builder builder = AllocationEventsResponse.newBuilder();
            builder.mergeFrom(resultSet.getBytes(1));
            return builder.build();
          }
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
      return null;
    }
  }

  public void insertAllocationContext(@NotNull List<AllocatedClass> classes, @NotNull List<AllocationStack> stacks) {
    synchronized (myDataQueryLock) {
      // TODO: batch insert
      classes.forEach(klass -> execute(MemoryStatements.INSERT_ALLOCATED_CLASS, klass.getClassId(), klass.toByteArray()));
      stacks.forEach(stack -> execute(MemoryStatements.INSERT_ALLOCATION_STACK, stack.getStackId().toByteArray(), stack.toByteArray()));
    }
  }

  public AllocationContextsResponse listAllocationContexts(@NotNull AllocationContextsRequest request) {
    synchronized (myDataQueryLock) {
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
  }

  /**
   * Note: this would reset the Status and Dump data of the heap to NOT_READY if an info with the same Id already exist.
   */
  public void insertOrUpdateHeapInfo(HeapDumpInfo info) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.INSERT_OR_REPLACE_HEAP_INFO, info.getDumpId(), info.getStartTime(), info.getEndTime(),
              DumpDataResponse.Status.NOT_READY.ordinal(), info.toByteArray());
    }
  }

  public DumpDataResponse.Status getHeapDumpStatus(int dumpId) {
    synchronized (myDataQueryLock) {
      try {
        ResultSet result = executeQuery(MemoryStatements.FIND_HEAP_STATUS, dumpId);
        if (result.next()) {
          return DumpDataResponse.Status.forNumber(result.getInt(1));
        }
      }
      catch (SQLException ex) {
        getLogger().error(ex);
      }
      return DumpDataResponse.Status.NOT_FOUND;
    }
  }

  public void insertHeapDumpData(int dumpId, DumpDataResponse.Status status, ByteString data) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.UPDATE_HEAP_INFO, data.toByteArray(), status.getNumber(), dumpId);
    }
  }

  private <T extends GeneratedMessageV3> List<T> getResultsInfo(MemoryStatements query,
                                                                boolean includeUnspecified,
                                                                long startTime,
                                                                long endTime,
                                                                T defaultInstance) {
    List<T> datas = new ArrayList<>();
    try {
      ResultSet resultSet = includeUnspecified ? executeQuery(query, DurationData.UNSPECIFIED_DURATION, startTime, endTime) :
                            executeQuery(query, startTime, endTime);
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
