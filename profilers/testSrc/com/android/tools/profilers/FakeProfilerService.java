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

import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.intellij.util.containers.MultiMap;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;

public final class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
  public static final String VERSION = "3141592";
  private final Map<String, Profiler.Device> myDevices;
  private final MultiMap<Profiler.Device, Profiler.Process> myProcesses;
  private long myTimestampNs;
  private boolean myThrowErrorOnGetDevices;

  public FakeProfilerService() {
    this(true);
  }

  /**
   * Creates a fake profiler service. If connected is true there will be a device with a process already present.
   */
  public FakeProfilerService(boolean connected) {
    myDevices = new HashMap<>();
    myProcesses = MultiMap.create();
    if (connected) {
      Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
      Profiler.Process process = Profiler.Process.newBuilder().setPid(20).setName("FakeProcess").build();
      addDevice(device);
      addProcess("FakeDevice", process);
    }
  }

  public void addProcess(String serial, Profiler.Process process) {
    if (!myDevices.containsKey(serial)) {
      throw new IllegalArgumentException("Invalid device serial: " + serial);
    }
    myProcesses.putValue(myDevices.get(serial), process);
  }

  public void addDevice(Profiler.Device device) {
    myDevices.put(device.getSerial(), device);
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
    String serial = request.getSerial();
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
  public void getTimes(Profiler.TimesRequest request, StreamObserver<Profiler.TimesResponse> responseObserver) {
    Profiler.TimesResponse.Builder response = Profiler.TimesResponse.newBuilder();
    response.setTimestampNs(myTimestampNs);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public void setThrowErrorOnGetDevices(boolean throwErrorOnGetDevices) {
    myThrowErrorOnGetDevices = throwErrorOnGetDevices;
  }
}
