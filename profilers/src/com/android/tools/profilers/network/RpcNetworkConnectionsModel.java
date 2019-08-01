/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Network;
import com.android.tools.profiler.proto.Network.NetworkHttpConnectionData;
import com.android.tools.profiler.proto.Transport.BytesRequest;
import com.android.tools.profiler.proto.Transport.BytesResponse;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.intellij.openapi.util.text.StringUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link NetworkConnectionsModel} that uses the new event pipeline to fetch http connection data originated from an app.
 */
public class RpcNetworkConnectionsModel implements NetworkConnectionsModel {
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myTransportService;
  @NotNull private final Common.Session mySession;

  public RpcNetworkConnectionsModel(@NotNull TransportServiceGrpc.TransportServiceBlockingStub transportService,
                                    @NotNull Common.Session session) {
    myTransportService = transportService;
    mySession = session;
  }

  @NotNull
  @Override
  public List<HttpData> getData(@NotNull Range timeCurrentRangeUs) {
    long queryStartTimeNs = TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin());
    long queryEndTimeNs = TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax());
    GetEventGroupsRequest request = GetEventGroupsRequest.newBuilder()
      .setStreamId(mySession.getStreamId())
      .setPid(mySession.getPid())
      .setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION)
      .build();
    GetEventGroupsResponse response = myTransportService.getEventGroups(request);

    GetEventGroupsRequest threadRequest = GetEventGroupsRequest.newBuilder()
      .setStreamId(mySession.getStreamId())
      .setPid(mySession.getPid())
      .setKind(Common.Event.Kind.NETWORK_HTTP_THREAD)
      .build();
    Map<Long, List<Common.Event>> connectionThreadMap = myTransportService.getEventGroups(threadRequest)
      .getGroupsList().stream().collect(Collectors.toMap(EventGroup::getGroupId, EventGroup::getEventsList));

    List<HttpData> httpDataList = new ArrayList<>(response.getGroupsCount());
    for (EventGroup connectionGroup : response.getGroupsList()) {
      // Skip event groups that occur before or after the query range.
      if (connectionGroup.getEvents(0).getTimestamp() > queryEndTimeNs ||
          (connectionGroup.getEvents(connectionGroup.getEventsCount() - 1).getTimestamp() < queryStartTimeNs &&
           connectionGroup.getEvents(connectionGroup.getEventsCount() - 1).getIsEnded())) {
        continue;
      }

      Map<NetworkHttpConnectionData.UnionCase, Common.Event> events = new HashMap<>();
      connectionGroup.getEventsList().forEach(e -> events.put(e.getNetworkHttpConnection().getUnionCase(), e));
      Common.Event requestStartEvent =
        events.getOrDefault(NetworkHttpConnectionData.UnionCase.HTTP_REQUEST_STARTED, Common.Event.getDefaultInstance());
      Common.Event requestCompleteEvent =
        events.getOrDefault(NetworkHttpConnectionData.UnionCase.HTTP_REQUEST_COMPLETED, Common.Event.getDefaultInstance());
      Common.Event responseStartEvent =
        events.getOrDefault(NetworkHttpConnectionData.UnionCase.HTTP_RESPONSE_STARTED, Common.Event.getDefaultInstance());
      Common.Event responseCompleteEvent =
        events.getOrDefault(NetworkHttpConnectionData.UnionCase.HTTP_RESPONSE_COMPLETED, Common.Event.getDefaultInstance());
      Common.Event connectionEndEvent =
        events.getOrDefault(NetworkHttpConnectionData.UnionCase.HTTP_CLOSED, Common.Event.getDefaultInstance());

      // Ingore the group if we missed the starting request event.
      if (requestStartEvent.equals(Common.Event.getDefaultInstance())) {
        continue;
      }

      // We must also have thread information associated with the connection.
      if (!connectionThreadMap.containsKey(connectionGroup.getGroupId())) {
        continue;
      }

      long requestStartTimeUs = TimeUnit.NANOSECONDS.toMicros(requestStartEvent.getTimestamp());
      long requestCompleteTimeUs = TimeUnit.NANOSECONDS.toMicros(requestCompleteEvent.getTimestamp());
      long respondStartTimeUs = TimeUnit.NANOSECONDS.toMicros(responseStartEvent.getTimestamp());
      long respondCompleteTimeUs = TimeUnit.NANOSECONDS.toMicros(responseCompleteEvent.getTimestamp());
      long connectionEndTimeUs = TimeUnit.NANOSECONDS.toMicros(connectionEndEvent.getTimestamp());
      List<HttpData.JavaThread> threadData = connectionThreadMap.get(connectionGroup.getGroupId()).stream()
        .map(e -> e.getNetworkHttpThread()).map(proto -> new HttpData.JavaThread(proto.getId(), proto.getName()))
        .collect(Collectors.toList());

      HttpData.Builder httpBuilder =
        new HttpData.Builder(
          connectionGroup.getGroupId(),
          requestStartTimeUs,
          requestCompleteTimeUs,
          respondStartTimeUs,
          respondCompleteTimeUs,
          connectionEndTimeUs,
          threadData);

      Network.NetworkHttpConnectionData.HttpRequestStarted requestStartData =
        requestStartEvent.getNetworkHttpConnection().getHttpRequestStarted();
      httpBuilder.setUrl(requestStartData.getUrl());
      httpBuilder.setMethod(requestStartData.getMethod());
      httpBuilder.setTrace(requestStartData.getTrace());
      httpBuilder.setRequestFields(requestStartData.getFields());
      if (!requestCompleteEvent.equals(Common.Event.getDefaultInstance())) {
        httpBuilder.setRequestPayloadId(requestCompleteEvent.getNetworkHttpConnection().getHttpRequestCompleted().getPayloadId());
      }
      if (!responseStartEvent.equals(Common.Event.getDefaultInstance())) {
        httpBuilder.setResponseFields(responseStartEvent.getNetworkHttpConnection().getHttpResponseStarted().getFields());
      }
      if (!responseCompleteEvent.equals(Common.Event.getDefaultInstance())) {
        httpBuilder.setResponsePayloadId(responseCompleteEvent.getNetworkHttpConnection().getHttpResponseCompleted().getPayloadId());
        httpBuilder.setResponsePayloadSize(responseCompleteEvent.getNetworkHttpConnection().getHttpResponseCompleted().getPayloadSize());
      }

      httpDataList.add(httpBuilder.build());
    }

    return httpDataList;
  }

  @NotNull
  @Override
  public ByteString requestBytes(@NotNull String id) {
    if (StringUtil.isEmpty(id)) {
      return ByteString.EMPTY;
    }

    BytesRequest request = BytesRequest.newBuilder()
      .setStreamId(mySession.getStreamId())
      .setId(id)
      .build();

    BytesResponse response = myTransportService.getBytes(request);
    return response.getContents();
  }
}
