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
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

class FakeMemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {
  private static final long INDETERMINATE_END_TIMESTAMP = -1;

  private MemoryProfiler.TrackAllocationsResponse.Status myExplicitStatus = null;
  private ArrayList<MemoryProfiler.MemoryData.AllocationsInfo> myInfos = new ArrayList<>();
  private long myStartTime = Long.MAX_VALUE;
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
    MemoryProfiler.TrackAllocationsResponse.Builder builder = MemoryProfiler.TrackAllocationsResponse.newBuilder();
    if (myExplicitStatus != null) {
      builder.setStatus(myExplicitStatus).setTimestamp(myCurrentTime);
    }
    else if (request.getEnabled()) {
      if (myStartTime == Long.MAX_VALUE) {
        builder.setStatus(MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS).setTimestamp(myCurrentTime);
        myStartTime = myCurrentTime;
        myInfos.add(
          MemoryProfiler.MemoryData.AllocationsInfo.newBuilder().setStartTime(myStartTime).setEndTime(INDETERMINATE_END_TIMESTAMP).build());
      }
      else {
        builder.setStatus(MemoryProfiler.TrackAllocationsResponse.Status.IN_PROGRESS).setTimestamp(myCurrentTime);
      }
    }
    else {
      if (myStartTime == Long.MAX_VALUE) {
        builder.setStatus(MemoryProfiler.TrackAllocationsResponse.Status.NOT_ENABLED).setTimestamp(myCurrentTime);
      }
      else {
        builder.setStatus(MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS).setTimestamp(myCurrentTime);
        assert myInfos.size() > 0;
        MemoryProfiler.MemoryData.AllocationsInfo lastInfo = myInfos.get(myInfos.size() - 1);
        assert lastInfo.getEndTime() == INDETERMINATE_END_TIMESTAMP;
        myInfos.set(myInfos.size() - 1, lastInfo.toBuilder().setEndTime(myCurrentTime).build());
      }
    }
    response.onNext(builder.build());
    response.onCompleted();
  }

  @Override
  public void getData(MemoryProfiler.MemoryRequest request, StreamObserver<MemoryProfiler.MemoryData> response) {
    response.onNext(MemoryProfiler.MemoryData.newBuilder().setEndTimestamp(request.getStartTime() + 1).build());
    response.onCompleted();
  }

  @Override
  public void listHeapDumpInfos(MemoryProfiler.ListDumpInfosRequest request,
                                StreamObserver<MemoryProfiler.ListHeapDumpInfosResponse> response) {
    response.onNext(MemoryProfiler.ListHeapDumpInfosResponse.newBuilder().build());
    response.onCompleted();
  }

  @NotNull
  public FakeMemoryService advanceTime(long newTime) {
    myCurrentTime = newTime;
    return this;
  }

  @NotNull
  public FakeMemoryService setExplicitStatus(@Nullable MemoryProfiler.TrackAllocationsResponse.Status status) {
    myExplicitStatus = status;
    return this;
  }
}
