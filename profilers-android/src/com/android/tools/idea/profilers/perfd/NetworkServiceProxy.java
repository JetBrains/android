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
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import org.jetbrains.annotations.NotNull;

public class NetworkServiceProxy extends NetworkServiceGrpc.NetworkServiceImplBase {
  private NetworkServiceGrpc.NetworkServiceBlockingStub myServiceStub;

  public NetworkServiceProxy(@NotNull IDevice device, @NotNull ManagedChannel channel) {
    myServiceStub = NetworkServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public ServerServiceDefinition bindService() {
    return ServerServiceDefinition.builder(NetworkServiceGrpc.getServiceDescriptor())
      .addMethod(NetworkServiceGrpc.METHOD_GET_DATA,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getData(request));
                   observer.onCompleted();
                 }))
      .addMethod(NetworkServiceGrpc.METHOD_START_MONITORING_APP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.startMonitoringApp(request));
                   observer.onCompleted();
                 }))
      .addMethod(NetworkServiceGrpc.METHOD_STOP_MONITORING_APP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.stopMonitoringApp(request));
                   observer.onCompleted();
                 }))
      .addMethod(NetworkServiceGrpc.METHOD_GET_HTTP_RANGE,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getHttpRange(request));
                   observer.onCompleted();
                 }))
      .addMethod(NetworkServiceGrpc.METHOD_GET_HTTP_DETAILS,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getHttpDetails(request));
                   observer.onCompleted();
                 }))
      .build();
  }
}
