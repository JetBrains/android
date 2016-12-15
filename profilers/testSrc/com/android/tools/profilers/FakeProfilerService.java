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
import io.grpc.stub.StreamObserver;

public final class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
  public static final String VERSION = "3141592";
  private final Profiler.Device myDevice = Profiler.Device.newBuilder().setSerial("FakeDevice").build();
  private final Profiler.Process myProcess = Profiler.Process.newBuilder().setPid(20).setName("FakeProcess").build();

  public Profiler.Device getDevice() {
    return myDevice;
  }

  public Profiler.Process getProcess() {
    return myProcess;
  }

  @Override
  public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> responseObserver) {
    responseObserver.onNext(Profiler.VersionResponse.newBuilder().setVersion(VERSION).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
    Profiler.GetDevicesResponse.Builder response = Profiler.GetDevicesResponse.newBuilder();
    response.addDevice(myDevice);

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> responseObserver) {
    Profiler.GetProcessesResponse.Builder response = Profiler.GetProcessesResponse.newBuilder();
    response.addProcess(myProcess);

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getTimes(Profiler.TimesRequest request, StreamObserver<Profiler.TimesResponse> responseObserver) {
    Profiler.TimesResponse.Builder response = Profiler.TimesResponse.newBuilder();

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }
}
