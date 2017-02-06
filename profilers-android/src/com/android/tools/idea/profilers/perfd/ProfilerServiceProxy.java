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
package com.android.tools.idea.profilers.perfd;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.Maps;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.android.ddmlib.Client.CHANGE_NAME;

/**
 * A proxy ProfilerService on host that intercepts grpc requests from perfd-host to device perfd.
 * This enables us to support legacy workflows based on device's API levels.
 */
public class ProfilerServiceProxy extends ProfilerServiceGrpc.ProfilerServiceImplBase implements AndroidDebugBridge.IClientChangeListener {

  private ProfilerServiceGrpc.ProfilerServiceBlockingStub myServiceStub;
  private final IDevice myDevice;
  private final Profiler.Device myProfilerDevice;
  // TODO synchronize process list.
  private final Map<Common.Session, List<Profiler.Process>> myProcesses = Maps.newHashMap();

  public ProfilerServiceProxy(@NotNull IDevice device, @NotNull ManagedChannel channel) {
    myDevice = device;
    myServiceStub = ProfilerServiceGrpc.newBlockingStub(channel);
    myProfilerDevice = Profiler.Device.newBuilder()
      .setSerial(device.getSerialNumber())
      .setModel(device.getName())
      .setApi(Integer.toString(device.getVersion().getApiLevel()))
      //TODO: Change this to use the device boot_id.
      .setBootId(Integer.toString(device.hashCode()))
      .build();
    updateProcesses();

    // TODO remove listeners when this class instance goes away.
    AndroidDebugBridge.addClientChangeListener(this);
  }

  @Override
  public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
    Profiler.GetDevicesResponse response = Profiler.GetDevicesResponse.newBuilder().addDevice(myProfilerDevice).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> responseObserver) {
    List<Profiler.Process> processes = myProcesses.get(request.getSession());
    Profiler.GetProcessesResponse response = Profiler.GetProcessesResponse.newBuilder().addAllProcess(processes).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void clientChanged(@NonNull Client client, int changeMask) {
    if ((changeMask & CHANGE_NAME) != 0) {
      updateProcesses();
    }
  }

  private void updateProcesses() {
    myProcesses.clear();
    assert myDevice.isOnline();

    Profiler.DeviceProcesses.Builder deviceProcesses = Profiler.DeviceProcesses.newBuilder();
    deviceProcesses.setDevice(myProfilerDevice);
    for (Client client : myDevice.getClients()) {
      String description = client.getClientData().getClientDescription();
      deviceProcesses.addProcess(Profiler.Process.newBuilder()
                                   .setName(description == null ? "[UNKNOWN]" : description)
                                   .setPid(client.getClientData().getPid())
                                   .build());
    }
    Common.Session session = Common.Session.newBuilder()
      .setBootId(myProfilerDevice.getBootId())
      .setDeviceSerial(myProfilerDevice.getSerial())
      .build();
    myProcesses.put(session, deviceProcesses.getProcessList());
  }

  /**
   * TODO: instead of override bindServer(), we should use a generic way to redirect grpc calls to the service stub for all methods
   * that are not overridden in this proxy service. Same goes for all the proxy service implementation.
   */
  @Override
  public ServerServiceDefinition bindService() {
    return ServerServiceDefinition.builder(ProfilerServiceGrpc.getServiceDescriptor())
      // getDevices + getProcesses are handled locally in this service.
      .addMethod(ProfilerServiceGrpc.METHOD_GET_DEVICES,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   getDevices(request, observer);
                 }))
      .addMethod(ProfilerServiceGrpc.METHOD_GET_PROCESSES,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   getProcesses(request, observer);
                 }))
      // the rest of the MethodDefinitions are redirected to the connected service if one exists.
      .addMethod(ProfilerServiceGrpc.METHOD_GET_CURRENT_TIME,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getCurrentTime(request));
                   observer.onCompleted();
                 }))
      .addMethod(ProfilerServiceGrpc.METHOD_GET_VERSION,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getVersion(request));
                   observer.onCompleted();
                 }))
      .addMethod(ProfilerServiceGrpc.METHOD_GET_BYTES,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getBytes(request));
                   observer.onCompleted();
                 }))

      .build();
  }
}
