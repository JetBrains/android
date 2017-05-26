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
package com.android.tools.profilers;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.util.containers.MultiMap;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
  public static final String VERSION = "3141592";
  private final Map<Common.Session, Profiler.Device> myDevices;
  private final MultiMap<Profiler.Device, Profiler.Process> myProcesses;
  private final Map<String, ByteString> myCache;
  private long myTimestampNs;
  private boolean myThrowErrorOnGetDevices;
  private boolean myAttachAgentCalled;
  private Profiler.AgentStatusResponse.Status myAgentStatus;

  public FakeProfilerService() {
    this(true);
  }

  public void reset() {
    myAttachAgentCalled = false;
  }

  /**
   * Creates a fake profiler service. If connected is true there will be a device with a process already present.
   */
  public FakeProfilerService(boolean connected) {
    myDevices = new HashMap<>();
    myProcesses = MultiMap.create();
    myCache = new HashMap<>();
    if (connected) {
      Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
      Profiler.Process process = Profiler.Process.newBuilder()
        .setPid(20)
        .setState(Profiler.Process.State.ALIVE)
        .setName("FakeProcess")
        .build();
      addDevice(device);
      Common.Session session = Common.Session.newBuilder()
        .setBootId(device.getBootId())
        .setDeviceSerial(device.getSerial())
        .build();
      addProcess(session, process);
    }
  }

  public void addProcess(Common.Session session, Profiler.Process process) {
    if (!myDevices.containsKey(session)) {
      throw new IllegalArgumentException("Invalid device serial: " + session);
    }
    myProcesses.putValue(myDevices.get(session), process);
  }

  public void removeProcess(Common.Session session, Profiler.Process process) {
    if (!myDevices.containsKey(session)) {
      throw new IllegalArgumentException("Invalid device serial: " + session);
    }
    myProcesses.remove(myDevices.get(session), process);
  }

  public void addDevice(Profiler.Device device) {
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myDevices.put(session, device);
  }

  public void updateDevice(Common.Session session, Profiler.Device oldDevice, Profiler.Device newDevice) {
    // Move processes from old to new device
    myProcesses.putValues(newDevice, myProcesses.get(oldDevice));
    // Remove old device from processes map.
    myProcesses.remove(oldDevice);
    // Update device on devices map
    myDevices.put(session, newDevice);
  }

  public void addFile(String id, ByteString contents) {
    myCache.put(id, contents);
  }

  public void setTimestampNs(long timestamp) {
    myTimestampNs = timestamp;
  }

  @Override
  public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> responseObserver) {
    responseObserver.onNext(Profiler.VersionResponse.newBuilder().setVersion(VERSION).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
    if (myThrowErrorOnGetDevices) {
      responseObserver.onError(new RuntimeException("Server error"));
      return;
    }
    Profiler.GetDevicesResponse.Builder response = Profiler.GetDevicesResponse.newBuilder();
    for (Profiler.Device device : myDevices.values()) {
      response.addDevice(device);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> responseObserver) {
    Profiler.GetProcessesResponse.Builder response = Profiler.GetProcessesResponse.newBuilder();
    Common.Session serial = request.getSession();
    Profiler.Device device = myDevices.get(serial);
    if (device != null) {
      for (Profiler.Process process : myProcesses.get(device)) {
        response.addProcess(process);
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getCurrentTime(Profiler.TimeRequest request, StreamObserver<Profiler.TimeResponse> responseObserver) {
    Profiler.TimeResponse.Builder response = Profiler.TimeResponse.newBuilder();
    response.setTimestampNs(myTimestampNs);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getBytes(Profiler.BytesRequest request, StreamObserver<Profiler.BytesResponse> responseObserver) {
    Profiler.BytesResponse.Builder builder = Profiler.BytesResponse.newBuilder();
    ByteString bytes = myCache.get(request.getId());
    if (bytes != null) {
      builder.setContents(bytes);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getAgentStatus(Profiler.AgentStatusRequest request, StreamObserver<Profiler.AgentStatusResponse> responseObserver) {
    Profiler.AgentStatusResponse.Builder builder = Profiler.AgentStatusResponse.newBuilder();
    if (myAgentStatus != null) {
      builder.setStatus(myAgentStatus);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void attachAgent(Profiler.AgentAttachRequest request, StreamObserver<Profiler.AgentAttachResponse> responseObserver) {
    myAttachAgentCalled = true;
    responseObserver.onNext(Profiler.AgentAttachResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  public void setAgentStatus(@NotNull Profiler.AgentStatusResponse.Status status) {
    myAgentStatus = status;
  }

  public void setThrowErrorOnGetDevices(boolean throwErrorOnGetDevices) {
    myThrowErrorOnGetDevices = throwErrorOnGetDevices;
  }

  public boolean getAgentAttachCalled() {
    return myAttachAgentCalled;
  }
}
