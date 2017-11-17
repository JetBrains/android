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
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CpuTableTest {

  private static final int PROCESS_ID = 1;
  private static final int PROCESS_ID_INVALID = 5;
  private static final int TEST_DATA = 10;
  private static final int SESSION_ONE_OFFSET = 100;
  private static final int SESSION_TWO_OFFSET = 1000;
  private static final Common.Session SESSION_HUNDREDS = Common.Session.newBuilder().setDeviceId(100).build();
  private static final Common.Session SESSION_THOUSANDS = Common.Session.newBuilder().setDeviceId(1000).build();

  private File myDbFile;
  private CpuTable myTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    HashMap<Common.Session, Long> sessionLookup = new HashMap<>();
    sessionLookup.put(SESSION_HUNDREDS, 1L);
    sessionLookup.put(SESSION_THOUSANDS, 2L);
    myDbFile = FileUtil.createTempFile("CpuTable", "mysql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath(), DataStoreDatabase.Characteristic.DURABLE);
    myTable = new CpuTable(sessionLookup);
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
      CpuProfiler.CpuProfilerData testData = CpuProfiler.CpuProfilerData.newBuilder()
        .setBasicInfo(Common.CommonData.newBuilder()
                        .setProcessId(PROCESS_ID)
                        .setEndTimestamp(SESSION_ONE_OFFSET + i))
        .setCpuUsage(CpuProfiler.CpuUsageData.newBuilder()
                       .setAppCpuTimeInMillisec(SESSION_ONE_OFFSET + i)
                       .setSystemCpuTimeInMillisec(SESSION_ONE_OFFSET + i)
                       .setElapsedTimeInMillisec(SESSION_ONE_OFFSET + i)).build();
      myTable.insert(SESSION_HUNDREDS, testData);
    }

    for (int i = 0; i < TEST_DATA; i++) {
      CpuProfiler.CpuProfilerData testData = CpuProfiler.CpuProfilerData.newBuilder()
        .setBasicInfo(Common.CommonData.newBuilder()
                        .setProcessId(PROCESS_ID)
                        .setEndTimestamp(SESSION_TWO_OFFSET + i))
        .setCpuUsage(CpuProfiler.CpuUsageData.newBuilder()
                       .setAppCpuTimeInMillisec(SESSION_TWO_OFFSET + i)
                       .setSystemCpuTimeInMillisec(SESSION_TWO_OFFSET + i)
                       .setElapsedTimeInMillisec(SESSION_TWO_OFFSET + i)).build();
      myTable.insert(SESSION_THOUSANDS, testData);
    }

    List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = new ArrayList<>();
    for (int i = 0; i < TEST_DATA; i++) {
      activities.add(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                       .setTimestamp(SESSION_ONE_OFFSET + i)
                       .setNewState(CpuProfiler.GetThreadsResponse.State.SLEEPING)
                       .build());
    }
    myTable.insertActivities(PROCESS_ID, SESSION_HUNDREDS, SESSION_ONE_OFFSET, "Thread 100", activities);
    activities.clear();
    for (int i = 0; i < TEST_DATA; i++) {
      activities.add(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                       .setTimestamp(SESSION_TWO_OFFSET + i)
                       .setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING)
                       .build());
    }
    myTable.insertActivities(PROCESS_ID, SESSION_THOUSANDS, SESSION_TWO_OFFSET, "Thread 1000", activities);

    for (int i = 0; i < TEST_DATA; i++) {
      CpuProfiler.TraceInfo trace = CpuProfiler.TraceInfo.newBuilder()
        .setTraceId(SESSION_ONE_OFFSET + i)
        .setProfilerType(CpuProfiler.CpuProfilerType.ART)
        .setFromTimestamp(SESSION_ONE_OFFSET + i)
        .setToTimestamp(SESSION_ONE_OFFSET + 1 + i)
        .build();

      myTable
        .insertTrace(PROCESS_ID, trace.getTraceId(), SESSION_HUNDREDS, trace.getProfilerType(), ByteString.copyFromUtf8("100s club: " + i));
      myTable.insertTraceInfo(PROCESS_ID, trace, SESSION_HUNDREDS);
    }
  }

  @Test
  public void testGetData() throws Exception {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(Long.MAX_VALUE)
      .setProcessId(PROCESS_ID)
      .build();
    List<CpuProfiler.CpuProfilerData> response = myTable.getCpuDataByRequest(request);

    // Validate that we have data from start timestamp (exclusive) to end timestamp (inclusive)
    assertEquals(TEST_DATA - 1, response.size());

    // Validate we only got back data we expected to get back.
    for (int i = 1; i < response.size(); i++) {
      CpuProfiler.CpuProfilerData data = response.get(i - 1);
      assertEquals(PROCESS_ID, data.getBasicInfo().getProcessId());
      assertEquals(SESSION_ONE_OFFSET + i, data.getBasicInfo().getEndTimestamp());
      assertEquals(SESSION_ONE_OFFSET + i, data.getCpuUsage().getAppCpuTimeInMillisec());
      assertEquals(SESSION_ONE_OFFSET + i, data.getCpuUsage().getSystemCpuTimeInMillisec());
      assertEquals(SESSION_ONE_OFFSET + i, data.getCpuUsage().getElapsedTimeInMillisec());
    }
  }

  @Test
  public void testGetDataInvalidProcess() throws Exception {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1))
      .setProcessId(PROCESS_ID_INVALID)
      .build();
    List<CpuProfiler.CpuProfilerData> response = myTable.getCpuDataByRequest(request);

    assertEquals(0, response.size());
  }

  @Test
  public void testGetDataInvalidSession() throws Exception {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setSession(Common.Session.getDefaultInstance())
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1))
      .setProcessId(PROCESS_ID)
      .build();
    List<CpuProfiler.CpuProfilerData> response = myTable.getCpuDataByRequest(request);

    assertEquals(0, response.size());
  }

  @Test
  public void testGetDataInvalidTimeRange() throws Exception {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(0)
      .setEndTimestamp(10)
      .setProcessId(PROCESS_ID)
      .build();
    List<CpuProfiler.CpuProfilerData> response = myTable.getCpuDataByRequest(request);

    assertEquals(0, response.size());
  }

  @Test
  public void testGetThreadsDataByRequest() throws Exception {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1))
      .setProcessId(PROCESS_ID)
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
  public void testGetThreadsDataByRequestInvalidProcess() throws Exception {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setStartTimestamp(SESSION_ONE_OFFSET)
      .setEndTimestamp(SESSION_ONE_OFFSET + (TEST_DATA - 1))
      .setProcessId(PROCESS_ID_INVALID)
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
      .setProcessId(PROCESS_ID)
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
      .setProcessId(PROCESS_ID)
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
      .setProcessId(PROCESS_ID)
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
    CpuTable.TraceData traceData = myTable.getTraceData(PROCESS_ID, -1, SESSION_HUNDREDS);
    assertNull(traceData);
  }

  @Test
  public void testGetTraceByRequestInvalidSession() throws Exception {
    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setSession(SESSION_THOUSANDS)
      .setFromTimestamp(0)
      .setToTimestamp(Long.MAX_VALUE)
      .setProcessId(PROCESS_ID)
      .build();
    List<CpuProfiler.TraceInfo> traceInfo = myTable.getTraceInfo(request);
    assertEquals(0, traceInfo.size());
  }

  @Test
  @Ignore
  //TODO: Enable when trace request filter by process id
  public void testGetTraceByRequestInvalidProcessId() throws Exception {
    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setSession(SESSION_HUNDREDS)
      .setFromTimestamp(0)
      .setToTimestamp(Long.MAX_VALUE)
      .setProcessId(PROCESS_ID_INVALID)
      .build();
    List<CpuProfiler.TraceInfo> traceInfo = myTable.getTraceInfo(request);
    assertEquals(0, traceInfo.size());
  }
}