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

import com.android.tools.profiler.proto.Commands.Command;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Wrapper that represents a client connected to a channel via the input |channelName|.
 */
public class TransportClient {

  private static final long SHUTDOWN_TIMEOUT_MS = 1000;

  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myTransportStub;
  @NotNull private final ManagedChannel myChannel;

  public TransportClient(String channelName) {
    // Optimization - In-process direct-executor channel which allows us to communicate between the client and transport-database without
    // going through the thread pool. This gives us a speed boost per grpc call plus the full caller's stack in transport-database.
    myChannel = InProcessChannelBuilder.forName(channelName).usePlaintext().directExecutor().build();
    myTransportStub = TransportServiceGrpc.newBlockingStub(myChannel);
  }

  @NotNull
  public TransportServiceGrpc.TransportServiceBlockingStub getTransportStub() {
    return myTransportStub;
  }

  public void shutdown() throws InterruptedException {
    myChannel.shutdown();
    myChannel.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  public CompletableFuture<Transport.ExecuteResponse> executeAsync(Command command, Executor executor) {
    return executeAsync(myTransportStub, command, executor);
  }

  public static CompletableFuture<Transport.ExecuteResponse> executeAsync(
    TransportServiceGrpc.TransportServiceBlockingStub stub, Command command, Executor executor) {
    return CompletableFuture.supplyAsync(
      () -> stub.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build()), executor
    );
  }
}
