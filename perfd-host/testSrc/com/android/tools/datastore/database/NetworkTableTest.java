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
import com.android.tools.profiler.proto.NetworkProfiler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NetworkTableTest {
  private static final int PROCESS_ID = 1;
  private static final int PROCESS_ID_INVALID = 2;
  private static final int VALID_CONN_ID = 3;
  private static final int INVALID_CONN_ID = -1;
  private static final int TEST_DATA = 10;
  private static final Common.Session VALID_SESSION = Common.Session.newBuilder().setSessionId(1L).setDeviceId(1234).build();
  private static final Common.Session INVALID_SESSION = Common.Session.newBuilder().setSessionId(-1L).setDeviceId(4321).build();

  private File myDbFile;
  private NetworkTable myTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    myDbFile = File.createTempFile("NetworkTable", "mysql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath(), DataStoreDatabase.Characteristic.DURABLE);
    myTable = new NetworkTable();
    myTable.initialize(myDatabase.getConnection());
    populateDatabase();
  }

  @After
  public void tearDown() throws Exception {
    myDatabase.disconnect();
    myDbFile.delete();
  }

  private void populateDatabase() {
    for (int i = 0; i < TEST_DATA; i++) {
      NetworkProfiler.HttpConnectionData connection = NetworkProfiler.HttpConnectionData.newBuilder()
        .setConnId(VALID_CONN_ID + i)
        .setStartTimestamp(100 + i)
        .setEndTimestamp(101 + i)
        .build();
      NetworkProfiler.HttpDetailsResponse request = NetworkProfiler.HttpDetailsResponse.newBuilder()
        .setRequest(NetworkProfiler.HttpDetailsResponse.Request.newBuilder()
                      .setUrl("TestUrl"))
        .build();
      NetworkProfiler.HttpDetailsResponse threads = NetworkProfiler.HttpDetailsResponse.newBuilder()
        .setAccessingThreads(NetworkProfiler.HttpDetailsResponse.AccessingThreads.newBuilder()
                               .addThread(NetworkProfiler.JavaThread.newBuilder().setId(0).setName("threadA"))
                               .addThread(NetworkProfiler.JavaThread.newBuilder().setId(1).setName("threadB")))
        .build();
      myTable.insertOrReplace(PROCESS_ID, VALID_SESSION, request, null, null, null, threads, connection);
    }
  }

  @Test
  public void testGetHttpDetails() throws Exception {
    NetworkProfiler.HttpDetailsResponse response =
      myTable.getHttpDetailsResponseById(VALID_CONN_ID, VALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.REQUEST);
    assertEquals("TestUrl", response.getRequest().getUrl());
  }

  @Test
  public void testGetHttpDetailsInvalidConnId() throws Exception {
    NetworkProfiler.HttpDetailsResponse response =
      myTable.getHttpDetailsResponseById(INVALID_CONN_ID, VALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.REQUEST);
    assertNull(response);
  }

  @Test
  public void testGetHttpDetailsInvalidSession() throws Exception {
    NetworkProfiler.HttpDetailsResponse response =
      myTable.getHttpDetailsResponseById(VALID_CONN_ID, INVALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.REQUEST);
    assertNull(response);
  }

  @Test
  public void testGetHttpDetailsAccessingThreads() throws Exception {
    NetworkProfiler.HttpDetailsResponse response =
      myTable.getHttpDetailsResponseById(VALID_CONN_ID, VALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.ACCESSING_THREADS);
    assertEquals(2, response.getAccessingThreads().getThreadCount());
    assertEquals(0, response.getAccessingThreads().getThread(0).getId());
    assertEquals("threadA", response.getAccessingThreads().getThread(0).getName());
    assertEquals(1, response.getAccessingThreads().getThread(1).getId());
    assertEquals("threadB", response.getAccessingThreads().getThread(1).getName());
  }

  @Test
  public void testGetHttpDetailsAccessingThreadsInvalidSession() throws Exception {
    NetworkProfiler.HttpDetailsResponse response =
      myTable.getHttpDetailsResponseById(VALID_CONN_ID, INVALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.ACCESSING_THREADS);
    assertNull(response);
  }

  @Test
  public void testGetNetworkConnectionDataByRequest() throws Exception {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setSession(VALID_SESSION)
      .setProcessId(PROCESS_ID)
      .setStartTimestamp(100)
      .setEndTimestamp(101)
      .build();
    List<NetworkProfiler.HttpConnectionData> response = myTable.getNetworkConnectionDataByRequest(request);
    assertEquals(2, response.size());
    int offset = 0;
    assertEquals(VALID_CONN_ID + offset, response.get(offset).getConnId());
    assertEquals(100 + offset, response.get(offset).getStartTimestamp());
    offset++;
    assertEquals(VALID_CONN_ID + offset, response.get(offset).getConnId());
    assertEquals(100 + offset, response.get(offset).getStartTimestamp());
  }

  @Test
  public void testGetNetworkConnectionDataByRequestInvalidSession() throws Exception {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setSession(INVALID_SESSION)
      .setProcessId(PROCESS_ID)
      .setStartTimestamp(100)
      .setEndTimestamp(101)
      .build();
    List<NetworkProfiler.HttpConnectionData> response = myTable.getNetworkConnectionDataByRequest(request);
    assertEquals(0, response.size());
  }

  @Test
  public void testGetNetworkConnectionDataByRequestInvalidProcess() throws Exception {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setSession(VALID_SESSION)
      .setProcessId(PROCESS_ID_INVALID)
      .setStartTimestamp(100)
      .setEndTimestamp(101)
      .build();
    List<NetworkProfiler.HttpConnectionData> response = myTable.getNetworkConnectionDataByRequest(request);
    assertEquals(0, response.size());
  }

  @Test
  public void testGetNetworkConnectionDataByRequestInvalidRange() throws Exception {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest.newBuilder()
      .setSession(VALID_SESSION)
      .setProcessId(PROCESS_ID)
      .setStartTimestamp(0)
      .setEndTimestamp(10)
      .build();
    List<NetworkProfiler.HttpConnectionData> response = myTable.getNetworkConnectionDataByRequest(request);
    assertEquals(0, response.size());
  }
}
