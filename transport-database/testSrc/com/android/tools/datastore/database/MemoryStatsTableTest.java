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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class MemoryStatsTableTest extends DatabaseTest<MemoryStatsTable> {
  private static final Common.Session VALID_SESSION = Common.Session.newBuilder().setSessionId(1L).setStreamId(1234).setPid(1).build();
  private static final Common.Session INVALID_SESSION = Common.Session.newBuilder().setSessionId(-1L).setStreamId(4321).setPid(-1).build();

  @Override
  protected MemoryStatsTable createTable() {
    return new MemoryStatsTable();
  }

  @Override
  protected List<Consumer<MemoryStatsTable>> getTableQueryMethodsForVerification() {
    List<Consumer<MemoryStatsTable>> methodCalls = new ArrayList<>();
    Common.Session session = Common.Session.getDefaultInstance();
    methodCalls.add((table) -> assertThat(table.getAllocationsInfo(session, 0)).isNull());
    methodCalls.add((table) -> assertThat(table.getHeapDumpInfoByRequest(session, ListDumpInfosRequest.getDefaultInstance())).isEmpty());
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
    methodCalls.add((table) -> {
      List<MemoryData.MemorySample> samples = new ArrayList<>();
      samples.add(MemoryData.MemorySample.getDefaultInstance());
      table.insertMemory(session, samples);
    });
    methodCalls.add((table) -> table.insertOrReplaceAllocationsInfo(session, AllocationsInfo.getDefaultInstance()));
    methodCalls.add((table) -> table.insertOrReplaceHeapInfo(session, HeapDumpInfo.getDefaultInstance()));
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

  private static void verifyMemoryDataResultCounts(@NotNull MemoryData result,
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