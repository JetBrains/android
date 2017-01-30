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

import com.android.ddmlib.IDevice;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import org.jetbrains.annotations.NotNull;

/**
 * A proxy CpuService on host that intercepts grpc requests from perfd-host to device perfd.
 * This enables us to support legacy workflows based on device's API levels.
 */
public class CpuServiceProxy extends CpuServiceGrpc.CpuServiceImplBase {
  private CpuServiceGrpc.CpuServiceBlockingStub myServiceStub;

  public CpuServiceProxy(@NotNull IDevice device, @NotNull ManagedChannel channel) {
    myServiceStub = CpuServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public ServerServiceDefinition bindService() {
    return ServerServiceDefinition.builder(CpuServiceGrpc.getServiceDescriptor())
      .addMethod(CpuServiceGrpc.METHOD_GET_DATA,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getData(request));
                   observer.onCompleted();
                 }))
      .addMethod(CpuServiceGrpc.METHOD_GET_THREADS,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getThreads(request));
                   observer.onCompleted();
                 }))
      .addMethod(CpuServiceGrpc.METHOD_GET_TRACE_INFO,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getTraceInfo(request));
                   observer.onCompleted();
                 }))
      .addMethod(CpuServiceGrpc.METHOD_GET_TRACE,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getTrace(request));
                   observer.onCompleted();
                 }))
      .addMethod(CpuServiceGrpc.METHOD_START_MONITORING_APP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.startMonitoringApp(request));
                   observer.onCompleted();
                 }))
      .addMethod(CpuServiceGrpc.METHOD_STOP_MONITORING_APP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.stopMonitoringApp(request));
                   observer.onCompleted();
                 }))
      .addMethod(CpuServiceGrpc.METHOD_START_PROFILING_APP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.startProfilingApp(request));
                   observer.onCompleted();
                 }))
      .addMethod(CpuServiceGrpc.METHOD_STOP_PROFILING_APP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.stopProfilingApp(request));
                   observer.onCompleted();
                 }))
      .addMethod(CpuServiceGrpc.METHOD_CHECK_APP_PROFILING_STATE,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.checkAppProfilingState(request));
                   observer.onCompleted();
                 }))
      .build();
  }
}
