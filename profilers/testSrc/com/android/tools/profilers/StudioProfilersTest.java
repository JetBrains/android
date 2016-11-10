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
import io.grpc.Channel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

final public class StudioProfilersTest {
  public static final String CHANNEL_NAME = "StudioProfilerTestChannel";
  private ProfilerServiceGrpc.ProfilerServiceBlockingStub client;

  @Before
  public void setUp() throws Exception {
    Channel channel = InProcessChannelBuilder.forName(CHANNEL_NAME).usePlaintext(true).build();
    client = ProfilerServiceGrpc.newBlockingStub(channel);
  }

  @Test
  public void test() throws Exception {
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
    Profiler.VersionResponse response = client.getVersion(Profiler.VersionRequest.getDefaultInstance());
    assertEquals(version, response.getVersion());
    server.shutdownNow();
  }
}
