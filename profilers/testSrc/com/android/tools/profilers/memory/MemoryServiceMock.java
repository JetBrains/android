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

import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse.Status;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

class MemoryServiceMock extends MemoryServiceGrpc.MemoryServiceImplBase {
  private static final long INDETERMINATE_END_TIMESTAMP = -1;

  private Status myExplicitStatus = null;
  private ArrayList<AllocationsInfo> myInfos = new ArrayList<>();
  private long myStartTime = Long.MAX_VALUE;
  private long myCurrentTime = 0;

  @Override
  public void trackAllocations(TrackAllocationsRequest request,
                               StreamObserver<TrackAllocationsResponse> response) {
    TrackAllocationsResponse.Builder builder = TrackAllocationsResponse.newBuilder();
    if (myExplicitStatus != null) {
      builder.setStatus(myExplicitStatus).setTimestamp(myCurrentTime);
    }
    else if (request.getEnabled()) {
      if (myStartTime == Long.MAX_VALUE) {
        builder.setStatus(Status.SUCCESS).setTimestamp(myCurrentTime);
        myStartTime = myCurrentTime;
        myInfos.add(AllocationsInfo.newBuilder().setStartTime(myStartTime).setEndTime(INDETERMINATE_END_TIMESTAMP).build());
      }
      else {
        builder.setStatus(Status.IN_PROGRESS).setTimestamp(myCurrentTime);
      }
    }
    else {
      if (myStartTime == Long.MAX_VALUE) {
        builder.setStatus(Status.NOT_ENABLED).setTimestamp(myCurrentTime);
      }
      else {
        builder.setStatus(Status.SUCCESS).setTimestamp(myCurrentTime);
        assert myInfos.size() > 0;
        AllocationsInfo lastInfo = myInfos.get(myInfos.size() - 1);
        assert lastInfo.getEndTime() == INDETERMINATE_END_TIMESTAMP;
        myInfos.set(myInfos.size() - 1, lastInfo.toBuilder().setEndTime(myCurrentTime).build());
      }
    }
    response.onNext(builder.build());
    response.onCompleted();
  }

  @Override
  public void getData(MemoryRequest request, StreamObserver<MemoryData> response) {
    response.onNext(MemoryData.newBuilder().setEndTimestamp(request.getStartTime() + 1).build());
    response.onCompleted();
  }

  @Override
  public void listHeapDumpInfos(ListDumpInfosRequest request,
                                StreamObserver<ListHeapDumpInfosResponse> response) {
    response.onNext(ListHeapDumpInfosResponse.newBuilder().build());
    response.onCompleted();
  }

  @NotNull
  public MemoryServiceMock advanceTime(long newTime) {
    myCurrentTime = newTime;
    return this;
  }

  @NotNull
  public MemoryServiceMock setExplicitStatus(@Nullable Status status) {
    myExplicitStatus = status;
    return this;
  }
}
