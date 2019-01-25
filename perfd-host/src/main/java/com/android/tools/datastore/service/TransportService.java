/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.datastore.service;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.DeviceId;
import com.android.tools.datastore.LogService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DataStoreTable;
import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.datastore.poller.DeviceProcessPoller;
import com.android.tools.datastore.poller.UnifiedEventsDataPoller;
import com.android.tools.profiler.proto.Common.AgentData;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Common.Stream;
import com.android.tools.profiler.proto.Common.StreamData;
import com.android.tools.profiler.proto.Transport.AgentStatusRequest;
import com.android.tools.profiler.proto.Transport.BytesRequest;
import com.android.tools.profiler.proto.Transport.BytesResponse;
import com.android.tools.profiler.proto.Transport.ConfigureStartupAgentRequest;
import com.android.tools.profiler.proto.Transport.ConfigureStartupAgentResponse;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.ExecuteRequest;
import com.android.tools.profiler.proto.Transport.ExecuteResponse;
import com.android.tools.profiler.proto.Transport.GetDevicesRequest;
import com.android.tools.profiler.proto.Transport.GetDevicesResponse;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profiler.proto.Transport.GetProcessesRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesResponse;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profiler.proto.Transport.VersionRequest;
import com.android.tools.profiler.proto.Transport.VersionResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.google.common.collect.Maps;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Datastore layer of the unified event rpc pipeline, responsible for forwarding commands to the device and handling generic event
 * queries to the database. It currently also houses the legacy APIs for querying for devices and processes. e.g.
 * {@link #getDevices(GetDevicesRequest, StreamObserver)}, {@link #getProcesses(GetProcessesRequest, StreamObserver)}, etc.
 */
public class TransportService extends TransportServiceGrpc.TransportServiceImplBase implements ServicePassThrough {
  private final Map<Channel, DeviceProcessPoller> myLegacyPollers = Maps.newHashMap();
  private final Consumer<Runnable> myFetchExecutor;
  @NotNull private final LogService myLogService;
  @NotNull private final UnifiedEventsTable myTable;
  @NotNull private final DataStoreService myService;
  /**
   * A mapping of stream ids to active stubs. This mapping allows commands to be routed to the proper stubs.
   */
  private final HashMap<Long, TransportServiceGrpc.TransportServiceBlockingStub> myStreamIdToStub;
  /**
   * A mapping of active channels to pollers. This mapping allows us to keep track of active pollers for a channel, and clean up pollers
   * when channels are closed.
   */
  private final Map<Channel, UnifiedEventsDataPoller> myUnifiedEventsPollers = Maps.newHashMap();
  /**
   * A map of active channels to unified event streams. This map helps us clean up streams when a channel is closed.
   */
  private final Map<Channel, Stream> myChannelToStream = Maps.newHashMap();

  public TransportService(@NotNull DataStoreService service,
                          Consumer<Runnable> fetchExecutor,
                          @NotNull LogService logService) {
    myService = service;
    myFetchExecutor = fetchExecutor;
    myLogService = logService;
    myTable = new UnifiedEventsTable();
    myStreamIdToStub = new HashMap<>();
  }


  @NotNull
  @Override
  public List<DataStoreService.BackingNamespace> getBackingNamespaces() {
    return Collections.singletonList(DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE);
  }

  @Override
  public void setBackingStore(@NotNull DataStoreService.BackingNamespace namespace, @NotNull Connection connection) {
    assert namespace == DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE;
    myTable.initialize(connection);
    myTable.initialize(connection);
  }

  public void startMonitoring(Channel channel) {
    assert !myLegacyPollers.containsKey(channel);
    TransportServiceGrpc.TransportServiceBlockingStub stub = TransportServiceGrpc.newBlockingStub(channel);
    DeviceProcessPoller poller = new DeviceProcessPoller(myService, myTable, stub);
    myLegacyPollers.put(channel, poller);
    DataStoreTable.addDataStoreErrorCallback(poller);
    myFetchExecutor.accept(myLegacyPollers.get(channel));
  }

  /**
   * This call to startPolling maps a stream to a channel. This information is used in the new event pipeline.
   */
  public void startPolling(Stream stream, Channel channel) {
    TransportServiceGrpc.TransportServiceBlockingStub stub = TransportServiceGrpc.newBlockingStub(channel);
    streamConnected(stream);
    UnifiedEventsDataPoller poller = new UnifiedEventsDataPoller(stream.getStreamId(), myTable, stub);
    myUnifiedEventsPollers.put(channel, poller);
    myStreamIdToStub.put(stream.getStreamId(), stub);
    myChannelToStream.put(channel, stream);
    myFetchExecutor.accept(poller);
  }

  public void stopMonitoring(Channel channel) {
    if (myLegacyPollers.containsKey(channel)) {
      DeviceProcessPoller poller = myLegacyPollers.remove(channel);
      poller.stop();
      DataStoreTable.removeDataStoreErrorCallback(poller);
    }
    if (myUnifiedEventsPollers.containsKey(channel)) {
      UnifiedEventsDataPoller poller = myUnifiedEventsPollers.remove(channel);
      poller.stop();
      streamDisconnected(myChannelToStream.get(channel));
      myStreamIdToStub.remove(myChannelToStream.get(channel).getStreamId());
      myChannelToStream.remove(channel);
    }
  }

  private void streamConnected(Stream stream) {
    myTable.insertUnifiedEvent(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Event.newBuilder()
      .setKind(Event.Kind.STREAM)
      .setGroupId(stream.getStreamId())
      .setTimestamp(System.nanoTime())
      .setStream(StreamData.newBuilder()
                   .setStreamConnected(StreamData.StreamConnected.newBuilder()
                                         .setStream(stream)))
      .build());
  }

  private void streamDisconnected(Stream stream) {
    myTable.insertUnifiedEvent(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Event.newBuilder()
      .setKind(Event.Kind.STREAM)
      .setGroupId(stream.getStreamId())
      .setIsEnded(true)
      .setTimestamp(System.nanoTime())
      .build());
  }

  @Override
  public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> observer) {
    // This function can get called before the datastore is connected to a device as such we need to check
    // if we have a connection before attempting to get the time.
    long streamId = request.getStreamId();
    TransportServiceGrpc.TransportServiceBlockingStub client =
      myStreamIdToStub.containsKey(streamId) ? myStreamIdToStub.get(streamId) : myService.getTransportClient(DeviceId.of(streamId));
    if (client != null) {
      observer.onNext(client.getCurrentTime(request));
    }
    else {
      // Need to return something in the case of no device.
      observer.onNext(TimeResponse.getDefaultInstance());
    }
    observer.onCompleted();
  }

  @Override
  public void getVersion(VersionRequest request, StreamObserver<VersionResponse> observer) {
    long streamId = request.getStreamId();
    TransportServiceGrpc.TransportServiceBlockingStub client =
      myStreamIdToStub.containsKey(streamId) ? myStreamIdToStub.get(streamId) : myService.getTransportClient(DeviceId.of(streamId));
    if (client != null) {
      observer.onNext(client.getVersion(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getDevices(GetDevicesRequest request, StreamObserver<GetDevicesResponse> observer) {
    GetDevicesResponse response = myTable.getDevices();
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getProcesses(GetProcessesRequest request, StreamObserver<GetProcessesResponse> observer) {
    GetProcessesResponse response = myTable.getProcesses(request);
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getAgentStatus(AgentStatusRequest request, StreamObserver<AgentData> observer) {
    observer.onNext(myTable.getAgentStatus(request));
    observer.onCompleted();
  }

  @Override
  public void getBytes(BytesRequest request, StreamObserver<BytesResponse> responseObserver) {
    // TODO: Currently the cache is on demand, we want to look into caching all available files.
    BytesResponse response = myTable.getBytes(request);
    long streamId = request.getStreamId();
    TransportServiceGrpc.TransportServiceBlockingStub client =
      myStreamIdToStub.containsKey(streamId) ? myStreamIdToStub.get(streamId) : myService.getTransportClient(DeviceId.of(streamId));

    if (response == null && client != null) {
      response = client.getBytes(request);
      myTable.insertBytes(request.getStreamId(), request.getId(), response);
    }
    else if (response == null) {
      response = BytesResponse.getDefaultInstance();
    }

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void configureStartupAgent(ConfigureStartupAgentRequest request,
                                    StreamObserver<ConfigureStartupAgentResponse> observer) {
    long streamId = request.getStreamId();
    TransportServiceGrpc.TransportServiceBlockingStub client =
      myStreamIdToStub.containsKey(streamId) ? myStreamIdToStub.get(streamId) : myService.getTransportClient(DeviceId.of(streamId));
    if (client != null) {
      observer.onNext(client.configureStartupAgent(request));
    }
    else {
      observer.onNext(ConfigureStartupAgentResponse.getDefaultInstance());
    }

    observer.onCompleted();
  }

  @Override
  public void execute(ExecuteRequest request, StreamObserver<ExecuteResponse> responseObserver) {
    long streamId = request.getCommand().getStreamId();
    // TODO (b/114751407): Send stream id 0 to all streams.
    // TODO (b/114751407): Handle stream not found.
    if (myStreamIdToStub.containsKey(streamId)) {
      TransportServiceGrpc.TransportServiceBlockingStub client = myStreamIdToStub.get(streamId);
      responseObserver.onNext(client.execute(request));
      responseObserver.onCompleted();
    }
    else {
      responseObserver.onNext(ExecuteResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }

  @Override
  public void getEventGroups(GetEventGroupsRequest request, StreamObserver<GetEventGroupsResponse> responseObserver) {
    GetEventGroupsResponse.Builder response = GetEventGroupsResponse.newBuilder();
    Collection<EventGroup> events = myTable.queryUnifiedEventGroups(request);
    response.addAllGroups(events);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }
}
