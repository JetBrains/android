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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertThat;

public class MemoryStatsTableTest extends DatabaseTest<MemoryStatsTable> {
  private static final Common.Session VALID_SESSION = Common.Session.newBuilder().setSessionId(1L).setDeviceId(1234).setPid(1).build();
  private static final Common.Session INVALID_SESSION = Common.Session.newBuilder().setSessionId(-1L).setDeviceId(4321).setPid(-1).build();

  @Override
  protected MemoryStatsTable createTable() {
    return new MemoryStatsTable();
  }

  @Override
  protected List<Consumer<MemoryStatsTable>> getTableQueryMethodsForVerification() {
    List<Consumer<MemoryStatsTable>> methodCalls = new ArrayList<>();
    Common.Session session = Common.Session.getDefaultInstance();
    methodCalls.add((table) -> assertThat(table.getAllocationsInfo(session, 0)).isNull());
    methodCalls.add((table) -> assertThat(table.getHeapDumpData(session, 0)).isNull());
    methodCalls.add((table) -> assertThat(table.getHeapDumpInfoByRequest(session, ListDumpInfosRequest.getDefaultInstance())).isEmpty());
    methodCalls.add((table) -> assertThat(table.getHeapDumpStatus(session, 0)).isEqualTo(DumpDataResponse.Status.NOT_FOUND));
    methodCalls.add(
      (table) -> assertThat(table.getLegacyAllocationContexts(LegacyAllocationContextsRequest.newBuilder().addClassIds(1).build()))
        .isEqualTo(AllocationContextsResponse.getDefaultInstance()));
    methodCalls.add((table) -> assertThat(table.getLegacyAllocationData(session, 0)).isNull());
    methodCalls.add((table) -> assertThat(table.getLegacyAllocationDumpData(session, 0)).isNull());
    methodCalls.add((table) -> {
      ArrayList<MemoryData.AllocStatsSample> stats = new ArrayList<>();
      stats.add(MemoryData.AllocStatsSample.getDefaultInstance());
      table.insertAllocStats(session, stats);
    });
    methodCalls.add((table) -> {
      ArrayList<MemoryData.GcStatsSample> samples = new ArrayList<>();
      samples.add(MemoryData.GcStatsSample.getDefaultInstance());
      table.insertGcStats(session, samples);
    });
    methodCalls.add((table) -> table.insertHeapDumpData(session, 0, DumpDataResponse.Status.FAILURE_UNKNOWN, ByteString.EMPTY));
    methodCalls.add((table) -> {
      List<AllocatedClass> classes = new ArrayList<>();
      classes.add(AllocatedClass.getDefaultInstance());
      table.insertLegacyAllocationContext(session, classes, new ArrayList<>());
    });
    methodCalls.add((table) -> {
      List<MemoryData.MemorySample> samples = new ArrayList<>();
      samples.add(MemoryData.MemorySample.getDefaultInstance());
      table.insertMemory(session, samples);
    });
    methodCalls.add((table) -> table.insertOrReplaceAllocationsInfo(session, AllocationsInfo.getDefaultInstance()));
    methodCalls.add((table) -> table.insertOrReplaceHeapInfo(session, HeapDumpInfo.getDefaultInstance()));
    methodCalls.add((table) -> table.updateLegacyAllocationDump(session, 0, null));
    methodCalls.add((table) -> table.updateLegacyAllocationEvents(session, 0, LegacyAllocationEventsResponse.getDefaultInstance()));
    methodCalls.add((table) -> table.getData(MemoryRequest.getDefaultInstance()));
    return methodCalls;
  }

  @Test
  public void testInsertAndGetData() {
    /*
     * Insert a cascading sequence of sample data into the database:
     * Timestamp:     0 1 2 3 4 5 6 7 8 9
     * mem              |
     * allocStats         |
     * ongoing heap         |---------->
     * finished heap          |-|
     * ongoing alloc              |---->
     * finished alloc               |-|
     * gcStats                        |-|
     */
    MemoryData.MemorySample memSample = MemoryData.MemorySample.newBuilder().setTimestamp(1).build();
    MemoryData.AllocStatsSample allocStatsSample = MemoryData.AllocStatsSample.newBuilder().setTimestamp(2).build();
    HeapDumpInfo ongoingHeapSample =
      HeapDumpInfo.newBuilder().setStartTime(3).setEndTime(Long.MAX_VALUE).build();
    HeapDumpInfo finishedHeapSample = HeapDumpInfo.newBuilder().setStartTime(4).setEndTime(5).build();
    AllocationsInfo ongoingAllocSample =
      AllocationsInfo.newBuilder().setStartTime(6).setEndTime(Long.MAX_VALUE).build();
    AllocationsInfo finishedAllocSample = AllocationsInfo.newBuilder().setStartTime(7).setEndTime(8).build();
    MemoryData.GcStatsSample gcStatsSample = MemoryData.GcStatsSample.newBuilder().setStartTime(8).setEndTime(9).build();

    getTable().insertMemory(VALID_SESSION, Collections.singletonList(memSample));
    getTable().insertAllocStats(VALID_SESSION, Collections.singletonList(allocStatsSample));
    getTable().insertGcStats(VALID_SESSION, Collections.singletonList(gcStatsSample));
    getTable().insertOrReplaceHeapInfo(VALID_SESSION, finishedHeapSample);
    getTable().insertOrReplaceHeapInfo(VALID_SESSION, ongoingHeapSample);
    getTable().insertOrReplaceAllocationsInfo(VALID_SESSION, ongoingAllocSample);
    getTable().insertOrReplaceAllocationsInfo(VALID_SESSION, finishedAllocSample);

    // Perform a sequence of queries to ensure we are getting startTime-exclusive and endTime-inclusive data.
    MemoryData result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(-1).setEndTime(0).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 0, 0);

    result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(0).setEndTime(1).build());
    verifyMemoryDataResultCounts(result, 1, 0, 0, 0, 0);
    assertThat(result.getMemSamples(0)).isEqualTo(memSample);

    result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(1).setEndTime(2).build());
    verifyMemoryDataResultCounts(result, 0, 1, 0, 0, 0);
    assertThat(result.getAllocStatsSamples(0)).isEqualTo(allocStatsSample);

    result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(2).setEndTime(3).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 0);
    assertThat(result.getHeapDumpInfos(0)).isEqualTo(ongoingHeapSample);

    result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(3).setEndTime(4).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 2, 0);
    assertThat(result.getHeapDumpInfosList()).contains(ongoingHeapSample);
    assertThat(result.getHeapDumpInfosList()).contains(finishedHeapSample);

    result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(4).setEndTime(5).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 2, 0);
    assertThat(result.getHeapDumpInfosList()).contains(ongoingHeapSample);
    assertThat(result.getHeapDumpInfosList()).contains(finishedHeapSample);

    result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(5).setEndTime(6).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 1);
    assertThat(result.getHeapDumpInfos(0)).isEqualTo(ongoingHeapSample);
    assertThat(result.getAllocationsInfo(0)).isEqualTo(ongoingAllocSample);

    result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(6).setEndTime(7).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 2);
    assertThat(result.getHeapDumpInfos(0)).isEqualTo(ongoingHeapSample);
    assertThat(result.getAllocationsInfoList()).contains(ongoingAllocSample);
    assertThat(result.getAllocationsInfoList()).contains(finishedAllocSample);

    result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(7).setEndTime(8).build());
    verifyMemoryDataResultCounts(result, 0, 0, 1, 1, 2);
    assertThat(result.getGcStatsSamples(0)).isEqualTo(gcStatsSample);
    assertThat(result.getHeapDumpInfos(0)).isEqualTo(ongoingHeapSample);
    assertThat(result.getAllocationsInfoList()).contains(ongoingAllocSample);
    assertThat(result.getAllocationsInfoList()).contains(finishedAllocSample);

    result = getTable().getData(MemoryRequest.newBuilder().setSession(VALID_SESSION).setStartTime(8).setEndTime(9).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 1, 1);
    assertThat(result.getHeapDumpInfos(0)).isEqualTo(ongoingHeapSample);
    assertThat(result.getAllocationsInfo(0)).isEqualTo(ongoingAllocSample);

    // Test that querying for an invalid session returns no data.
    result = getTable().getData(MemoryRequest.newBuilder().setSession(INVALID_SESSION).setStartTime(0).setEndTime(9).build());
    verifyMemoryDataResultCounts(result, 0, 0, 0, 0, 0);
  }

  @Test
  public void testHeapDumpQueriesAfterInsertion() {
    HeapDumpInfo sample = HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(0).build();
    getTable().insertOrReplaceHeapInfo(VALID_SESSION, sample);

    // Test that Status is set to NOT_READY and dump data is null
    assertThat(getTable().getHeapDumpStatus(VALID_SESSION, sample.getStartTime())).isEqualTo(DumpDataResponse.Status.NOT_READY);
    assertThat(getTable().getHeapDumpData(VALID_SESSION, sample.getStartTime())).isNull();

    // Update the HeapInfo with status and data and test that they returned correctly
    byte[] rawBytes = new byte[]{'a', 'b', 'c'};
    getTable()
      .insertHeapDumpData(VALID_SESSION, sample.getStartTime(), DumpDataResponse.Status.SUCCESS, ByteString.copyFrom(rawBytes));

    assertThat(getTable().getHeapDumpStatus(VALID_SESSION, sample.getStartTime())).isEqualTo(DumpDataResponse.Status.SUCCESS);
    assertThat(getTable().getHeapDumpData(VALID_SESSION, sample.getStartTime())).isEqualTo(rawBytes);

    // Test that querying for the invalid app id returns NOT FOUND
    assertThat(getTable().getHeapDumpStatus(INVALID_SESSION, sample.getStartTime())).isEqualTo(DumpDataResponse.Status.NOT_FOUND);
    assertThat(getTable().getHeapDumpData(INVALID_SESSION, sample.getStartTime())).isNull();

    // Test that querying for the invalid session returns NOT FOUND
    assertThat(getTable().getHeapDumpStatus(INVALID_SESSION, sample.getStartTime())).isEqualTo(DumpDataResponse.Status.NOT_FOUND);
    assertThat(getTable().getHeapDumpData(INVALID_SESSION, sample.getStartTime())).isNull();
  }

  @Test
  public void testLegacyAllocationsQueriesAfterInsertion() {
    AllocationsInfo sample = AllocationsInfo.newBuilder().setStartTime(1).setEndTime(2).build();
    getTable().insertOrReplaceAllocationsInfo(VALID_SESSION, sample);

    // Tests that the info has been inserted into table, but the event response + dump data are still null
    assertThat(getTable().getAllocationsInfo(VALID_SESSION, sample.getStartTime())).isEqualTo(sample);
    assertThat(getTable().getLegacyAllocationData(VALID_SESSION, sample.getStartTime())).isNull();
    assertThat(getTable().getLegacyAllocationDumpData(VALID_SESSION, sample.getStartTime())).isNull();

    int stackId = 1;
    LegacyAllocationEventsResponse events = LegacyAllocationEventsResponse
      .newBuilder()
      .addEvents(LegacyAllocationEvent.newBuilder().setClassId(1).setStackId(stackId))
      .addEvents(LegacyAllocationEvent.newBuilder().setClassId(2).setStackId(stackId)).build();
    getTable().updateLegacyAllocationEvents(VALID_SESSION, sample.getStartTime(), events);
    assertThat(getTable().getLegacyAllocationData(VALID_SESSION, sample.getStartTime())).isEqualTo(events);

    byte[] rawBytes = new byte[]{'d', 'e', 'f'};
    getTable().updateLegacyAllocationDump(VALID_SESSION, sample.getStartTime(), rawBytes);
    assertThat(getTable().getLegacyAllocationDumpData(VALID_SESSION, sample.getStartTime())).isEqualTo(rawBytes);

    // Test that querying for the invalid app id returns null
    assertThat(getTable().getAllocationsInfo(INVALID_SESSION, sample.getStartTime())).isNull();
    assertThat(getTable().getLegacyAllocationData(INVALID_SESSION, sample.getStartTime())).isNull();
    assertThat(getTable().getLegacyAllocationDumpData(INVALID_SESSION, sample.getStartTime())).isNull();

    // Test that querying for the invalid session returns null
    assertThat(getTable().getAllocationsInfo(INVALID_SESSION, sample.getStartTime())).isNull();
    assertThat(getTable().getLegacyAllocationData(INVALID_SESSION, sample.getStartTime())).isNull();
    assertThat(getTable().getLegacyAllocationDumpData(INVALID_SESSION, sample.getStartTime())).isNull();
  }

  @Test
  public void testLegacyAllocationContextQueriesAfterInsertion() {
    int classId1 = 1;
    int classId2 = 2;
    int stackId3 = 3;
    int stackId4 = 4;

    AllocatedClass class1 = AllocatedClass.newBuilder().setClassId(classId1).setClassName("Class1").build();
    AllocatedClass class2 = AllocatedClass.newBuilder().setClassId(classId2).setClassName("Class2").build();
    AllocationStack stack1 = AllocationStack.newBuilder().setStackId(stackId3).build();
    AllocationStack stack2 = AllocationStack.newBuilder().setStackId(stackId4).build();
    getTable().insertLegacyAllocationContext(VALID_SESSION, Arrays.asList(class1, class2), Arrays.asList(stack1, stack2));

    LegacyAllocationContextsRequest request =
      LegacyAllocationContextsRequest.newBuilder().setSession(VALID_SESSION).addClassIds(classId1)
                                     .addStackIds(stackId4).build();
    AllocationContextsResponse response = getTable().getLegacyAllocationContexts(request);
    assertThat(response.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(response.getAllocationStacksCount()).isEqualTo(1);
    assertThat(response.getAllocatedClasses(0)).isEqualTo(class1);
    assertThat(response.getAllocationStacks(0)).isEqualTo(stack2);

    request = LegacyAllocationContextsRequest.newBuilder().setSession(VALID_SESSION).addClassIds(classId2)
                                             .addStackIds(stackId3).build();
    response = getTable().getLegacyAllocationContexts(request);
    assertThat(response.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(response.getAllocationStacksCount()).isEqualTo(1);
    assertThat(response.getAllocatedClasses(0)).isEqualTo(class2);
    assertThat(response.getAllocationStacks(0)).isEqualTo(stack1);
  }

  @Test
  public void testAllocationContextNotFound() {
    LegacyAllocationContextsRequest request = LegacyAllocationContextsRequest
      .newBuilder().setSession(VALID_SESSION).addClassIds(1).addClassIds(2).addStackIds(1).addStackIds(2).build();
    AllocationContextsResponse response = getTable().getLegacyAllocationContexts(request);

    assertThat(response.getAllocatedClassesCount()).isEqualTo(0);
    assertThat(response.getAllocationStacksCount()).isEqualTo(0);
  }

  private static void verifyMemoryDataResultCounts(@NotNull MemoryProfiler.MemoryData result,
                                                   int numMemSample,
                                                   int numAllocStatsSample,
                                                   int numGcStatsSample,
                                                   int numHeapInfoSample,
                                                   int numAllocInfoSample) {
    assertThat(result.getMemSamplesCount()).isEqualTo(numMemSample);
    assertThat(result.getAllocStatsSamplesCount()).isEqualTo(numAllocStatsSample);
    assertThat(result.getGcStatsSamplesCount()).isEqualTo(numGcStatsSample);
    assertThat(result.getHeapDumpInfosCount()).isEqualTo(numHeapInfoSample);
    assertThat(result.getAllocationsInfoCount()).isEqualTo(numAllocInfoSample);
  }
}