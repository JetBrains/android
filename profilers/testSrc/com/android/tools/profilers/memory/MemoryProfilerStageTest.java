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
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.internal.ServerImpl;
import io.grpc.stub.StreamObserver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MemoryProfilerStageTest {

  private static final String CHANNEL_NAME = "MEMORY_TEST_CHANNEL";

  @Test
  public void testToggleLegacyCapture() throws Exception {
    ProfilerClient client = new ProfilerClient(CHANNEL_NAME);
    MemoryServiceMock service = new MemoryServiceMock();
    ServerImpl server = InProcessServerBuilder.forName(CHANNEL_NAME).addService(service).build();
    server.start();


    StudioProfilers profilers = new StudioProfilers(client);
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

    server.shutdownNow();
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
