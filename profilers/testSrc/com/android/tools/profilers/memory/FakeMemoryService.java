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

class FakeMemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {
  private Status myExplicitStatus = null;
  private AllocationsInfo myExplicitInfo = null;
  private long myCurrentTime = 0;

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
    if (myExplicitStatus != null) {
      builder.setStatus(myExplicitStatus);
    }
    if (myExplicitInfo != null) {
      builder.setInfo(myExplicitInfo);
    }
    builder.setTimestamp(myCurrentTime);
    response.onNext(builder.build());
    response.onCompleted();
  }

  @Override
  public void getData(MemoryProfiler.MemoryRequest request, StreamObserver<MemoryProfiler.MemoryData> response) {
    response.onNext(MemoryProfiler.MemoryData.newBuilder().setEndTimestamp(request.getStartTime() + 1).build());
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
    response.onNext(MemoryProfiler.ListHeapDumpInfosResponse.newBuilder().build());
    response.onCompleted();
  }

  @Override
  public void getAllocationsInfoStatus(GetAllocationsInfoStatusRequest request,
                                       StreamObserver<GetAllocationsInfoStatusResponse> responseObserver) {
    responseObserver.onNext(
      GetAllocationsInfoStatusResponse.newBuilder().setInfoId(myExplicitInfo.getInfoId()).setStatus(myExplicitInfo.getStatus()).build());
    responseObserver.onCompleted();
  }

  @NotNull
  public FakeMemoryService advanceTime(long newTime) {
    myCurrentTime = newTime;
    return this;
  }

  @NotNull
  public FakeMemoryService setExplicitStatus(@Nullable Status status) {
    myExplicitStatus = status;
    return this;
  }

  public FakeMemoryService setExplicitAllocationsInfo(int infoId, AllocationsInfo.Status infoStatus,
                                                      long startTime, long endTime, boolean legacy) {
    myExplicitInfo = AllocationsInfo.newBuilder().setInfoId(infoId).setStatus(infoStatus).setStartTime(startTime).setEndTime(endTime)
      .setLegacyTracking(legacy).build();
    return this;
  }
}
