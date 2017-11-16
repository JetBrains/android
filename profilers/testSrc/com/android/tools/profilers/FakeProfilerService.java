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
import com.android.tools.profiler.proto.Profiler.*;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.util.containers.MultiMap;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
  public static final String VERSION = "3141592";
  private final Map<Common.Session, Common.Device> myDevices;
  private final MultiMap<Common.Device, Common.Process> myProcesses;
  private final Map<String, ByteString> myCache;
  private long myTimestampNs;
  private boolean myThrowErrorOnGetDevices;
  private boolean myAttachAgentCalled;
  private AgentStatusResponse.Status myAgentStatus;

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
      Common.Device device = Common.Device.newBuilder().setSerial("FakeDevice").setState(Common.Device.State.ONLINE).build();
      Common.Process process = Common.Process.newBuilder()
        .setPid(20)
        .setState(Common.Process.State.ALIVE)
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

  public void addProcess(Common.Session session, Common.Process process) {
    if (!myDevices.containsKey(session)) {
      throw new IllegalArgumentException("Invalid device serial: " + session);
    }
    myProcesses.putValue(myDevices.get(session), process);
  }

  public void removeProcess(Common.Session session, Common.Process process) {
    if (!myDevices.containsKey(session)) {
      throw new IllegalArgumentException("Invalid device serial: " + session);
    }
    myProcesses.remove(myDevices.get(session), process);
  }

  public void addDevice(Common.Device device) {
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myDevices.put(session, device);
  }

  public void updateDevice(Common.Session session, Common.Device oldDevice, Common.Device newDevice) {
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

  public void removeFile(String id) {
    if (myCache.containsKey(id)) {
      myCache.remove(id);
    }
  }

  public void setTimestampNs(long timestamp) {
    myTimestampNs = timestamp;
  }

  @Override
  public void getVersion(VersionRequest request, StreamObserver<VersionResponse> responseObserver) {
    responseObserver.onNext(VersionResponse.newBuilder().setVersion(VERSION).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getDevices(GetDevicesRequest request, StreamObserver<GetDevicesResponse> responseObserver) {
    if (myThrowErrorOnGetDevices) {
      responseObserver.onError(new RuntimeException("Server error"));
      return;
    }
    GetDevicesResponse.Builder response = GetDevicesResponse.newBuilder();
    for (Common.Device device : myDevices.values()) {
      response.addDevice(device);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getProcesses(GetProcessesRequest request, StreamObserver<GetProcessesResponse> responseObserver) {
    GetProcessesResponse.Builder response = GetProcessesResponse.newBuilder();
    Common.Device device = request.getDevice();
    if (device != null) {
      for (Common.Process process : myProcesses.get(device)) {
        response.addProcess(process);
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> responseObserver) {
    TimeResponse.Builder response = TimeResponse.newBuilder();
    response.setTimestampNs(myTimestampNs);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getBytes(BytesRequest request, StreamObserver<BytesResponse> responseObserver) {
    BytesResponse.Builder builder = BytesResponse.newBuilder();
    ByteString bytes = myCache.get(request.getId());
    if (bytes != null) {
      builder.setContents(bytes);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getAgentStatus(AgentStatusRequest request, StreamObserver<AgentStatusResponse> responseObserver) {
    AgentStatusResponse.Builder builder = AgentStatusResponse.newBuilder();
    if (myAgentStatus != null) {
      builder.setStatus(myAgentStatus);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void attachAgent(AgentAttachRequest request, StreamObserver<AgentAttachResponse> responseObserver) {
    myAttachAgentCalled = true;
    responseObserver.onNext(AgentAttachResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  public void setAgentStatus(@NotNull AgentStatusResponse.Status status) {
    myAgentStatus = status;
  }

  public void setThrowErrorOnGetDevices(boolean throwErrorOnGetDevices) {
    myThrowErrorOnGetDevices = throwErrorOnGetDevices;
  }

  public boolean getAgentAttachCalled() {
    return myAttachAgentCalled;
  }
}
