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
package com.android.tools.datastore.service;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.DeviceId;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DataStoreTable;
import com.android.tools.datastore.database.ProfilerTable;
import com.android.tools.datastore.poller.ProfilerDevicePoller;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.*;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.Maps;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class hosts an EventService that will provide callers access to all cached EventData.
 * The data is populated from polling the service passed into the connectService function.
 */
public class ProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase implements ServicePassThrough {
  private final Map<Channel, ProfilerDevicePoller> myPollers = Maps.newHashMap();
  private final Consumer<Runnable> myFetchExecutor;
  private final ProfilerTable myTable;
  private final DataStoreService myService;

  public ProfilerService(@NotNull DataStoreService service,
                         Consumer<Runnable> fetchExecutor) {
    myService = service;
    myFetchExecutor = fetchExecutor;
    myTable = new ProfilerTable();
  }

  @Override
  public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> observer) {
    // This function can get called before the datastore is connected to a device as such we need to check
    // if we have a connection before attempting to get the time.
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client =
      myService.getProfilerClient(DeviceId.of(request.getDeviceId()));
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
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client =
      myService.getProfilerClient(DeviceId.of(request.getDeviceId()));
    if (client != null) {
      observer.onNext(client.getVersion(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getDevices(GetDevicesRequest request, StreamObserver<GetDevicesResponse> observer) {
    GetDevicesResponse response = myTable.getDevices(request);
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
  public void getAgentStatus(AgentStatusRequest request, StreamObserver<AgentStatusResponse> observer) {
    observer.onNext(myTable.getAgentStatus(request));
    observer.onCompleted();
  }

  @Override
  public void configureStartupAgent(ConfigureStartupAgentRequest request, StreamObserver<ConfigureStartupAgentResponse> observer) {
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client =
      myService.getProfilerClient(DeviceId.of(request.getDeviceId()));

    if (client != null) {
      observer.onNext(client.configureStartupAgent(request));
    }
    else {
      observer.onNext(ConfigureStartupAgentResponse.getDefaultInstance());
    }

    observer.onCompleted();
  }

  @Override
  public void beginSession(BeginSessionRequest request, StreamObserver<BeginSessionResponse> responseObserver) {
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client = myService.getProfilerClient(DeviceId.of(request.getDeviceId()));
    if (client == null) {
      responseObserver.onNext(BeginSessionResponse.getDefaultInstance());
    }
    else {
      BeginSessionResponse response = client.beginSession(request);
      // TODO (b/67508808) re-investigate whether we should use a poller to update the session instead.
      // The downside is we will have a delay before getSessions will see the data
      myTable.insertOrUpdateSession(response.getSession(),
                                    request.getSessionName(),
                                    request.getRequestTimeEpochMs(),
                                    request.getJvmtiConfig().getAttachAgent(),
                                    request.getJvmtiConfig().getLiveAllocationEnabled());
      responseObserver.onNext(response);
    }
    responseObserver.onCompleted();
  }

  @Override
  public void endSession(EndSessionRequest request, StreamObserver<EndSessionResponse> responseObserver) {
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client = myService.getProfilerClient(DeviceId.of(request.getDeviceId()));
    if (client == null) {
      responseObserver.onNext(EndSessionResponse.getDefaultInstance());
    }
    else {
      EndSessionResponse response = client.endSession(request);
      Common.Session session = response.getSession();
      // TODO (b/67508808) re-investigate whether we should use a poller to update the session instead.
      // The downside is we will have a delay before getSessions will see the data
      myTable.updateSessionEndTime(session.getSessionId(), session.getEndTimestamp());
      responseObserver.onNext(response);
    }
    responseObserver.onCompleted();
  }

  @Override
  public void getSessionMetaData(GetSessionMetaDataRequest request,
                                 StreamObserver<GetSessionMetaDataResponse> responseObserver) {
    responseObserver.onNext(myTable.getSessionMetaData(request.getSessionId()));
    responseObserver.onCompleted();
  }

  @Override
  public void getSessions(GetSessionsRequest request, StreamObserver<GetSessionsResponse> responseObserver) {
    responseObserver.onNext(myTable.getSessions());
    responseObserver.onCompleted();
  }

  public void startMonitoring(Channel channel) {
    assert !myPollers.containsKey(channel);
    ProfilerServiceGrpc.ProfilerServiceBlockingStub stub = ProfilerServiceGrpc.newBlockingStub(channel);
    ProfilerDevicePoller poller = new ProfilerDevicePoller(myService, myTable, stub);
    myPollers.put(channel, poller);
    DataStoreTable.addDataStoreErrorCallback(poller);
    myFetchExecutor.accept(myPollers.get(channel));
  }

  public void stopMonitoring(Channel channel) {
    if (myPollers.containsKey(channel)) {
      ProfilerDevicePoller poller = myPollers.remove(channel);
      poller.stop();
      DataStoreTable.removeDataStoreErrorCallback(poller);
    }
  }

  @Override
  public void getBytes(BytesRequest request, StreamObserver<BytesResponse> responseObserver) {
    // TODO: Currently the cache is on demand, we want to look into caching all available files.
    BytesResponse response = myTable.getBytes(request);
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client =
      myService.getProfilerClient(DeviceId.fromSession(request.getSession()));

    if (response == null && client != null) {
      response = myService.getProfilerClient(DeviceId.fromSession(request.getSession())).getBytes(request);
      myTable.insertOrUpdateBytes(request.getId(), request.getSession(), response);
    }
    else if (response == null) {
      response = BytesResponse.getDefaultInstance();
    }

    responseObserver.onNext(response);
    responseObserver.onCompleted();
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
  }
}
