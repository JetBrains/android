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

import com.android.tools.datastore.DataStoreDatabase;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CpuTableTest {

  private static final int PROCESS_ID = 1;
  private static final int TEST_DATA = 10;
  private static final int SESSION_ONE_OFFSET = 100;
  private static final int SESSION_TWO_OFFSET = 1000;
  private static final Common.Session SESSION_HUNDREDS =
    Common.Session.newBuilder().setSessionId(1L).setDeviceId(100).setPid(PROCESS_ID).build();
  private static final Common.Session SESSION_THOUSANDS =
    Common.Session.newBuilder().setSessionId(2L).setDeviceId(1000).setPid(PROCESS_ID).build();

  private File myDbFile;
  private CpuTable myTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    myDbFile = FileUtil.createTempFile("CpuTable", "mysql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath(), DataStoreDatabase.Characteristic.DURABLE);
    myTable = new CpuTable();
    myTable.initialize(myDatabase.getConnection());
    populateDatabase();
  }

  @After
  public void tearDown() throws Exception {
    myDatabase.disconnect();
    FileUtil.delete(myDbFile);
  }

  private void populateDatabase() {

    for (int i = 0; i < TEST_DATA; i++) {
      CpuProfiler.CpuUsageData testData = CpuProfiler.CpuUsageData.newBuilder()
        .setAppCpuTimeInMillisec(SESSION_ONE_OFFSET + i)
        .setSystemCpuTimeInMillisec(SESSION_ONE_OFFSET + i)
        .setElapsedTimeInMillisec(SESSION_ONE_OFFSET + i)
        .setEndTimestamp(SESSION_ONE_OFFSET + i).build();
      myTable.insert(SESSION_HUNDREDS, testData);
    }

    for (int i = 0; i < TEST_DATA; i++) {
      CpuProfiler.CpuUsageData testData = CpuProfiler.CpuUsageData.newBuilder()
        .setAppCpuTimeInMillisec(SESSION_TWO_OFFSET + i)
        .setSystemCpuTimeInMillisec(SESSION_TWO_OFFSET + i)
        .setElapsedTimeInMillisec(SESSION_TWO_OFFSET + i)
        .setEndTimestamp(SESSION_TWO_OFFSET + i).build();
      myTable.insert(SESSION_THOUSANDS, testData);
    }

    List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = new ArrayList<>();
    for (int i = 0; i < TEST_DATA; i++) {
      activities.add(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                       .setTimestamp(SESSION_ONE_OFFSET + i)
                       .setNewState(CpuProfiler.GetThreadsResponse.State.SLEEPING)
                       .build());
    }
    myTable.insertActivities(SESSION_HUNDREDS, SESSION_ONE_OFFSET, "Thread 100", activities);
    activities.clear();
    for (int i = 0; i < TEST_DATA; i++) {
      activities.add(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                       .setTimestamp(SESSION_TWO_OFFSET + i)
                       .setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING)
                       .build());
    }
    myTable.insertActivities(SESSION_THOUSANDS, SESSION_TWO_OFFSET, "Thread 1000", activities);

    for (int i = 0; i < TEST_DATA; i++) {
      CpuProfiler.TraceInfo trace = CpuProfiler.TraceInfo.newBuilder()
        .setTraceId(SESSION_ONE_OFFSET + i)
        .setProfilerType(CpuProfiler.CpuProfilerType.ART)
        .setFromTimestamp(SESSION_ONE_OFFSET + i)
        .setToTimestamp(SESSION_ONE_OFFSET + 1 + i)
        .build();

      myTable
        .insertTrace(SESSION_HUNDREDS, trace.getTraceId(), trace.getProfilerType(), ByteString.copyFromUtf8("100s club: " + i));
      myTable.insertTraceInfo(SESSION_HUNDREDS, trace);
    }
  }

  @Test
  public void testGetData() throws Exception {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    List<CpuProfiler.CpuUsageData> response = myTable.getCpuDataByRequest(request);

    // Validate that we have data from start timestamp (exclusive) to end timestamp (inclusive)
    assertEquals(TEST_DATA - 1, response.size());

    // Validate we only got back data we expected to get back.
    for (int i = 1; i < response.size(); i++) {
      CpuProfiler.CpuUsageData data = response.get(i - 1);
      assertEquals(SESSION_ONE_OFFSET + i, data.getEndTimestamp());
      assertEquals(SESSION_ONE_OFFSET + i, data.getAppCpuTimeInMillisec());
      assertEquals(SESSION_ONE_OFFSET + i, data.getSystemCpuTimeInMillisec());
      assertEquals(SESSION_ONE_OFFSET + i, data.getElapsedTimeInMillisec());
    }
  }

  @Test
  public void testGetDataInvalidSession() throws Exception {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setSession(Common.Session.getDefaultInstance())
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1))
      .build();
    List<CpuProfiler.CpuUsageData> response = myTable.getCpuDataByRequest(request);

    assertEquals(0, response.size());
  }

  @Test
  public void testGetDataInvalidTimeRange() throws Exception {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(0)
      .setEndTimestamp(10)
      .build();
    List<CpuProfiler.CpuUsageData> response = myTable.getCpuDataByRequest(request);

    assertEquals(0, response.size());
  }

  @Test
  public void testGetThreadsDataByRequest() throws Exception {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1))
      .build();
    List<CpuProfiler.GetThreadsResponse.Thread> response = myTable.getThreadsDataByRequest(request);

    assertEquals(1, response.size());
    int activityCount = response.get(0).getActivitiesCount();
    assertEquals(TEST_DATA, activityCount);
    for (int i = 0; i < activityCount; i++) {
      CpuProfiler.GetThreadsResponse.ThreadActivity activity = response.get(0).getActivities(i);
      assertEquals(SESSION_ONE_OFFSET + i, activity.getTimestamp());
      assertEquals(CpuProfiler.GetThreadsResponse.State.SLEEPING, activity.getNewState());
    }
  }

  @Test
  public void testGetThreadsDataByRequestInvalidSession() throws Exception {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setSession(Common.Session.getDefaultInstance())
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1))
      .build();
    List<CpuProfiler.GetThreadsResponse.Thread> response = myTable.getThreadsDataByRequest(request);

    assertEquals(0, response.size());
  }

  @Test
  public void testGetThreadsDataByRequestInvalidTiming() throws Exception {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(0)
      .setEndTimestamp(10)
      .build();
    List<CpuProfiler.GetThreadsResponse.Thread> response = myTable.getThreadsDataByRequest(request);

    assertEquals(0, response.size());
  }

  @Test
  public void testGetThreadsDataByRequestSessionSpecific() throws Exception {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(0)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    List<CpuProfiler.GetThreadsResponse.Thread> response = myTable.getThreadsDataByRequest(request);

    assertEquals(1, response.size());
    int activityCount = response.get(0).getActivitiesCount();
    assertEquals(TEST_DATA, activityCount);
    for (int i = 0; i < activityCount; i++) {
      CpuProfiler.GetThreadsResponse.ThreadActivity activity = response.get(0).getActivities(i);
      assertEquals(SESSION_ONE_OFFSET + i, activity.getTimestamp());
      assertEquals(CpuProfiler.GetThreadsResponse.State.SLEEPING, activity.getNewState());
    }
  }

  @Test
  public void testGetTraceInfo() throws Exception {
    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setFromTimestamp(0)
      .setToTimestamp(Long.MAX_VALUE)
      .build();
    List<CpuProfiler.TraceInfo> traceInfo = myTable.getTraceInfo(request);
    assertEquals(TEST_DATA, traceInfo.size());
    for (int i = 0; i < traceInfo.size(); i++) {
      assertEquals(SESSION_ONE_OFFSET + i, traceInfo.get(i).getFromTimestamp());
      assertEquals(SESSION_ONE_OFFSET + 1 + i, traceInfo.get(i).getToTimestamp());
      assertEquals(SESSION_ONE_OFFSET + i, traceInfo.get(i).getTraceId());
    }
  }

  @Test
  public void testGetInvalidTraceByRequest() throws Exception {
    CpuTable.TraceData traceData = myTable.getTraceData(SESSION_HUNDREDS, -1);
    assertNull(traceData);
  }

  @Test
  public void testGetTraceByRequestInvalidSession() throws Exception {
    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setSession(SESSION_THOUSANDS)
      .setFromTimestamp(0)
      .setToTimestamp(Long.MAX_VALUE)
      .build();
    List<CpuProfiler.TraceInfo> traceInfo = myTable.getTraceInfo(request);
    assertEquals(0, traceInfo.size());
  }
}