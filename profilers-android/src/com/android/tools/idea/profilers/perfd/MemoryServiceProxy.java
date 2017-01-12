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
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import org.jetbrains.annotations.NotNull;

/**
 * A proxy MemoryService on host that intercepts grpc requests from perfd-host to device perfd.
 * This enables us to support legacy workflows based on device's API levels.
 *
 * TODO move legacy allocation logic in here.
 */
public class MemoryServiceProxy extends MemoryServiceGrpc.MemoryServiceImplBase {

  private MemoryServiceGrpc.MemoryServiceBlockingStub myServiceStub;

  public MemoryServiceProxy(@NotNull IDevice device, @NotNull ManagedChannel channel) {
    myServiceStub = MemoryServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public ServerServiceDefinition bindService() {
    return ServerServiceDefinition.builder(MemoryServiceGrpc.getServiceDescriptor())
      .addMethod(MemoryServiceGrpc.METHOD_START_MONITORING_APP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.startMonitoringApp(request));
                   observer.onCompleted();
                 }))
      .addMethod(MemoryServiceGrpc.METHOD_STOP_MONITORING_APP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.stopMonitoringApp(request));
                   observer.onCompleted();
                 }))
      .addMethod(MemoryServiceGrpc.METHOD_GET_DATA,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getData(request));
                   observer.onCompleted();
                 }))
      .addMethod(MemoryServiceGrpc.METHOD_TRIGGER_HEAP_DUMP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.triggerHeapDump(request));
                   observer.onCompleted();
                 }))
      .addMethod(MemoryServiceGrpc.METHOD_GET_HEAP_DUMP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getHeapDump(request));
                   observer.onCompleted();
                 }))
      .addMethod(MemoryServiceGrpc.METHOD_LIST_HEAP_DUMP_INFOS,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.listHeapDumpInfos(request));
                   observer.onCompleted();
                 }))
      .addMethod(MemoryServiceGrpc.METHOD_TRACK_ALLOCATIONS,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.trackAllocations(request));
                   observer.onCompleted();
                 }))
      .addMethod(MemoryServiceGrpc.METHOD_LIST_ALLOCATION_CONTEXTS,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.listAllocationContexts(request));
                   observer.onCompleted();
                 }))
      .addMethod(MemoryServiceGrpc.METHOD_GET_ALLOCATIONS_INFO_STATUS,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getAllocationsInfoStatus(request));
                   observer.onCompleted();
                 }))
      .addMethod(MemoryServiceGrpc.METHOD_GET_ALLOCATION_DUMP,
                 ServerCalls.asyncUnaryCall((request, observer) -> {
                   observer.onNext(myServiceStub.getAllocationDump(request));
                   observer.onCompleted();
                 }))
      .build();
  }
}