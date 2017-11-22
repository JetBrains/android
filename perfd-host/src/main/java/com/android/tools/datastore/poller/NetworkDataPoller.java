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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.database.NetworkTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.NetworkProfiler.*;
import com.android.tools.profiler.proto.NetworkServiceGrpc;

// TODO: Implement a storage container that can read/write data to disk
public class NetworkDataPoller extends PollRunner {
  // Intentionally accessing this field out of sync block because it's OK for it to be o
  // off by a frame; we'll pick up all data eventually
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private long myHttpRangeRequestStartTimeNs = Long.MIN_VALUE;
  private int myProcessId = -1;
  // TODO: Key data off device session.
  private Common.Session mySession;
  private NetworkTable myNetworkTable;
  NetworkServiceGrpc.NetworkServiceBlockingStub myPollingService;

  public NetworkDataPoller(int processId,
                           Common.Session session,
                           NetworkTable table,
                           NetworkServiceGrpc.NetworkServiceBlockingStub pollingService) {
    super(POLLING_DELAY_NS);
    myProcessId = processId;
    myNetworkTable = table;
    mySession = session;
    myPollingService = pollingService;
  }

  @Override
  public void poll() {
    if (myProcessId == -1) {
      return;
    }
    NetworkDataRequest.Builder dataRequestBuilder = NetworkDataRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE)
      .setType(NetworkDataRequest.Type.ALL);
    NetworkDataResponse response = myPollingService.getData(dataRequestBuilder.build());

    for (NetworkProfilerData data : response.getDataList()) {
      myDataRequestStartTimestampNs = Math.max(myDataRequestStartTimestampNs, data.getBasicInfo().getEndTimestamp());
      myNetworkTable.insert(data.getBasicInfo().getProcessId(), data);
    }
    pollHttpRange();
  }

  private void pollHttpRange() {
    HttpRangeRequest.Builder requestBuilder = HttpRangeRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(myHttpRangeRequestStartTimeNs)
      .setEndTimestamp(Long.MAX_VALUE);
    HttpRangeResponse httpRange = myPollingService.getHttpRange(requestBuilder.build());

    for (HttpConnectionData connection : httpRange.getDataList()) {
      myHttpRangeRequestStartTimeNs = Math.max(myHttpRangeRequestStartTimeNs, connection.getStartTimestamp() + 1);
      myHttpRangeRequestStartTimeNs = Math.max(myHttpRangeRequestStartTimeNs, connection.getEndTimestamp() + 1);
      HttpDetailsResponse initialData = myNetworkTable.getHttpDetailsResponseById(connection.getConnId(),
                                                                                  mySession,
                                                                                  HttpDetailsRequest.Type.REQUEST);

      HttpDetailsResponse request = initialData;
      HttpDetailsResponse requestBody =
        myNetworkTable.getHttpDetailsResponseById(connection.getConnId(), mySession, HttpDetailsRequest.Type.REQUEST_BODY);
      HttpDetailsResponse response = null;
      HttpDetailsResponse responseBody = null;
      HttpDetailsResponse threads;
      if (initialData == null) {
        request = pollHttpDetails(connection.getConnId(), HttpDetailsRequest.Type.REQUEST);
      }
      if (connection.getUploadedTimestamp() != 0) {
        requestBody = pollHttpDetails(connection.getConnId(), HttpDetailsRequest.Type.REQUEST_BODY);
      }
      if (connection.getEndTimestamp() != 0) {
        response = pollHttpDetails(connection.getConnId(), HttpDetailsRequest.Type.RESPONSE);
        responseBody = pollHttpDetails(connection.getConnId(), HttpDetailsRequest.Type.RESPONSE_BODY);
      }
      threads = pollHttpDetails(connection.getConnId(), HttpDetailsRequest.Type.ACCESSING_THREADS);
      myNetworkTable.insertOrReplace(myProcessId, mySession, request, response, requestBody, responseBody, threads, connection);
    }
  }

  private HttpDetailsResponse pollHttpDetails(long id, HttpDetailsRequest.Type type) {
    HttpDetailsRequest request = HttpDetailsRequest.newBuilder()
      .setConnId(id)
      .setType(type)
      .build();
    return myPollingService.getHttpDetails(request);
  }
}