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
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.TestGrpcChannel;
import io.grpc.stub.StreamObserver;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MemoryProfilerStageTest {

  @Rule
  public TestGrpcChannel<MemoryServiceMock> myGrpcChannel = new TestGrpcChannel<>("MEMORY_TEST_CHANNEL", new MemoryServiceMock());

  @Test
  public void testToggleLegacyCapture() throws Exception {
    StudioProfilers profilers = myGrpcChannel.getProfilers();
    MemoryServiceMock service = myGrpcChannel.getService();
    MemoryProfilerStage stage = new MemoryProfilerStage(profilers);
    profilers.setStage(stage);

    assertEquals(false, stage.isTrackingAllocations());
    service.setNextStatus(MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS);
    stage.trackAllocations(true);
    assertEquals(true, stage.isTrackingAllocations());

    service.setNextStatus(MemoryProfiler.TrackAllocationsResponse.Status.IN_PROGRESS);
    stage.trackAllocations(true);
    assertEquals(true, stage.isTrackingAllocations());

    service.setNextStatus(MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS);
    stage.trackAllocations(false);
    assertEquals(false, stage.isTrackingAllocations());

    service.setNextStatus(MemoryProfiler.TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
    stage.trackAllocations(true);
    assertEquals(false, stage.isTrackingAllocations());
  }

  private static class MemoryServiceMock extends MemoryServiceGrpc.MemoryServiceImplBase {
    private MemoryProfiler.TrackAllocationsResponse.Status myNextStatus;

    @Override
    public void trackAllocations(MemoryProfiler.TrackAllocationsRequest request,
                                 StreamObserver<MemoryProfiler.TrackAllocationsResponse> response) {
      response.onNext(MemoryProfiler.TrackAllocationsResponse.newBuilder().setStatus(myNextStatus).build());
      response.onCompleted();
    }

    public void setNextStatus(MemoryProfiler.TrackAllocationsResponse.Status status) {
      myNextStatus = status;
    }
  }
}
