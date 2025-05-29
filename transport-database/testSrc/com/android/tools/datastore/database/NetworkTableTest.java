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
import com.android.tools.profiler.proto.NetworkProfiler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class NetworkTableTest extends DatabaseTest<NetworkTable> {
  private static final int VALID_CONN_ID = 3;
  private static final int INVALID_CONN_ID = -1;
  private static final int TEST_DATA = 10;
  private static final Common.Session VALID_SESSION = Common.Session.newBuilder().setSessionId(1L).setStreamId(1234).build();
  private static final Common.Session INVALID_SESSION = Common.Session.newBuilder().setSessionId(-1L).setStreamId(4321).build();

  @Override
  public void before() throws Exception {
    super.before();
    populateDatabase();
  }

  @Override
  @NotNull
  protected NetworkTable createTable() {
    return new NetworkTable();
  }

  @Override
  @NotNull
  protected List<Consumer<NetworkTable>> getTableQueryMethodsForVerification() {
    List<Consumer<NetworkTable>> methodCalls = new ArrayList<>();
    methodCalls.add((table) -> assertThat(
      table.getHttpDetailsResponseById(0, Common.Session.getDefaultInstance(), NetworkProfiler.HttpDetailsRequest.Type.REQUEST)).isNull());
    methodCalls
      .add((table) -> assertThat(table.getNetworkConnectionDataByRequest(NetworkProfiler.HttpRangeRequest.getDefaultInstance())).isEmpty());
    methodCalls
      .add((table) -> assertThat(table.getNetworkDataByRequest(NetworkProfiler.NetworkDataRequest.getDefaultInstance())).isEmpty());
    methodCalls.add((table) -> table.insert(Common.Session.getDefaultInstance(), NetworkProfiler.NetworkProfilerData.getDefaultInstance()));
    methodCalls.add((table) -> {
      NetworkProfiler.HttpDetailsResponse defaultData = NetworkProfiler.HttpDetailsResponse.getDefaultInstance();
      table.insertOrReplace(Common.Session.getDefaultInstance(), defaultData, defaultData, defaultData, defaultData, defaultData,
                            NetworkProfiler.HttpConnectionData
                              .getDefaultInstance());
    });
    return methodCalls;
  }

  private void populateDatabase() {
    for (int i = 0; i < TEST_DATA; i++) {
      NetworkProfiler.HttpConnectionData connection = NetworkProfiler.HttpConnectionData
        .newBuilder().setConnId(VALID_CONN_ID + i).setStartTimestamp(100 + i).setEndTimestamp(101 + i).build();
      NetworkProfiler.HttpDetailsResponse request = NetworkProfiler.HttpDetailsResponse
        .newBuilder().setRequest(NetworkProfiler.HttpDetailsResponse.Request.newBuilder().setUrl("TestUrl")).build();
      NetworkProfiler.HttpDetailsResponse threads = NetworkProfiler.HttpDetailsResponse
        .newBuilder()
        .setAccessingThreads(
          NetworkProfiler.HttpDetailsResponse.AccessingThreads
            .newBuilder()
            .addThread(NetworkProfiler.JavaThread.newBuilder().setId(0).setName("threadA"))
            .addThread(NetworkProfiler.JavaThread.newBuilder().setId(1).setName("threadB")))
        .build();
      getTable().insertOrReplace(VALID_SESSION, request, null, null, null, threads, connection);
    }
  }

  @Test
  public void testGetHttpDetails() {
    NetworkProfiler.HttpDetailsResponse response =
      getTable().getHttpDetailsResponseById(VALID_CONN_ID, VALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.REQUEST);
    assertThat(response.getRequest().getUrl()).isEqualTo("TestUrl");
  }

  @Test
  public void testGetHttpDetailsInvalidConnId() {
    NetworkProfiler.HttpDetailsResponse response =
      getTable().getHttpDetailsResponseById(INVALID_CONN_ID, VALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.REQUEST);
    assertThat(response).isNull();
  }

  @Test
  public void testGetHttpDetailsInvalidSession() {
    NetworkProfiler.HttpDetailsResponse response =
      getTable().getHttpDetailsResponseById(VALID_CONN_ID, INVALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.REQUEST);
    assertThat(response).isNull();
  }

  @Test
  public void testGetHttpDetailsAccessingThreads() {
    NetworkProfiler.HttpDetailsResponse response =
      getTable().getHttpDetailsResponseById(VALID_CONN_ID, VALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.ACCESSING_THREADS);
    assertThat(response.getAccessingThreads().getThreadCount()).isEqualTo(2);
    assertThat(response.getAccessingThreads().getThread(0).getId()).isEqualTo(0);
    assertThat(response.getAccessingThreads().getThread(0).getName()).isEqualTo("threadA");
    assertThat(response.getAccessingThreads().getThread(1).getId()).isEqualTo(1);
    assertThat(response.getAccessingThreads().getThread(1).getName()).isEqualTo("threadB");
  }

  @Test
  public void testGetHttpDetailsAccessingThreadsInvalidSession() {
    NetworkProfiler.HttpDetailsResponse response =
      getTable().getHttpDetailsResponseById(VALID_CONN_ID, INVALID_SESSION, NetworkProfiler.HttpDetailsRequest.Type.ACCESSING_THREADS);
    assertThat(response).isNull();
  }

  @Test
  public void testGetNetworkConnectionDataByRequest() {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest
      .newBuilder().setSession(VALID_SESSION).setStartTimestamp(100).setEndTimestamp(101).build();
    List<NetworkProfiler.HttpConnectionData> response = getTable().getNetworkConnectionDataByRequest(request);
    assertThat(response).hasSize(2);
    int offset = 0;
    assertThat(response.get(offset).getConnId()).isEqualTo(VALID_CONN_ID + offset);
    assertThat(response.get(offset).getStartTimestamp()).isEqualTo(100 + offset);
    offset++;
    assertThat(response.get(offset).getConnId()).isEqualTo(VALID_CONN_ID + offset);
    assertThat(response.get(offset).getStartTimestamp()).isEqualTo(100 + offset);
  }

  @Test
  public void testGetNetworkConnectionDataByRequestInvalidSession() {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest
      .newBuilder().setSession(INVALID_SESSION).setStartTimestamp(100).setEndTimestamp(101).build();
    List<NetworkProfiler.HttpConnectionData> response = getTable().getNetworkConnectionDataByRequest(request);
    assertThat(response).isEmpty();
  }

  @Test
  public void testGetNetworkConnectionDataByRequestInvalidRange() {
    NetworkProfiler.HttpRangeRequest request = NetworkProfiler.HttpRangeRequest
      .newBuilder().setSession(VALID_SESSION).setStartTimestamp(0).setEndTimestamp(10).build();
    List<NetworkProfiler.HttpConnectionData> response = getTable().getNetworkConnectionDataByRequest(request);
    assertThat(response).isEmpty();
  }
}
