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
package com.android.tools.profilers.memory;

import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse.Status;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakeMemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {
  private Status myExplicitAllocationsStatus = null;
  private AllocationsInfo myExplicitAllocationsInfo = null;
  private TriggerHeapDumpResponse.Status myExplicitHeapDumpStatus = null;
  private HeapDumpInfo myExplicitHeapDumpInfo = null;
  private long myCurrentTime = 0;
  private MemoryData myMemoryData = null;
  private ListHeapDumpInfosResponse.Builder myHeapDumpInfoBuilder = ListHeapDumpInfosResponse.newBuilder();

  private int myAppId;

  @Override
  public void startMonitoringApp(MemoryProfiler.MemoryStartRequest request,
                                 StreamObserver<MemoryProfiler.MemoryStartResponse> responseObserver) {

    myAppId = request.getAppId();
    responseObserver.onNext(MemoryProfiler.MemoryStartResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(MemoryProfiler.MemoryStopRequest request,
                                StreamObserver<MemoryProfiler.MemoryStopResponse> responseObserver) {
    myAppId = request.getAppId();
    responseObserver.onNext(MemoryProfiler.MemoryStopResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  public int getAppId() {
    return myAppId;
  }

  @Override
  public void trackAllocations(MemoryProfiler.TrackAllocationsRequest request,
                               StreamObserver<MemoryProfiler.TrackAllocationsResponse> response) {
    TrackAllocationsResponse.Builder builder = TrackAllocationsResponse.newBuilder();
    if (myExplicitAllocationsStatus != null) {
      builder.setStatus(myExplicitAllocationsStatus);
    }
    if (myExplicitAllocationsInfo != null) {
      builder.setInfo(myExplicitAllocationsInfo);
    }
    builder.setTimestamp(myCurrentTime);
    response.onNext(builder.build());
    response.onCompleted();
  }

  @Override
  public void getData(MemoryProfiler.MemoryRequest request, StreamObserver<MemoryProfiler.MemoryData> response) {
    response.onNext(myMemoryData != null ? myMemoryData
                                         : MemoryProfiler.MemoryData.newBuilder().setEndTimestamp(request.getStartTime() + 1).build());
    response.onCompleted();
  }

  @Override
  public void listAllocationContexts(AllocationContextsRequest request,
                                     StreamObserver<AllocationContextsResponse> responseObserver) {
    // TODO add test data.
    responseObserver.onNext(AllocationContextsResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void listHeapDumpInfos(MemoryProfiler.ListDumpInfosRequest request,
                                StreamObserver<MemoryProfiler.ListHeapDumpInfosResponse> response) {
    response.onNext(myHeapDumpInfoBuilder.build());
    response.onCompleted();
  }

  @Override
  public void getAllocationsInfoStatus(GetAllocationsInfoStatusRequest request,
                                       StreamObserver<GetAllocationsInfoStatusResponse> responseObserver) {
    responseObserver.onNext(
      GetAllocationsInfoStatusResponse.newBuilder().setInfoId(myExplicitAllocationsInfo.getInfoId())
        .setStatus(myExplicitAllocationsInfo.getStatus()).build());
    responseObserver.onCompleted();
  }

  @Override
  public void triggerHeapDump(TriggerHeapDumpRequest request,
                              StreamObserver<TriggerHeapDumpResponse> responseObserver) {
    TriggerHeapDumpResponse.Builder builder = TriggerHeapDumpResponse.newBuilder();
    if (myExplicitHeapDumpStatus != null) {
      builder.setStatus(myExplicitHeapDumpStatus);
    }
    if (myExplicitHeapDumpInfo != null) {
      builder.setInfo(myExplicitHeapDumpInfo);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @NotNull
  public FakeMemoryService advanceTime(long newTime) {
    myCurrentTime = newTime;
    return this;
  }

  @NotNull
  public FakeMemoryService setExplicitAllocationsStatus(@Nullable Status status) {
    myExplicitAllocationsStatus = status;
    return this;
  }

  public FakeMemoryService setExplicitAllocationsInfo(int infoId, AllocationsInfo.Status infoStatus,
                                                      long startTime, long endTime, boolean legacy) {
    myExplicitAllocationsInfo =
      AllocationsInfo.newBuilder().setInfoId(infoId).setStatus(infoStatus).setStartTime(startTime).setEndTime(endTime)
        .setLegacyTracking(legacy).build();
    return this;
  }

  public FakeMemoryService setExplicitHeapDumpStatus(@Nullable TriggerHeapDumpResponse.Status status) {
    myExplicitHeapDumpStatus = status;
    return this;
  }

  public FakeMemoryService setExplicitHeapDumpInfo(int dumpId, long startTime, long endTime) {
    myExplicitHeapDumpInfo = HeapDumpInfo.newBuilder().setDumpId(dumpId).setStartTime(startTime).setEndTime(endTime).build();
    return this;
  }

  public FakeMemoryService setMemoryData(@Nullable MemoryData data) {
    myMemoryData = data;
    return this;
  }

  public FakeMemoryService addExplicitHeapDumpInfo(@NotNull HeapDumpInfo info) {
    myHeapDumpInfoBuilder.addInfos(info);
    return this;
  }
}
