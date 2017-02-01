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
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo;
import com.google.protobuf3jarjar.ByteString;
import com.google.protobuf3jarjar.GeneratedMessageV3;
import com.google.protobuf3jarjar.InvalidProtocolBufferException;
import com.google.protobuf3jarjar.Message;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MemoryTable extends DatastoreTable<MemoryTable.MemoryStatements> {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryTable.class);
  }

  /**
   * TODO: currently we are using the same PreparedStatements across different threads. This can lead to a ResultSet resetting/closing
   * while another thread is still iterating results. For now we use a lock to synchornize queries, but should we ensure each thread
   * execute its own unique PreparedStatements?
   */
  @NotNull
  private final Object myDataQueryLock = new Object();

  public enum MemoryStatements {
    INSERT_SAMPLE,
    INSERT_HEAP,
    INSERT_ALLOCATION_INFO,
    FIND_HEAP_DATA,
    QUERY_HEAP_INFO,
    QUERY_MEMORY,
    QUERY_VMSTATS,
    QUERY_ALLOCATION_INFO,
    QUERY_ALLOCATION_EVENT,
    REMOVE_UNFINISHED_ALLOCAITON_INFO,
    INSERT_ALLOCATION_STACK,
    INSERT_ALLOCATED_CLASS,
    QUERY_ALLOCATION_STACK,
    QUERY_ALLOCATED_CLASS,
    UPDATE_HEAP_INFO,
    UPDATE_ALLOCATIONS_INFO,
    FIND_HEAP_STATUS,
    FIND_ALLOCATION_INFO,
    FIND_ALLOCATION_DATA
  }

  public enum MemorySamplesType {
    MEMORY,
    VMSTATS,
    ALLOCATION_EVENT
  }

  @Override
  public void initialize(Connection connection) {
    super.initialize(connection);
    try {
      createTable("Memory_Samples", "Type INTEGER", "Timestamp INTEGER", "Data BLOB");
      createTable("Memory_AllocationInfo", "DumpId INTEGER, StartTime INTEGER", "EndTime INTEGER", "InfoData BLOB",
                  "DumpData BLOB", "PRIMARY KEY(DumpId)");
      createTable("Memory_AllocationStack", "Id BLOB", "Data BLOB", "PRIMARY KEY(Id)");
      createTable("Memory_AllocatedClass", "Name TEXT", "Data BLOB", "PRIMARY KEY(Name)");
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
      createStatement(MemoryStatements.QUERY_ALLOCATION_EVENT,
                      String.format(sampleQueryString, MemorySamplesType.ALLOCATION_EVENT.ordinal()));

      createStatement(MemoryStatements.INSERT_HEAP,
                      "INSERT INTO Memory_HeapDump (DumpId, StartTime, EndTime, Status, InfoData) VALUES (?, ?, ?, ?, ?)");
      createStatement(MemoryStatements.UPDATE_HEAP_INFO,
                      "UPDATE Memory_HeapDump SET DumpData = ?, InfoData = ?, Status = ? WHERE DumpId = ?");
      createStatement(MemoryStatements.FIND_HEAP_DATA, "SELECT InfoData, DumpData FROM Memory_HeapDump where DumpId = ?");
      createStatement(MemoryStatements.FIND_HEAP_STATUS, "SELECT Status FROM Memory_HeapDump where DumpId = ?");
      createStatement(MemoryStatements.QUERY_HEAP_INFO,
                      "SELECT InfoData FROM Memory_HeapDump where (EndTime = ? OR EndTime > ?) AND StartTime <= ?");

      createStatement(MemoryStatements.INSERT_ALLOCATION_INFO,
                      "INSERT OR REPLACE INTO Memory_AllocationInfo (DumpId, StartTime, EndTime, InfoData) VALUES (?, ?, ?, ?)");
      createStatement(MemoryStatements.UPDATE_ALLOCATIONS_INFO,
                      "UPDATE Memory_AllocationInfo SET InfoData = ?, DumpData = ? WHERE DumpId = ?");
      createStatement(MemoryStatements.FIND_ALLOCATION_INFO, "SELECT InfoData from Memory_AllocationInfo WHERE DumpId = ?");
      createStatement(MemoryStatements.FIND_ALLOCATION_DATA, "SELECT DumpData from Memory_AllocationInfo WHERE DumpId = ?");
      createStatement(MemoryStatements.REMOVE_UNFINISHED_ALLOCAITON_INFO, "DELETE FROM Memory_AllocationInfo WHERE EndTime = ?");

      createStatement(MemoryStatements.INSERT_ALLOCATION_STACK, "INSERT OR REPLACE INTO Memory_AllocationStack (Id, Data) VALUES (?, ?)");
      createStatement(MemoryStatements.INSERT_ALLOCATED_CLASS, "INSERT OR REPLACE INTO Memory_AllocatedClass (Name, Data) VALUES (?, ?)");

      createStatement(MemoryStatements.QUERY_ALLOCATION_STACK, "Select Data FROM Memory_AllocationStack");
      createStatement(MemoryStatements.QUERY_ALLOCATED_CLASS, "Select Data FROM Memory_AllocatedClass");

      createStatement(MemoryStatements.QUERY_ALLOCATION_INFO,
                      "SELECT InfoData FROM Memory_AllocationInfo WHERE (EndTime = ? OR EndTime > ?) AND StartTime <= ?");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public byte[] getHeapDumpData(int dumpId, MemoryProfiler.HeapDumpInfo.Builder out_info) {
    synchronized (myDataQueryLock) {
      try {
        ResultSet resultSet = executeQuery(MemoryStatements.FIND_HEAP_DATA, dumpId);
        if (resultSet.next()) {
          out_info.mergeFrom(resultSet.getBytes(1));
          return resultSet.getBytes(2);
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
      return null;
    }
  }

  public byte[] getAllocationDumpData(int dumpId) {
    synchronized (myDataQueryLock) {
      try {
        ResultSet resultSet = executeQuery(MemoryStatements.FIND_ALLOCATION_DATA, dumpId);
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

  public MemoryProfiler.MemoryData getData(MemoryProfiler.MemoryRequest request) {
    synchronized (myDataQueryLock) {
      MemoryProfiler.MemoryData.Builder response = MemoryProfiler.MemoryData.newBuilder();
      response.addAllMemSamples(getMemoryDataByRequest(request));
      response.addAllVmStatsSamples(getVmStatsDataByRequest(request));
      response.addAllHeapDumpInfos(getHeapDumpInfoByRequest(request));
      response.addAllAllocationsInfo(getAllocationInfoByRequest(request));
      response.addAllAllocationEvents(getAllocationEventsByRequest(request));
      return response.build();
    }
  }

  public List<MemoryProfiler.HeapDumpInfo> getHeapDumpInfoByRequest(MemoryProfiler.ListDumpInfosRequest request) {
    synchronized (myDataQueryLock) {
      return getResultsInfo(MemoryStatements.QUERY_HEAP_INFO, true, request.getStartTime(), request.getEndTime(),
                            MemoryProfiler.HeapDumpInfo.getDefaultInstance());
    }
  }

  private List<MemoryProfiler.HeapDumpInfo> getHeapDumpInfoByRequest(MemoryProfiler.MemoryRequest request) {
    return getResultsInfo(MemoryStatements.QUERY_HEAP_INFO, true, request.getStartTime(), request.getEndTime(),
                          MemoryProfiler.HeapDumpInfo.getDefaultInstance());
  }

  private List<MemoryProfiler.MemoryData.MemorySample> getMemoryDataByRequest(MemoryProfiler.MemoryRequest request) {
    return getResultsInfo(MemoryStatements.QUERY_MEMORY, true, request.getStartTime(), request.getEndTime(),
                          MemoryProfiler.MemoryData.MemorySample.getDefaultInstance());
  }

  private List<MemoryProfiler.MemoryData.VmStatsSample> getVmStatsDataByRequest(MemoryProfiler.MemoryRequest request) {
    return getResultsInfo(MemoryStatements.QUERY_VMSTATS, true, request.getStartTime(), request.getEndTime(),
                          MemoryProfiler.MemoryData.VmStatsSample.getDefaultInstance());
  }

  private List<AllocationsInfo> getAllocationInfoByRequest(MemoryProfiler.MemoryRequest request) {
    return getResultsInfo(MemoryStatements.QUERY_ALLOCATION_INFO, true, request.getStartTime(), request.getEndTime(),
                          MemoryProfiler.AllocationsInfo.getDefaultInstance());
  }

  private List<MemoryProfiler.MemoryData.AllocationEvent> getAllocationEventsByRequest(MemoryProfiler.MemoryRequest request) {
    return getResultsInfo(MemoryStatements.QUERY_ALLOCATION_EVENT, true, request.getStartTime(), request.getEndTime(),
                          MemoryProfiler.MemoryData.AllocationEvent.getDefaultInstance());
  }

  public MemoryProfiler.GetAllocationsInfoStatusResponse getAllocationInfoStatus(int id) {
    synchronized (myDataQueryLock) {
      ResultSet results = executeQuery(MemoryStatements.FIND_ALLOCATION_INFO, id);
      MemoryProfiler.GetAllocationsInfoStatusResponse.Builder response = MemoryProfiler.GetAllocationsInfoStatusResponse.newBuilder();
      try {
        if (results.next()) {
          MemoryProfiler.AllocationsInfo.Status status =
            MemoryProfiler.AllocationsInfo.newBuilder().mergeFrom(results.getBytes(1)).getStatus();
          response.setInfoId(id);
          response.setStatus(status);
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
      return response.build();
    }
  }

  public void updateAllocationInfo(int id, AllocationsInfo.Status status) {
    synchronized (myDataQueryLock) {
      ResultSet results = executeQuery(MemoryStatements.FIND_ALLOCATION_INFO, id);
      try {
        if (results.next()) {
          MemoryProfiler.AllocationsInfo info = MemoryProfiler.AllocationsInfo.newBuilder().mergeFrom(results.getBytes(1))
            .setStatus(status)
            .build();
          execute(MemoryStatements.INSERT_ALLOCATION_INFO, info.getInfoId(), info.getStartTime(), info.getEndTime(), info.toByteArray());
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
    }
  }

  public void insertMemory(List<MemoryProfiler.MemoryData.MemorySample> samples) {
    synchronized (myDataQueryLock) {
      for (MemoryProfiler.MemoryData.MemorySample sample : samples) {
        execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.MEMORY.ordinal(), sample.getTimestamp(), sample.toByteArray());
      }
    }
  }

  public void insertVmStats(List<MemoryProfiler.MemoryData.VmStatsSample> samples) {
    synchronized (myDataQueryLock) {
      for (MemoryProfiler.MemoryData.VmStatsSample sample : samples) {
        execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.VMSTATS.ordinal(), sample.getTimestamp(), sample.toByteArray());
      }
    }
  }

  public void insertAllocation(List<MemoryProfiler.MemoryData.AllocationEvent> samples) {
    synchronized (myDataQueryLock) {
      for (MemoryProfiler.MemoryData.AllocationEvent sample : samples) {
        execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.ALLOCATION_EVENT.ordinal(), sample.getTimestamp(), sample.toByteArray());
      }
    }
  }

  public void insertAllocationDumpData(AllocationsInfo info, byte[] data) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.UPDATE_ALLOCATIONS_INFO, info.toByteArray(), data, info.getInfoId());
    }
  }

  public void insert(MemoryProfiler.HeapDumpInfo info) {
    synchronized (myDataQueryLock) {
      MemoryProfiler.DumpDataResponse.Status status = MemoryProfiler.DumpDataResponse.Status.NOT_READY;
      long startTime = info.getStartTime();
      if (startTime == 0) {
        startTime = info.getEndTime();
      }
      execute(MemoryStatements.INSERT_HEAP, info.getDumpId(), startTime, info.getEndTime(), status.getNumber(), info.toByteArray());
    }
  }

  public void insert(MemoryProfiler.MemoryData.AllocationEvent event) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.ALLOCATION_EVENT.ordinal(), event.getTimestamp(), event.toByteArray());
    }
  }

  public void insertIfNotExist(String className, MemoryProfiler.AllocatedClass clazz) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.INSERT_ALLOCATED_CLASS, className, clazz.toByteArray());
    }
  }

  public void insertIfNotExist(ByteString id, MemoryProfiler.AllocationStack stack) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.INSERT_ALLOCATION_STACK, id.toByteArray(), stack.toByteArray());
    }
  }

  public void insertAndUpdateAllocationInfo(List<AllocationsInfo> infos) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.REMOVE_UNFINISHED_ALLOCAITON_INFO, DurationData.UNSPECIFIED_DURATION);
      for (MemoryProfiler.AllocationsInfo info : infos) {
        execute(MemoryStatements.INSERT_ALLOCATION_INFO, info.getInfoId(), info.getStartTime(), info.getEndTime(), info.toByteArray());
      }
    }
  }

  public List<MemoryProfiler.AllocationStack> getAllocationStacksForRequest(MemoryProfiler.AllocationContextsRequest request) {
    synchronized (myDataQueryLock) {
      List<MemoryProfiler.AllocationStack> datas = new ArrayList<>();
      try {
        ResultSet resultSet = executeQuery(MemoryStatements.QUERY_ALLOCATION_STACK);
        while (resultSet.next()) {
          MemoryProfiler.AllocationStack data = MemoryProfiler.AllocationStack.newBuilder().mergeFrom(resultSet.getBytes(1)).build();
          datas.add(data);
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
      return datas;
    }
  }

  public List<MemoryProfiler.AllocatedClass> getAllocatedClassesForRequest(MemoryProfiler.AllocationContextsRequest request) {
    synchronized (myDataQueryLock) {
      List<MemoryProfiler.AllocatedClass> datas = new ArrayList<>();
      try {
        ResultSet resultSet = executeQuery(MemoryStatements.QUERY_ALLOCATED_CLASS);
        while (resultSet.next()) {
          MemoryProfiler.AllocatedClass data = MemoryProfiler.AllocatedClass.newBuilder().mergeFrom(resultSet.getBytes(1)).build();
          datas.add(data);
        }
      }
      catch (InvalidProtocolBufferException | SQLException ex) {
        getLogger().error(ex);
      }
      return datas;
    }
  }

  public MemoryProfiler.DumpDataResponse.Status getHeapDumpStatus(int dumpId) {
    synchronized (myDataQueryLock) {
      try {
        ResultSet result = executeQuery(MemoryStatements.FIND_HEAP_STATUS, dumpId);
        return MemoryProfiler.DumpDataResponse.Status.forNumber(result.getInt(1));
      }
      catch (SQLException ex) {
        getLogger().error(ex);
      }
      return MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN;
    }
  }

  public void insertHeapDumpData(MemoryProfiler.DumpDataResponse.Status status, MemoryProfiler.HeapDumpInfo info, ByteString data) {
    synchronized (myDataQueryLock) {
      execute(MemoryStatements.UPDATE_HEAP_INFO, data.toByteArray(), info.toByteArray(), status.getNumber(), info.getDumpId());
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
