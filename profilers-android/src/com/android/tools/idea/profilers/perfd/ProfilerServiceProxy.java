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
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.text.StringUtil;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.ddmlib.Client.CHANGE_NAME;

/**
 * A proxy ProfilerService on host that intercepts grpc requests from perfd-host to device perfd.
 * This enables us to support legacy workflows based on device's API levels.
 */
public class ProfilerServiceProxy extends PerfdProxyService
  implements AndroidDebugBridge.IClientChangeListener, AndroidDebugBridge.IDeviceChangeListener {

  private ProfilerServiceGrpc.ProfilerServiceBlockingStub myServiceStub;
  private final IDevice myDevice;
  private final Profiler.Device myProfilerDevice;
  private final List<Profiler.Process> myProcesses = Collections.synchronizedList(new ArrayList<>());

  public ProfilerServiceProxy(@NotNull IDevice device, @NotNull ManagedChannel channel) {
    super(ProfilerServiceGrpc.getServiceDescriptor());
    myDevice = device;
    myServiceStub = ProfilerServiceGrpc.newBlockingStub(channel);
    myProfilerDevice = Profiler.Device.newBuilder()
      .setSerial(device.getSerialNumber())
      .setModel(DevicePropertyUtil.getModel(device, ""))
      .setVersion(StringUtil.notNullize(device.getProperty(IDevice.PROP_BUILD_VERSION)))
      .setApi(Integer.toString(device.getVersion().getApiLevel()))
      .setManufacturer(DevicePropertyUtil.getManufacturer(device, ""))
      .setIsEmulator(device.isEmulator())
      .setState(convertState(device.getState()))
      //TODO: Change this to use the device boot_id, using the serial number
      // to keep a consistent ID across plug/unplug sessions.
      .setBootId(Integer.toString(device.getSerialNumber().hashCode()))
      .build();
    updateProcesses();

    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addClientChangeListener(this);
  }

  private static Profiler.Device.State convertState(IDevice.DeviceState state) {
    switch (state) {
      case OFFLINE:
        return Profiler.Device.State.OFFLINE;

      case ONLINE:
        return Profiler.Device.State.ONLINE;

      case DISCONNECTED:
        return Profiler.Device.State.DISCONNECTED;

      default:
        return Profiler.Device.State.UNSPECIFIED;
    }
  }

  @Override
  public void disconnect() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
    AndroidDebugBridge.removeClientChangeListener(this);
  }

  public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
    Profiler.GetDevicesResponse response = Profiler.GetDevicesResponse.newBuilder().addDevice(myProfilerDevice).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> responseObserver) {
    synchronized (myProcesses) {
      Profiler.GetProcessesResponse response = Profiler.GetProcessesResponse.newBuilder().addAllProcess(myProcesses).build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
    // Don't care
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
    // Don't care
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    // This event can be triggered when a device goes offline. However, updateProcesses expects
    // an online device, so just ignore the event in that case.
    if (device.isOnline() && (changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
      updateProcesses();
    }
  }

  @Override
  public void clientChanged(@NonNull Client client, int changeMask) {
    if ((changeMask & CHANGE_NAME) != 0) {
      updateProcesses();
    }
  }

  private void updateProcesses() {
    synchronized (myProcesses) {
      myProcesses.clear();
      assert myDevice.isOnline();
      // Only request the time if we have processes, this is needed as the service test calls this function
      // without setting up a full profiler service
      if (myDevice.getClients().length > 0) {
        Profiler.TimeResponse times = myServiceStub.getCurrentTime(Profiler.TimeRequest.getDefaultInstance());
        Profiler.DeviceProcesses.Builder deviceProcesses = Profiler.DeviceProcesses.newBuilder();
        deviceProcesses.setDevice(myProfilerDevice);
        // TODO: getTimes should take the device
        for (Client client : myDevice.getClients()) {
          String description = client.getClientData().getClientDescription();
          if (description == null) {
            continue; // Process is still starting up and not ready yet
          }
          deviceProcesses.addProcess(Profiler.Process.newBuilder()
                                       .setName(description)
                                       .setPid(client.getClientData().getPid())
                                       .setState(Profiler.Process.State.ALIVE)
                                       // TODO: Set this to the applications actual start time.
                                       .setStartTimestampNs(times.getTimestampNs())
                                       .build());
        }
        myProcesses.addAll(deviceProcesses.getProcessList());
      }
    }
  }

  @Override
  public ServerServiceDefinition getServiceDefinition() {
    Map<MethodDescriptor, ServerCallHandler> overrides = Maps.newHashMap();
    overrides.put(ProfilerServiceGrpc.METHOD_GET_DEVICES,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getDevices((Profiler.GetDevicesRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(ProfilerServiceGrpc.METHOD_GET_PROCESSES,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getProcesses((Profiler.GetProcessesRequest)request, (StreamObserver)observer);
                  }));
    return generatePassThroughDefinitions(overrides, myServiceStub);
  }
}
