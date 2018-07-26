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
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertThat;

public class CpuTableTest extends DatabaseTest<CpuTable> {

  private static final int PROCESS_ID = 1;
  private static final int TEST_DATA = 10;
  private static final int SESSION_ONE_OFFSET = 100;
  private static final int SESSION_TWO_OFFSET = 1000;
  private static final Common.Session SESSION_HUNDREDS =
    Common.Session.newBuilder().setSessionId(1L).setDeviceId(100).setEndTimestamp(Long.MAX_VALUE /* alive */).setPid(PROCESS_ID).build();
  private static final Common.Session SESSION_THOUSANDS =
    Common.Session.newBuilder().setSessionId(2L).setDeviceId(1000).setEndTimestamp(10 /* ended */).setPid(PROCESS_ID).build();

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    populateDatabase();
  }

  @Override
  protected List<Consumer<CpuTable>> getTableQueryMethodsForVerification() {
    List<Consumer<CpuTable>> methodCalls = new ArrayList<>();
    methodCalls.add((table) -> assertThat(table.getCpuDataByRequest(CpuProfiler.CpuDataRequest.getDefaultInstance())).isEmpty());
    methodCalls.add((table) -> assertThat(table.getProfilingStateData(Common.Session.getDefaultInstance())).isNull());
    methodCalls.add((table) -> assertThat(table.getThreadsDataByRequest(CpuProfiler.GetThreadsRequest.getDefaultInstance())).isEmpty());
    methodCalls.add((table) -> assertThat(table.getTraceData(Common.Session.getDefaultInstance(), 0)).isNull());
    methodCalls.add((table) -> assertThat(table.getTraceInfo(CpuProfiler.GetTraceInfoRequest.getDefaultInstance())).isEmpty());
    methodCalls.add((table) -> table.insert(Common.Session.getDefaultInstance(), CpuProfiler.CpuUsageData.getDefaultInstance()));
    methodCalls.add((table) -> {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = new ArrayList<>();
      activities.add(CpuProfiler.GetThreadsResponse.ThreadActivity.getDefaultInstance());
      table.insertActivities(Common.Session.getDefaultInstance(), 0, "", activities);
    });
    methodCalls.add((table) -> table
      .insertProfilingStateData(Common.Session.getDefaultInstance(), CpuProfiler.ProfilingStateResponse.getDefaultInstance()));
    methodCalls.add((table) -> {
      List<CpuProfiler.GetThreadsResponse.ThreadSnapshot.Snapshot> snapshots = new ArrayList<>();
      snapshots.add(CpuProfiler.GetThreadsResponse.ThreadSnapshot.Snapshot.getDefaultInstance());
      table.insertSnapshot(Common.Session.getDefaultInstance(), 0, snapshots);
    });
    methodCalls.add((table) -> table
      .insertTrace(Common.Session.getDefaultInstance(), 0, CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER,
                   CpuProfiler.CpuProfilerMode.UNSPECIFIED_MODE, ByteString.EMPTY));
    methodCalls.add((table) -> table.insertTraceInfo(Common.Session.getDefaultInstance(), CpuProfiler.TraceInfo.getDefaultInstance()));
    return methodCalls;
  }

  @Override
  protected CpuTable createTable() {
    return new CpuTable();
  }

  private void populateDatabase() {
    for (int i = 0; i < TEST_DATA; i++) {
      CpuProfiler.CpuUsageData testData = CpuProfiler.CpuUsageData
        .newBuilder().setAppCpuTimeInMillisec(SESSION_ONE_OFFSET + i).setSystemCpuTimeInMillisec(SESSION_ONE_OFFSET + i)
        .setElapsedTimeInMillisec(SESSION_ONE_OFFSET + i).setEndTimestamp(SESSION_ONE_OFFSET + i).build();
      getTable().insert(SESSION_HUNDREDS, testData);
    }

    for (int i = 0; i < TEST_DATA; i++) {
      CpuProfiler.CpuUsageData testData = CpuProfiler.CpuUsageData
        .newBuilder().setAppCpuTimeInMillisec(SESSION_TWO_OFFSET + i).setSystemCpuTimeInMillisec(SESSION_TWO_OFFSET + i)
        .setElapsedTimeInMillisec(SESSION_TWO_OFFSET + i).setEndTimestamp(SESSION_TWO_OFFSET + i).build();
      getTable().insert(SESSION_THOUSANDS, testData);
    }

    List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = new ArrayList<>();
    for (int i = 0; i < TEST_DATA; i++) {
      activities.add(
        CpuProfiler.GetThreadsResponse.ThreadActivity
          .newBuilder().setTimestamp(SESSION_ONE_OFFSET + i).setNewState(CpuProfiler.GetThreadsResponse.State.SLEEPING).build());
    }
    getTable().insertActivities(SESSION_HUNDREDS, SESSION_ONE_OFFSET, "Thread 100", activities);
    activities.clear();
    for (int i = 0; i < TEST_DATA; i++) {
      activities.add(
        CpuProfiler.GetThreadsResponse.ThreadActivity
          .newBuilder().setTimestamp(SESSION_TWO_OFFSET + i).setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING).build());
    }
    getTable().insertActivities(SESSION_THOUSANDS, SESSION_TWO_OFFSET, "Thread 1000", activities);

    for (int i = 0; i < TEST_DATA; i++) {
      CpuProfiler.TraceInfo trace = CpuProfiler.TraceInfo
        .newBuilder().setTraceId(SESSION_ONE_OFFSET + i)
        .setProfilerType(CpuProfiler.CpuProfilerType.ART).setProfilerMode(CpuProfiler.CpuProfilerMode.SAMPLED)
        .setFromTimestamp(SESSION_ONE_OFFSET + i).setToTimestamp(SESSION_ONE_OFFSET + 1 + i)
        .build();

      getTable().insertTrace(SESSION_HUNDREDS, trace.getTraceId(), trace.getProfilerType(), trace.getProfilerMode(),
                             ByteString.copyFromUtf8("100s club: " + i));
      getTable().insertTraceInfo(SESSION_HUNDREDS, trace);
    }
  }

  @Test
  public void testGetData() {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest
      .newBuilder().setSession(SESSION_HUNDREDS).setStartTimestamp(SESSION_ONE_OFFSET).setEndTimestamp(Long.MAX_VALUE).build();
    List<CpuProfiler.CpuUsageData> response = getTable().getCpuDataByRequest(request);

    // Validate that we have data from start timestamp (exclusive) to end timestamp (inclusive)
    assertThat(response.size()).isEqualTo(TEST_DATA - 1);

    // Validate we only got back data we expected to get back.
    for (int i = 1; i < response.size(); i++) {
      CpuProfiler.CpuUsageData data = response.get(i - 1);
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
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1))
      .build();
    List<CpuProfiler.CpuUsageData> response = getTable().getCpuDataByRequest(request);

    assertThat(response.size()).isEqualTo(0);
  }

  @Test
  public void testGetDataInvalidTimeRange() {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest
      .newBuilder().setSession(SESSION_HUNDREDS).setStartTimestamp(0).setEndTimestamp(10).build();
    List<CpuProfiler.CpuUsageData> response = getTable().getCpuDataByRequest(request);

    assertThat(response.size()).isEqualTo(0);
  }

  @Test
  public void testGetThreadsDataByRequest() {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest
      .newBuilder().setSession(SESSION_HUNDREDS).setStartTimestamp(SESSION_ONE_OFFSET).setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1))
      .build();
    List<CpuProfiler.GetThreadsResponse.Thread> response = getTable().getThreadsDataByRequest(request);

    assertThat(response.size()).isEqualTo(1);
    int activityCount = response.get(0).getActivitiesCount();
    assertThat(activityCount).isEqualTo(TEST_DATA);
    for (int i = 0; i < activityCount; i++) {
      CpuProfiler.GetThreadsResponse.ThreadActivity activity = response.get(0).getActivities(i);
      assertThat(activity.getTimestamp()).isEqualTo(SESSION_ONE_OFFSET + i);
      assertThat(activity.getNewState()).isEqualTo(CpuProfiler.GetThreadsResponse.State.SLEEPING);
    }
  }

  @Test
  public void testGetThreadsDataByRequestInvalidSession() {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest
      .newBuilder().setSession(Common.Session.getDefaultInstance()).setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1)).build();
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

    assertThat(response.size()).isEqualTo(1);
    int activityCount = response.get(0).getActivitiesCount();
    assertThat(activityCount).isEqualTo(TEST_DATA);
    for (int i = 0; i < activityCount; i++) {
      CpuProfiler.GetThreadsResponse.ThreadActivity activity = response.get(0).getActivities(i);
      assertThat(activity.getTimestamp()).isEqualTo(SESSION_ONE_OFFSET + i);
      assertThat(activity.getNewState()).isEqualTo(CpuProfiler.GetThreadsResponse.State.SLEEPING);
    }
  }

  @Test
  public void testGetTraceInfo() {
    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest
      .newBuilder().setSession(SESSION_HUNDREDS).setFromTimestamp(0).setToTimestamp(Long.MAX_VALUE).build();
    List<CpuProfiler.TraceInfo> traceInfo = getTable().getTraceInfo(request);
    assertThat(traceInfo.size()).isEqualTo(TEST_DATA);
    for (int i = 0; i < traceInfo.size(); i++) {
      assertThat(traceInfo.get(i).getFromTimestamp()).isEqualTo(SESSION_ONE_OFFSET + i);
      assertThat(traceInfo.get(i).getToTimestamp()).isEqualTo(SESSION_ONE_OFFSET + 1 + i);
      assertThat(traceInfo.get(i).getTraceId()).isEqualTo(SESSION_ONE_OFFSET + i);
    }
  }

  @Test
  public void testGetInvalidTraceByRequest() {
    CpuTable.TraceData traceData = getTable().getTraceData(SESSION_HUNDREDS, -1);
    assertThat(traceData).isNull();
  }

  @Test
  public void testGetTraceByRequestInvalidSession() {
    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest
      .newBuilder().setSession(SESSION_THOUSANDS).setFromTimestamp(0).setToTimestamp(Long.MAX_VALUE).build();
    List<CpuProfiler.TraceInfo> traceInfo = getTable().getTraceInfo(request);
    assertThat(traceInfo.size()).isEqualTo(0);
  }

  @Test
  public void beingProfiledStateIsBasedOnSession() {
    CpuProfiler.CpuProfilerConfiguration simpleperfConfig = CpuProfiler.CpuProfilerConfiguration
      .newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF).build();
    CpuProfiler.ProfilingStateResponse response1 = CpuProfiler.ProfilingStateResponse
      .newBuilder().setBeingProfiled(true).setConfiguration(simpleperfConfig).build();

    CpuProfiler.CpuProfilerConfiguration artConfig = CpuProfiler.CpuProfilerConfiguration
      .newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ART).build();
    CpuProfiler.ProfilingStateResponse response2 = CpuProfiler.ProfilingStateResponse
      .newBuilder().setBeingProfiled(true).setConfiguration(artConfig).build();

    Common.Session aliveSession = Common.Session.newBuilder().setSessionId(3L).setEndTimestamp(Long.MAX_VALUE /* alive */).build();

    getTable().insertProfilingStateData(SESSION_HUNDREDS, response1);
    getTable().insertProfilingStateData(aliveSession, response2);

    // The response we return depends on the session we pass to the select query
    CpuProfiler.ProfilingStateResponse response = getTable().getProfilingStateData(SESSION_HUNDREDS);
    assertThat(response).isNotNull();
    assertThat(response.getBeingProfiled()).isTrue();
    assertThat(response.getConfiguration().getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.SIMPLEPERF);

    response = getTable().getProfilingStateData(aliveSession);
    assertThat(response).isNotNull();
    assertThat(response.getBeingProfiled()).isTrue();
    assertThat(response.getConfiguration().getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
  }

  @Test
  public void latestBeingProfiledStateShouldBeReturned() {
    CpuProfiler.CpuProfilerConfiguration simpleperfConfig = CpuProfiler.CpuProfilerConfiguration
      .newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF).build();
    CpuProfiler.ProfilingStateResponse response1 = CpuProfiler.ProfilingStateResponse
      .newBuilder().setBeingProfiled(true).setCheckTimestamp(1).setConfiguration(simpleperfConfig).build();

    CpuProfiler.CpuProfilerConfiguration artConfig = CpuProfiler.CpuProfilerConfiguration
      .newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ART).build();
    CpuProfiler.ProfilingStateResponse response2 = CpuProfiler.ProfilingStateResponse
      .newBuilder().setBeingProfiled(true).setCheckTimestamp(10).setConfiguration(artConfig).build();

    CpuProfiler.ProfilingStateResponse response3 = CpuProfiler.ProfilingStateResponse
      .newBuilder().setCheckTimestamp(5).setBeingProfiled(false).build();


    getTable().insertProfilingStateData(SESSION_HUNDREDS, response1);
    getTable().insertProfilingStateData(SESSION_HUNDREDS, response2);
    getTable().insertProfilingStateData(SESSION_HUNDREDS, response3);

    // response2 should be returned because it has the higher (most recent) check_timestamp, even if it was not the last to be inserted
    CpuProfiler.ProfilingStateResponse response = getTable().getProfilingStateData(SESSION_HUNDREDS);
    assertThat(response).isNotNull();
    assertThat(response.getBeingProfiled()).isTrue();
    assertThat(response.getConfiguration().getProfilerType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);
  }
}