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

import static com.android.tools.profiler.proto.Cpu.CpuThreadData.State.RUNNING;
import static com.android.tools.profiler.proto.Cpu.CpuThreadData.State.SLEEPING;
import static com.android.tools.profiler.proto.Cpu.CpuThreadData.State.WAITING;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.Trace;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

public class CpuTableTest extends DatabaseTest<CpuTable> {

  private static final int PROCESS_ID = 1;
  private static final int TEST_DATA_COUNT = 10;
  private static final int SESSION_ONE_OFFSET = 100;
  private static final int SESSION_TWO_OFFSET = 1000;
  private static final int SESSION_ONE_TID_100 = 100;
  private static final int SESSION_ONE_TID_101 = 101;
  private static final Common.Session SESSION_HUNDREDS =
    Common.Session.newBuilder().setSessionId(1L).setStreamId(100).setEndTimestamp(Long.MAX_VALUE /* alive */).setPid(PROCESS_ID).build();
  private static final Common.Session SESSION_THOUSANDS =
    Common.Session.newBuilder().setSessionId(2L).setStreamId(1000).setEndTimestamp(10 /* ended */).setPid(PROCESS_ID).build();

  @Before
  @Override
  public void before() throws Exception {
    super.before();
    populateDatabase();
  }

  @Override
  @NotNull
  protected List<Consumer<CpuTable>> getTableQueryMethodsForVerification() {
    List<Consumer<CpuTable>> methodCalls = new ArrayList<>();
    methodCalls.add((table) -> assertThat(table.getCpuDataByRequest(CpuProfiler.CpuDataRequest.getDefaultInstance())).isEmpty());
    methodCalls.add((table) -> assertThat(table.getThreadsDataByRequest(CpuProfiler.GetThreadsRequest.getDefaultInstance())).isEmpty());
    methodCalls.add((table) -> assertThat(table.getTraceInfo(CpuProfiler.GetTraceInfoRequest.getDefaultInstance())).isEmpty());
    methodCalls.add((table) -> table.insert(Common.Session.getDefaultInstance(), Cpu.CpuUsageData.getDefaultInstance()));
    methodCalls.add((table) -> {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = new ArrayList<>();
      activities.add(CpuProfiler.GetThreadsResponse.ThreadActivity.getDefaultInstance());
      table.insertActivities(Common.Session.getDefaultInstance(), 0, "", activities);
    });
    methodCalls.add((table) -> {
      List<CpuProfiler.GetThreadsResponse.ThreadSnapshot.Snapshot> snapshots = new ArrayList<>();
      snapshots.add(CpuProfiler.GetThreadsResponse.ThreadSnapshot.Snapshot.getDefaultInstance());
      table.insertSnapshot(Common.Session.getDefaultInstance(), 0, snapshots);
    });
    methodCalls.add((table) -> table.insertTraceInfo(Common.Session.getDefaultInstance(), Cpu.CpuTraceInfo.getDefaultInstance()));
    return methodCalls;
  }

  @Override
  @NotNull
  protected CpuTable createTable() {
    return new CpuTable();
  }

  private void populateDatabase() {
    for (int i = 0; i < TEST_DATA_COUNT; i++) {
      Cpu.CpuUsageData testData = Cpu.CpuUsageData
        .newBuilder().setAppCpuTimeInMillisec(SESSION_ONE_OFFSET + i).setSystemCpuTimeInMillisec(SESSION_ONE_OFFSET + i)
        .setElapsedTimeInMillisec(SESSION_ONE_OFFSET + i).setEndTimestamp(SESSION_ONE_OFFSET + i).build();
      getTable().insert(SESSION_HUNDREDS, testData);
    }

    for (int i = 0; i < TEST_DATA_COUNT; i++) {
      Cpu.CpuUsageData testData = Cpu.CpuUsageData
        .newBuilder().setAppCpuTimeInMillisec(SESSION_TWO_OFFSET + i).setSystemCpuTimeInMillisec(SESSION_TWO_OFFSET + i)
        .setElapsedTimeInMillisec(SESSION_TWO_OFFSET + i).setEndTimestamp(SESSION_TWO_OFFSET + i).build();
      getTable().insert(SESSION_THOUSANDS, testData);
    }

    List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = new ArrayList<>();
    for (int i = 0; i < TEST_DATA_COUNT; i++) {
      activities.add(
        CpuProfiler.GetThreadsResponse.ThreadActivity
          .newBuilder().setTimestamp(SESSION_ONE_OFFSET + i).setNewState(SLEEPING).build());
    }
    getTable().insertActivities(SESSION_HUNDREDS, SESSION_ONE_TID_100, "Thread " + SESSION_ONE_TID_100, activities);
    activities.clear();
    for (int i = 0; i < TEST_DATA_COUNT; i++) {
      activities.add(
        CpuProfiler.GetThreadsResponse.ThreadActivity
          .newBuilder().setTimestamp(SESSION_ONE_OFFSET + i).setNewState(WAITING).build());
    }
    getTable().insertActivities(SESSION_HUNDREDS, SESSION_ONE_TID_101, "Thread " + SESSION_ONE_TID_101, activities);
    activities.clear();
    for (int i = 0; i < TEST_DATA_COUNT; i++) {
      activities.add(
        CpuProfiler.GetThreadsResponse.ThreadActivity
          .newBuilder().setTimestamp(SESSION_TWO_OFFSET + i).setNewState(RUNNING).build());
    }
    getTable().insertActivities(SESSION_THOUSANDS, SESSION_TWO_OFFSET, "Thread " + SESSION_TWO_OFFSET, activities);

    for (int i = 0; i < TEST_DATA_COUNT; i++) {
      // spaces the trace infos by 2 time unit so we can query each sample at a time using [time:time+1]
      long startTime = SESSION_ONE_OFFSET + i * 2;
      Cpu.CpuTraceInfo trace = Cpu.CpuTraceInfo
        .newBuilder().setTraceId(startTime)
        .setConfiguration(Trace.TraceConfiguration.newBuilder())
        .setFromTimestamp(startTime).setToTimestamp(startTime + 1)
        .build();
      getTable().insertTraceInfo(SESSION_HUNDREDS, trace);
    }
  }

  @Test
  public void testGetData() {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest
      .newBuilder().setSession(SESSION_HUNDREDS).setStartTimestamp(SESSION_ONE_OFFSET).setEndTimestamp(Long.MAX_VALUE).build();
    List<Cpu.CpuUsageData> response = getTable().getCpuDataByRequest(request);

    // Validate that we have data from start timestamp (exclusive) to end timestamp (inclusive)
    assertThat(response.size()).isEqualTo(TEST_DATA_COUNT - 1);

    // Validate we only got back data we expected to get back.
    for (int i = 1; i < response.size(); i++) {
      Cpu.CpuUsageData data = response.get(i - 1);
      assertThat(data.getEndTimestamp()).isEqualTo(SESSION_ONE_OFFSET + i);
      assertThat(data.getAppCpuTimeInMillisec()).isEqualTo(SESSION_ONE_OFFSET + i);
      assertThat(data.getSystemCpuTimeInMillisec()).isEqualTo(SESSION_ONE_OFFSET + i);
      assertThat(data.getElapsedTimeInMillisec()).isEqualTo(SESSION_ONE_OFFSET + i);
    }
  }

  @Test
  public void testGetDataInvalidSession() {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest
      .newBuilder().setSession(Common.Session.getDefaultInstance())
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA_COUNT - 1))
      .build();
    List<Cpu.CpuUsageData> response = getTable().getCpuDataByRequest(request);

    assertThat(response.size()).isEqualTo(0);
  }

  @Test
  public void testGetDataInvalidTimeRange() {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest
      .newBuilder().setSession(SESSION_HUNDREDS).setStartTimestamp(0).setEndTimestamp(10).build();
    List<Cpu.CpuUsageData> response = getTable().getCpuDataByRequest(request);

    assertThat(response.size()).isEqualTo(0);
  }

  @Test
  public void testGetThreadsDataByRequest() {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest
      .newBuilder().setSession(SESSION_HUNDREDS).setStartTimestamp(SESSION_ONE_OFFSET + 1)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA_COUNT - 1))
      .build();
    List<CpuProfiler.GetThreadsResponse.Thread> response = getTable().getThreadsDataByRequest(request);

    assertThat(response.size()).isEqualTo(2);

    int activityCount = response.get(0).getActivitiesCount();
    // Even though start timestamp isn't inclusive, we should still it since the query should include
    // the latest one just prior to the start timestamp.
    assertThat(activityCount).isEqualTo(TEST_DATA_COUNT - 1);
    int tid = response.get(0).getTid();
    assertThat(tid).isAnyOf(SESSION_ONE_TID_100, SESSION_ONE_TID_101);
    Cpu.CpuThreadData.State expectedState = tid == SESSION_ONE_TID_100 ? SLEEPING : WAITING;
    for (int i = 0; i < activityCount; i++) {
      CpuProfiler.GetThreadsResponse.ThreadActivity activity = response.get(0).getActivities(i);
      assertThat(activity.getTimestamp()).isEqualTo(SESSION_ONE_OFFSET + i + 1);
      assertThat(activity.getNewState()).isEqualTo(expectedState);
    }

    activityCount = response.get(1).getActivitiesCount();
    assertThat(activityCount).isEqualTo(TEST_DATA_COUNT - 1);
    assertThat(response.get(1).getTid()).isNotEqualTo(tid); // assert that the two are different and not duplicate
    tid = response.get(1).getTid();
    assertThat(tid).isAnyOf(SESSION_ONE_TID_100, SESSION_ONE_TID_101);
    expectedState = tid == SESSION_ONE_TID_100 ? SLEEPING : WAITING;
    for (int i = 0; i < activityCount; i++) {
      CpuProfiler.GetThreadsResponse.ThreadActivity activity = response.get(1).getActivities(i);
      assertThat(activity.getTimestamp()).isEqualTo(SESSION_ONE_OFFSET + i + 1);
      assertThat(activity.getNewState()).isEqualTo(expectedState);
    }
  }

  @Test
  public void testGetThreadsDataByRequestInvalidSession() {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest
      .newBuilder().setSession(Common.Session.getDefaultInstance()).setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA_COUNT - 1)).build();
    List<CpuProfiler.GetThreadsResponse.Thread> response = getTable().getThreadsDataByRequest(request);

    assertThat(response.size()).isEqualTo(0);
  }

  @Test
  public void testGetThreadsDataByRequestInvalidTiming() {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest
      .newBuilder().setSession(SESSION_HUNDREDS).setStartTimestamp(0).setEndTimestamp(10).build();
    List<CpuProfiler.GetThreadsResponse.Thread> response = getTable().getThreadsDataByRequest(request);

    assertThat(response.size()).isEqualTo(0);
  }

  @Test
  public void testGetThreadsDataByRequestSessionSpecific() {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest
      .newBuilder().setSession(SESSION_HUNDREDS).setStartTimestamp(0).setEndTimestamp(Long.MAX_VALUE).build();
    List<CpuProfiler.GetThreadsResponse.Thread> response = getTable().getThreadsDataByRequest(request);

    assertThat(response.size()).isEqualTo(2);

    int activityCount = response.get(0).getActivitiesCount();
    assertThat(activityCount).isEqualTo(TEST_DATA_COUNT);
    int tid = response.get(0).getTid();
    assertThat(tid).isAnyOf(SESSION_ONE_TID_100, SESSION_ONE_TID_101);
    Cpu.CpuThreadData.State expectedState = tid == SESSION_ONE_TID_100 ? SLEEPING : WAITING;
    for (int i = 0; i < activityCount; i++) {
      CpuProfiler.GetThreadsResponse.ThreadActivity activity = response.get(0).getActivities(i);
      assertThat(activity.getTimestamp()).isEqualTo(SESSION_ONE_OFFSET + i);
      assertThat(activity.getNewState()).isEqualTo(expectedState);
    }

    activityCount = response.get(1).getActivitiesCount();
    assertThat(activityCount).isEqualTo(TEST_DATA_COUNT);
    tid = response.get(1).getTid();
    assertThat(tid).isAnyOf(SESSION_ONE_TID_100, SESSION_ONE_TID_101);
    expectedState = tid == SESSION_ONE_TID_100 ? SLEEPING : WAITING;
    for (int i = 0; i < activityCount; i++) {
      CpuProfiler.GetThreadsResponse.ThreadActivity activity = response.get(1).getActivities(i);
      assertThat(activity.getTimestamp()).isEqualTo(SESSION_ONE_OFFSET + i);
      assertThat(activity.getNewState()).isEqualTo(expectedState);
    }
  }

  @Test
  public void testGetTraceInfo() {
    for (int i = 0; i < TEST_DATA_COUNT; i++) {
      long startTime = SESSION_ONE_OFFSET + i * 2;
      CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest.newBuilder()
        .setSession(SESSION_HUNDREDS).setFromTimestamp(startTime).setToTimestamp(startTime + 2).build();
      List<Cpu.CpuTraceInfo> traceInfos = getTable().getTraceInfo(request);
      assertThat(traceInfos.size()).isEqualTo(1);
      Cpu.CpuTraceInfo traceInfo = traceInfos.get(0);
      assertThat(traceInfo.getTraceId()).isEqualTo(startTime);
      assertThat(traceInfo.getFromTimestamp()).isEqualTo(startTime);
      assertThat(traceInfo.getToTimestamp()).isEqualTo(startTime + 1);
    }
  }

  @Test
  public void testGetOngoingTraceInfo() {
    Common.Session session = Common.Session.newBuilder().setSessionId(10L).build();
    Cpu.CpuTraceInfo trace = Cpu.CpuTraceInfo.newBuilder()
      .setTraceId(10)
      .setFromTimestamp(100)
      .setToTimestamp(-1) // -1 for ongoing traces
      .build();
    getTable().insertTraceInfo(session, trace);

    // before trace range
    CpuProfiler.GetTraceInfoRequest.Builder request = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setSession(session).setFromTimestamp(-10).setToTimestamp(100);
    assertThat(getTable().getTraceInfo(request.build())).hasSize(0);

    // within trace range
    List<Cpu.CpuTraceInfo> traceInfos = getTable().getTraceInfo(request.setToTimestamp(101).build());
    assertThat(traceInfos).hasSize(1);
    assertThat(traceInfos.get(0).getTraceId()).isEqualTo(10);

    // beyond trace range
    traceInfos = getTable().getTraceInfo(request.setFromTimestamp(101).setToTimestamp(102).build());
    assertThat(traceInfos).hasSize(1);
    assertThat(traceInfos.get(0).getTraceId()).isEqualTo(10);
  }

  @Test
  public void testGetTraceByRequestInvalidSession() {
    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest
      .newBuilder().setSession(SESSION_THOUSANDS).setFromTimestamp(0).setToTimestamp(Long.MAX_VALUE).build();
    List<Cpu.CpuTraceInfo> traceInfo = getTable().getTraceInfo(request);
    assertThat(traceInfo.size()).isEqualTo(0);
  }
}