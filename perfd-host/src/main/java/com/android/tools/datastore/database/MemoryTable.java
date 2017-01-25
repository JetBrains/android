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
import java.util.*;
import java.util.stream.Collectors;

public class MemoryTable extends DatastoreTable<MemoryTable.MemoryStatements> {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryTable.class);
  }

  //TODO: Remove when we update SQLite database
  protected final List<MemoryProfiler.MemoryData.MemorySample> myMemoryData = new ArrayList<>();
  protected final List<MemoryProfiler.MemoryData.VmStatsSample> myStatsData = new ArrayList<>();
  protected final List<HeapDumpSample> myHeapData = new ArrayList<>();
  protected final List<MemoryProfiler.MemoryData.AllocationEvent> myAllocationEvents = new ArrayList<>();
  protected final List<MemoryProfiler.AllocationsInfo> myAllocationsInfos = new ArrayList<>();
  protected final Map<String, MemoryProfiler.AllocatedClass> myAllocatedClasses = new HashMap<>();
  protected final Map<ByteString, MemoryProfiler.AllocationStack> myAllocationStacks = new HashMap<>();
  private final Object myUpdatingDataLock = new Object();
  private final Object myUpdatingAllocationsLock = new Object();

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
    FIND_HEAP_STATUS,
    FIND_ALLOCATION_INFO

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
      createTable("Memory_AllocationInfo", "Id INTEGER, StartTime INTEGER", "EndTime INTEGER", "Data BLOB", "PRIMARY KEY(Id)");
      createTable("Memory_AllocationStack", "Id BLOB", "Data BLOB");
      createTable("Memory_AllocatedClass", "Name TEXT", "Data BLOB");
      createTable("Memory_HeapDump", "DumpId, INTEGER", "StartTime INTEGER", "EndTime INTEGER", "Status INTEGER", "InfoData BLOB",
                  "DumpData BLOB", "PRIMARY KEY(DumpId)");
      createIndex("Memory_HeapDump", "DumpId");
      createIndex("Memory_AllocationInfo", "Id");
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
                      "INSERT OR REPLACE INTO Memory_AllocationInfo (Id, StartTime, EndTime, Data) VALUES (?, ?, ?, ?)");
      createStatement(MemoryStatements.FIND_ALLOCATION_INFO, "SELECT Data from Memory_AllocationInfo WHERE Id = ?");
      createStatement(MemoryStatements.REMOVE_UNFINISHED_ALLOCAITON_INFO, "DELETE FROM Memory_AllocationInfo WHERE EndTime = ?");

      createStatement(MemoryStatements.INSERT_ALLOCATION_STACK, "INSERT INTO Memory_AllocationStack (Id, Data) VALUES (?, ?)");
      createStatement(MemoryStatements.INSERT_ALLOCATED_CLASS, "INSERT INTO Memory_AllocatedClass (Name, Data) VALUES (?, ?)");

      createStatement(MemoryStatements.QUERY_ALLOCATION_STACK, "Select Data FROM Memory_AllocationStack");
      createStatement(MemoryStatements.QUERY_ALLOCATED_CLASS, "Select Data FROM Memory_AllocatedClass");

      createStatement(MemoryStatements.QUERY_ALLOCATION_INFO,
                      "SELECT Data FROM Memory_AllocationInfo WHERE (EndTime = ? OR EndTime > ?) AND StartTime <= ?");
    }
    catch (SQLException ex) {
      getLogger().error(ex);
    }
  }

  public byte[] getHeapDumpData(int dumpId, MemoryProfiler.HeapDumpInfo.Builder out_info) {
    int index = Collections
      .binarySearch(myHeapData, new HeapDumpSample(dumpId), (o1, o2) -> o1.myInfo.getDumpId() - o2.myInfo.getDumpId());
    if (index < 0) {
      return null;
    }
    else {
      HeapDumpSample sample = myHeapData.get(index);
      out_info.mergeFrom(sample.myInfo);
      if (sample.myData != null) {
        return sample.myData.toByteArray();
      }
      return null;
    }
    //try {
    //  ResultSet resultSet = executeQuery(MemoryStatements.FIND_HEAP_DATA, dumpId);
    //  if (resultSet.next()) {
    //    out_info.mergeFrom(resultSet.getBytes(1));
    //    return resultSet.getBytes(2);
    //  }
    //} catch (InvalidProtocolBufferException | SQLException ex) {
    //  getLogger().error(ex);
    //}
    //return null;
  }

  private List<MemoryProfiler.HeapDumpInfo> getHeapDumpInfo(long startTime, long endTime) {
    List<MemoryProfiler.HeapDumpInfo> results = new ArrayList<>();
    synchronized (myUpdatingDataLock) {
      HeapDumpSample key = new HeapDumpSample(MemoryProfiler.HeapDumpInfo.newBuilder().setEndTime(startTime).build());
      int index =
        Collections.binarySearch(myHeapData, key, (left, right) -> compareTimes(left.myInfo.getEndTime(), right.myInfo.getEndTime()));
      // If there is an exact match, move on to the next index as start time is treated as exclusive.
      index = index < 0 ? -(index + 1) : index + 1;
      for (int i = index; i < myHeapData.size(); i++) {
        HeapDumpSample sample = myHeapData.get(i);
        assert sample.myInfo.getEndTime() == DurationData.UNSPECIFIED_DURATION || startTime < sample.myInfo.getEndTime();
        if (sample.myInfo.getStartTime() <= endTime) {
          results.add(sample.myInfo);
        }
      }
    }
    return results;
  }

  public MemoryProfiler.MemoryData getData(MemoryProfiler.MemoryRequest request) {
    MemoryProfiler.MemoryData.Builder response = MemoryProfiler.MemoryData.newBuilder();
    synchronized (myUpdatingDataLock) {
      synchronized (myUpdatingAllocationsLock) {
        response.addAllMemSamples(getMemoryDataByRequest(request));
        response.addAllVmStatsSamples(getVmStatsDataByRequest(request));
        response.addAllHeapDumpInfos(getHeapDumpInfoByRequest(request));
        response.addAllAllocationsInfo(getAllocationInfoByRequest(request));
        response.addAllAllocationEvents(getAllocationEventsByRequest(request));
      }
    }
    return response.build();
  }

  public List<MemoryProfiler.HeapDumpInfo> getHeapDumpInfoByRequest(MemoryProfiler.ListDumpInfosRequest request) {
    return getHeapDumpInfo(request.getStartTime(), request.getEndTime());
    //return getResultsInfo(MemoryStatements.QUERY_HEAP_INFO, true, request.getStartTime(), request.getEndTime(), MemoryProfiler.HeapDumpInfo.getDefaultInstance());
  }

  public List<MemoryProfiler.HeapDumpInfo> getHeapDumpInfoByRequest(MemoryProfiler.MemoryRequest request) {
    return getHeapDumpInfo(request.getStartTime(), request.getEndTime());
    //return getResultsInfo(MemoryStatements.QUERY_HEAP_INFO, true, request.getStartTime(), request.getEndTime(), MemoryProfiler.HeapDumpInfo.getDefaultInstance());
  }

  public List<MemoryProfiler.MemoryData.MemorySample> getMemoryDataByRequest(MemoryProfiler.MemoryRequest request) {
    List<MemoryProfiler.MemoryData.MemorySample> results = new ArrayList<>();
    synchronized (myUpdatingDataLock) {
      myMemoryData.stream().filter(obj -> obj.getTimestamp() > request.getStartTime() && obj.getTimestamp() <= request.getEndTime())
        .collect(
          Collectors.toCollection(() -> results));
    }
    return results;
    //return getResultsInfo(MemoryStatements.QUERY_MEMORY, true, request.getStartTime(), request.getEndTime(), MemoryProfiler.MemoryData.MemorySample.getDefaultInstance());
  }

  public List<MemoryProfiler.MemoryData.VmStatsSample> getVmStatsDataByRequest(MemoryProfiler.MemoryRequest request) {
    List<MemoryProfiler.MemoryData.VmStatsSample> results = new ArrayList<>();
    synchronized (myUpdatingDataLock) {
      myStatsData.stream().filter(obj -> obj.getTimestamp() > request.getStartTime() && obj.getTimestamp() <= request.getEndTime()).collect(
        Collectors.toCollection(() -> results));
    }
    return results;
    //return getResultsInfo(MemoryStatements.QUERY_VMSTATS, true, request.getStartTime(), request.getEndTime(), MemoryProfiler.MemoryData.VmStatsSample.getDefaultInstance());
  }

  public List<MemoryProfiler.AllocationsInfo> getAllocationInfoByRequest(MemoryProfiler.MemoryRequest request) {
    List<MemoryProfiler.AllocationsInfo> results = new ArrayList<>();
    synchronized (myUpdatingAllocationsLock) {
      // TODO: Handle the case where info.getEndTime() == UNSPECIFIED_DURATION
      myAllocationsInfos.stream()
        .filter(info -> (info.getStartTime() > request.getStartTime() && info.getStartTime() <= request.getEndTime()) ||
                        (info.getEndTime() > request.getStartTime() && info.getEndTime() <= request.getEndTime()))
        .collect(Collectors.toCollection(() -> results));
    }
    return results;
    //return getResultsInfo(MemoryStatements.QUERY_ALLOCATION_INFO, true, request.getStartTime(), request.getEndTime(), MemoryProfiler.AllocationsInfo.getDefaultInstance());
  }

  public List<MemoryProfiler.MemoryData.AllocationEvent> getAllocationEventsByRequest(MemoryProfiler.MemoryRequest request) {
    List<MemoryProfiler.MemoryData.AllocationEvent> results = new ArrayList<>();
    synchronized (myUpdatingAllocationsLock) {
      myAllocationEvents.stream()
        .filter(event -> event.getTimestamp() > request.getStartTime() && event.getTimestamp() <= request.getEndTime())
        .collect(Collectors.toCollection(() -> results));
    }
    return results;
    //return getResultsInfo(MemoryStatements.QUERY_ALLOCATION_EVENT, true, request.getStartTime(), request.getEndTime(), MemoryProfiler.MemoryData.AllocationEvent.getDefaultInstance());
  }

  public MemoryProfiler.GetAllocationsInfoStatusResponse getAllocationInfoStatus(int id) {
    MemoryProfiler.GetAllocationsInfoStatusResponse response = MemoryProfiler.GetAllocationsInfoStatusResponse.getDefaultInstance();
    synchronized (myUpdatingAllocationsLock) {
      for (MemoryProfiler.AllocationsInfo info : myAllocationsInfos) {
        if (id == info.getInfoId()) {
          response =
            MemoryProfiler.GetAllocationsInfoStatusResponse.newBuilder().setInfoId(info.getInfoId()).setStatus(info.getStatus()).build();
          break;
        }
      }
    }
    return response;
    /*
    ResultSet results = executeQuery(MemoryStatements.FIND_ALLOCATION_INFO, id);
    MemoryProfiler.GetAllocationsInfoStatusResponse.Builder response = MemoryProfiler.GetAllocationsInfoStatusResponse.newBuilder();
    try {
      if (results.next()) {
        MemoryProfiler.AllocationsInfo.Status status = MemoryProfiler.AllocationsInfo.newBuilder().mergeFrom(results.getBytes(1)).getStatus();
        response.setInfoId(id);
        response.setStatus(status);
      }
    } catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    return response.build();
    */
  }

  public void updateAllocationInfo(int id, MemoryProfiler.AllocationsInfo.Status status) {
    // Find the cached AllocationInfo and update its status.
    synchronized (myUpdatingAllocationsLock) {
      for (int i = myAllocationsInfos.size() - 1; i >= 0; i--) {
        MemoryProfiler.AllocationsInfo info = myAllocationsInfos.get(i);
        if (id == info.getInfoId()) {
          assert info.getLegacyTracking();
          info = info.toBuilder().setStatus(status).build();
          myAllocationsInfos.set(i, info);
          break;
        }
      }
    }
    /*
    ResultSet results = executeQuery(MemoryStatements.FIND_ALLOCATION_INFO, id);
    try {
      if (results.next()) {
        MemoryProfiler.AllocationsInfo info = MemoryProfiler.AllocationsInfo.newBuilder().mergeFrom(results.getBytes(1))
          .setStatus(status)
          .build();
        execute(MemoryStatements.INSERT_ALLOCATION_INFO, info.getInfoId(), info.getStartTime(), info.getEndTime(), info.toByteArray());
      }
    } catch (InvalidProtocolBufferException | SQLException ex) {
      getLogger().error(ex);
    }
    */
  }

  public void insertMemory(List<MemoryProfiler.MemoryData.MemorySample> samples) {
    synchronized (myUpdatingDataLock) {
      myMemoryData.addAll(samples);
    }
    //for (MemoryProfiler.MemoryData.MemorySample sample : samples) {
    //  execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.MEMORY.ordinal(), sample.getTimestamp(), sample.toByteArray());
    //}
  }

  public void insertVmStats(List<MemoryProfiler.MemoryData.VmStatsSample> samples) {
    synchronized (myUpdatingDataLock) {
      myStatsData.addAll(samples);
    }
    //for (MemoryProfiler.MemoryData.VmStatsSample sample : samples) {
    //  execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.VMSTATS.ordinal(), sample.getTimestamp(), sample.toByteArray());
    //}
  }

  public void insertAllocation(List<MemoryProfiler.MemoryData.AllocationEvent> samples) {
    synchronized (myUpdatingAllocationsLock) {
      myAllocationEvents.addAll(samples);
    }
    //for (MemoryProfiler.MemoryData.AllocationEvent sample : samples) {
    //  execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.ALLOCATION_EVENT.ordinal(), sample.getTimestamp(), sample.toByteArray());
    //}
  }

  public void insert(MemoryProfiler.HeapDumpInfo info) {
    HeapDumpSample sample = new HeapDumpSample(info);
    myHeapData.add(sample);

    //MemoryProfiler.DumpDataResponse.Status status = MemoryProfiler.DumpDataResponse.Status.NOT_READY;
    //long startTime = info.getStartTime();
    //if (startTime == 0) {
    //  startTime = info.getEndTime();
    //}
    //execute(MemoryStatements.INSERT_HEAP, info.getDumpId(), startTime, info.getEndTime(), status.getNumber(), info.toByteArray());
  }

  public void insert(MemoryProfiler.MemoryData.AllocationEvent event) {
    synchronized (myUpdatingAllocationsLock) {
      myAllocationEvents.add(event);
    }
    //execute(MemoryStatements.INSERT_SAMPLE, MemorySamplesType.ALLOCATION_EVENT.ordinal(), event.getTimestamp(), event.toByteArray());
  }

  public void insertIfNotExist(String className, MemoryProfiler.AllocatedClass clazz) {
    synchronized (myUpdatingAllocationsLock) {
      myAllocatedClasses.putIfAbsent(className, clazz);
    }
    //execute(MemoryStatements.INSERT_ALLOCATED_CLASS, className, clazz.toByteArray());
  }

  public void insertIfNotExist(ByteString id, MemoryProfiler.AllocationStack stack) {
    synchronized (myUpdatingAllocationsLock) {
      myAllocationStacks.putIfAbsent(id, stack);
    }
    //execute(MemoryStatements.INSERT_ALLOCATION_STACK, id.toByteArray(), stack.toByteArray());
  }

  public void insertAndUpdateAllocationInfo(List<MemoryProfiler.AllocationsInfo> infos) {
    int startAppendIndex = 0;
    synchronized (myUpdatingAllocationsLock) {
      int lastEntryIndex = myAllocationsInfos.size() - 1;
      if (lastEntryIndex >= 0 && myAllocationsInfos.get(lastEntryIndex).getEndTime() == DurationData.UNSPECIFIED_DURATION) {
        MemoryProfiler.AllocationsInfo lastOriginalEntry = myAllocationsInfos.get(lastEntryIndex);
        MemoryProfiler.AllocationsInfo firstIncomingEntry = infos.get(0);
        assert infos.get(0).getInfoId() == lastOriginalEntry.getInfoId();
        assert infos.get(0).getStartTime() == lastOriginalEntry.getStartTime();
        myAllocationsInfos.set(lastEntryIndex, firstIncomingEntry);
        startAppendIndex = 1;
      }
      for (int i = startAppendIndex; i < infos.size(); i++) {
        MemoryProfiler.AllocationsInfo info = infos.get(i);
        myAllocationsInfos.add(info);
      }
    }

    //execute(MemoryStatements.REMOVE_UNFINISHED_ALLOCAITON_INFO, DurationData.UNSPECIFIED_DURATION);
    //for (MemoryProfiler.AllocationsInfo info : infos) {
    //  execute(MemoryStatements.INSERT_ALLOCATION_INFO, info.getInfoId(), info.getStartTime(), info.getEndTime(), info.toByteArray());
    //}
  }

  public List<MemoryProfiler.AllocationStack> getAllocationStacksForRequest(MemoryProfiler.AllocationContextsRequest request) {
    synchronized (myUpdatingAllocationsLock) {
      return new ArrayList(myAllocationStacks.values());
    }
    //List<MemoryProfiler.AllocationStack> datas = new ArrayList<>();
    //try {
    //  ResultSet resultSet = executeQuery(MemoryStatements.QUERY_ALLOCATION_STACK);
    //  if (resultSet.next()) {
    //    MemoryProfiler.AllocationStack data = MemoryProfiler.AllocationStack.newBuilder().mergeFrom(resultSet.getBytes(1)).build();
    //    datas.add(data);
    //  }
    //} catch (InvalidProtocolBufferException | SQLException ex) {
    //  getLogger().error(ex);
    //}
    //return datas;
  }

  public List<MemoryProfiler.AllocatedClass> getAllocatedClassesForRequest(MemoryProfiler.AllocationContextsRequest request) {
    synchronized (myUpdatingAllocationsLock) {
      return new ArrayList<>(myAllocatedClasses.values());
    }
    //List<MemoryProfiler.AllocatedClass> datas = new ArrayList<>();
    //try {
    //  ResultSet resultSet = executeQuery(MemoryStatements.QUERY_ALLOCATED_CLASS);
    //  if (resultSet.next()) {
    //    MemoryProfiler.AllocatedClass data = MemoryProfiler.AllocatedClass.newBuilder().mergeFrom(resultSet.getBytes(1)).build();
    //    datas.add(data);
    //  }
    //}
    //catch (InvalidProtocolBufferException | SQLException ex) {
    //  getLogger().error(ex);
    //}
    //return datas;
  }

  public MemoryProfiler.DumpDataResponse.Status getHeapDumpStatus(int dumpId) {
    int index = Collections
      .binarySearch(myHeapData, new HeapDumpSample(dumpId), (o1, o2) -> o1.myInfo.getDumpId() - o2.myInfo.getDumpId());
    assert index >= 0;
    HeapDumpSample dump = myHeapData.get(index);
    if (dump == null) {
      return MemoryProfiler.DumpDataResponse.Status.NOT_READY;
    }
    if (dump.isError) {
      return MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN;
    }
    return MemoryProfiler.DumpDataResponse.Status.SUCCESS;

    //try {
    //  ResultSet result = executeQuery(MemoryStatements.FIND_HEAP_STATUS, dumpId);
    //  return MemoryProfiler.DumpDataResponse.Status.forNumber(result.getInt(1));
    //} catch (SQLException ex) {
    //  getLogger().error(ex);
    //}
    //return MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN;
  }

  public void insertDumpData(MemoryProfiler.DumpDataResponse.Status status, MemoryProfiler.HeapDumpInfo info, ByteString data) {
    int index = Collections
      .binarySearch(myHeapData, new HeapDumpSample(info.getDumpId()), (o1, o2) -> o1.myInfo.getDumpId() - o2.myInfo.getDumpId());
    assert index >= 0;
    HeapDumpSample dump = myHeapData.get(index);
    dump.myInfo = info;
    synchronized (myUpdatingDataLock) {
      if (status == MemoryProfiler.DumpDataResponse.Status.SUCCESS) {
        dump.myData = data;
      }
      else {
        dump.isError = true;
      }
    }
    //execute(MemoryStatements.UPDATE_HEAP_INFO, data.toByteArray(), info.toByteArray(), status.getNumber(), info.getDumpId());
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

  private static int compareTimes(long left, long right) {
    if (left == DurationData.UNSPECIFIED_DURATION) {
      return 1;
    }
    else if (right == DurationData.UNSPECIFIED_DURATION) {
      return -1;
    }
    else {
      long diff = left - right;
      return diff == 0 ? 0 : (diff < 0 ? -1 : 1); // diff >> 63 sign extends value into a mask, the bit-or deals with 0+ case
    }
  }

  private static class HeapDumpSample {
    @NotNull public MemoryProfiler.HeapDumpInfo myInfo;
    @Nullable public volatile ByteString myData = null;
    public volatile boolean isError = false;

    private HeapDumpSample(@NotNull MemoryProfiler.HeapDumpInfo info) {
      myInfo = info;
    }

    public HeapDumpSample(int id) {
      myInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setDumpId(id).build();
    }
  }
}
