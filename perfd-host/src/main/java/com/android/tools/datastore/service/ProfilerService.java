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
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.datastore.database.ProfilerTable;
import com.android.tools.datastore.poller.ProfilerDevicePoller;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.Maps;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Consumer;

/**
 * This class hosts an EventService that will provide callers access to all cached EventData.
 * The data is populated from polling the service passed into the connectService function.
 */
public class ProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase implements ServicePassThrough {
  private Map<ManagedChannel, ProfilerDevicePoller> myPollers = Maps.newHashMap();
  private Consumer<Runnable> myFetchExecutor;
  private ProfilerTable myTable = new ProfilerTable();
  private DataStoreService myService;

  public ProfilerService(@NotNull DataStoreService service, Consumer<Runnable> fetchExecutor) {
    myService = service;
    myFetchExecutor = fetchExecutor;
  }

  @Override
  public void getCurrentTime(Profiler.TimeRequest request, StreamObserver<Profiler.TimeResponse> observer) {
    // This function can get called before the datastore is connected to a device as such we need to check
    // if we have a connection before attempting to get the time.
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client = myService.getProfilerClient(request.getSession());
    if (client != null) {
      observer.onNext(client.getCurrentTime(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> observer) {
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client = myService.getProfilerClient(request.getSession());
    if (client != null) {
      observer.onNext(client.getVersion(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> observer) {
    Profiler.GetDevicesResponse response = myTable.getDevices(request);
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> observer) {
    Profiler.GetProcessesResponse response = myTable.getProcesses(request);
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getAgentStatus(Profiler.AgentStatusRequest request, StreamObserver<Profiler.AgentStatusResponse> observer) {
    observer.onNext(myTable.getAgentStatus(request));
    observer.onCompleted();
  }

  public void startMonitoring(ManagedChannel channel) {
    assert !myPollers.containsKey(channel);
    ProfilerServiceGrpc.ProfilerServiceBlockingStub stub = ProfilerServiceGrpc.newBlockingStub(channel);
    myPollers.put(channel, new ProfilerDevicePoller(myService, myTable, stub));
    myFetchExecutor.accept(myPollers.get(channel));
  }

  public void stopMonitoring(ManagedChannel channel) {
    if (myPollers.containsKey(channel)) {
      ProfilerDevicePoller poller = myPollers.remove(channel);
      poller.stop();
    }
  }

  @Override
  public void getBytes(Profiler.BytesRequest request, StreamObserver<Profiler.BytesResponse> responseObserver) {
    // TODO: This should check a local cache of files (either in a database or on disk) before
    // making the request against the device
    responseObserver.onNext(myService.getProfilerClient(request.getSession()).getBytes(request));
    responseObserver.onCompleted();
  }

  @Override
  public DatastoreTable getDatastoreTable() {
    return myTable;
  }
}
