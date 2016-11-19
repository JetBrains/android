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

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.Maps;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RunnableFuture;

/**
 * This class hosts an EventService that will provide callers access to all cached EventData.
 * The data is populated from polling the service passed into the connectService function.
 */
public class ProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase implements ServicePassThrough  {

  private final DataStoreService myService;
  ProfilerServiceGrpc.ProfilerServiceBlockingStub myPollingService;

  List<Profiler.Device> myDevices = new LinkedList<>();
  Map<String, List<Profiler.Process>> myProcesses = Maps.newHashMap();

  public ProfilerService(@NotNull DataStoreService service) {
    myService = service;
  }

  @Override
  public void getTimes(Profiler.TimesRequest request, StreamObserver<Profiler.TimesResponse> observer) {
    // This function can get called before the datastore is connected to a device as such we need to check
    // if we have a connection before attempting to get the time.
    if (myPollingService != null) {
      observer.onNext(myPollingService.getTimes(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> observer) {
    if (myPollingService != null) {
      observer.onNext(myPollingService.getVersion(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> observer) {
    Profiler.GetDevicesResponse response = Profiler.GetDevicesResponse.newBuilder().addAllDevice(myDevices).build();
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> observer) {
    List<Profiler.Process> processes = myProcesses.get(request.getSerial());
    Profiler.GetProcessesResponse response = Profiler.GetProcessesResponse.newBuilder().addAllProcess(processes).build();
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void connect(Profiler.ConnectRequest request, StreamObserver<Profiler.ConnectResponse> observer) {
    // TODO: Add support for multiple connections
    myService.connect(request.getPort());
    observer.onNext(Profiler.ConnectResponse.getDefaultInstance());
    observer.onCompleted();
  }

  @Override
  public void disconnect(Profiler.DisconnectRequest request, StreamObserver<Profiler.DisconnectResponse> observer) {
    myService.disconnect();
    observer.onNext(Profiler.DisconnectResponse.getDefaultInstance());
    observer.onCompleted();
  }

  @Override
  public void setProcesses(Profiler.SetProcessesRequest request, StreamObserver<Profiler.SetProcessesResponse> observer) {
    List<Profiler.DeviceProcesses> list = request.getDeviceProcessesList();
    myProcesses.clear();
    myDevices.clear();
    for (Profiler.DeviceProcesses processes : list) {
      myDevices.add(processes.getDevice());
      myProcesses.put(processes.getDevice().getSerial(), processes.getProcessList());
    }

    observer.onNext(Profiler.SetProcessesResponse.getDefaultInstance());
    observer.onCompleted();
  }

  @Override
  public ServerServiceDefinition getService() {
    return bindService();
  }

  @Override
  public void connectService(ManagedChannel channel) {
    myPollingService = ProfilerServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public RunnableFuture<Void> getRunner() { return null; }
}
