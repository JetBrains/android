/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport;

import com.android.tools.profiler.proto.TransportServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Wrapper that represents a client connected to a channel via the input |channelName|.
 */
public class TransportClient {

  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myTransportStub;

  public TransportClient(String channelName) {
    // Optimization - In-process direct-executor channel which allows us to communicate between the client and transport-database without
    // going through the thread pool. This gives us a speed boost per grpc call plus the full caller's stack in transport-database.
    ManagedChannel channel = InProcessChannelBuilder.forName(channelName).usePlaintext().directExecutor().build();
    myTransportStub = TransportServiceGrpc.newBlockingStub(channel);
  }

  @NotNull
  public TransportServiceGrpc.TransportServiceBlockingStub getTransportStub() {
    return myTransportStub;
  }
}
