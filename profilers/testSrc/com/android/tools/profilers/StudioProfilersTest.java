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
import com.android.tools.profilers.cpu.CpuProfilerStage;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

final public class StudioProfilersTest {
  public static final String CHANNEL_NAME = "StudioProfilerTestChannel";
  private ProfilerClient myClient;

  @Before
  public void setUp() throws Exception {
    myClient = new ProfilerClient(CHANNEL_NAME);
  }

  @Test
  public void testVersion() throws Exception {
    final String version = "3141592";
    ProfilerServiceGrpc.ProfilerServiceImplBase
      service = new ProfilerServiceGrpc.ProfilerServiceImplBase() {
        @Override
        public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> responseObserver) {
          responseObserver.onNext(Profiler.VersionResponse.newBuilder().setVersion(version).build());
          responseObserver.onCompleted();
        }
      };

    Server server = InProcessServerBuilder.forName(CHANNEL_NAME).addService(service).build();
    server.start();
    Profiler.VersionResponse response = myClient.getProfilerClient().getVersion(Profiler.VersionRequest.getDefaultInstance());
    assertEquals(version, response.getVersion());
    server.shutdownNow();
  }

  @Test
  public void testClearedOnMonitorStage() throws Exception {
    StudioProfilers profilers = new StudioProfilers(myClient);

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());

    profilers.setStage(new CpuProfilerStage(profilers));
    profilers.getTimeline().getSelectionRange().set(10, 10);
    profilers.setMonitoringStage();

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());
  }
}
