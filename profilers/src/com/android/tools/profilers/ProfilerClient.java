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

import com.android.tools.idea.transport.TransportClient;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.io.grpc.ManagedChannel;
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;

public class ProfilerClient {
  @NotNull private final ManagedChannel myChannel;
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myTransportClient;
  @NotNull private final EventServiceGrpc.EventServiceBlockingStub myEventClient;

  public ProfilerClient(@NotNull String name) {
    // Optimization - In-process direct-executor channel which allows us to communicate between the profiler and transport-database without
    // going through the thread pool. This gives us a speed boost per grpc call plus the full caller's stack in transport-database.
    this(InProcessChannelBuilder.forName(name).usePlaintext().directExecutor().build());
  }

  @VisibleForTesting
  public ProfilerClient(@NotNull ManagedChannel channel) {
    myChannel = channel;
    myTransportClient = TransportServiceGrpc.newBlockingStub(channel);
    myEventClient = EventServiceGrpc.newBlockingStub(channel);
  }

  @NotNull
  public TransportServiceGrpc.TransportServiceBlockingStub getTransportClient() {
    return myTransportClient;
  }

  public CompletableFuture<Transport.ExecuteResponse> executeAsync(Commands.Command command, Executor executor) {
    return TransportClient.executeAsync(myTransportClient, command, executor);
  }

  @NotNull
  public EventServiceGrpc.EventServiceBlockingStub getEventClient() {
    return myEventClient;
  }

  /**
   * Shuts down the managed channel. Should be called when this client is no longer used.
   */
  public void shutdownChannel() {
    if (!myChannel.isShutdown()) {
      myChannel.shutdown();
    }
  }
}
