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
package com.android.tools.idea.transport.faketransport;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.ExternalResource;

/**
 * JUnit rule for creating a light, in-process GRPC client / server connection that is initialized
 * with fake services which provides it test data. This class handles starting up / shutting down
 * this connection automatically before / after each test.
 */
public class FakeGrpcChannel extends ExternalResource {
  private final String myName;
  private final BindableService[] myServices;
  private Server myServer;
  private ManagedChannel myChannel;

  public FakeGrpcChannel(String name, BindableService... services) {
    myName = name;
    myServices = services;
  }

  @Override
  protected void before() throws Throwable {
    InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(myName);
    for (BindableService service : myServices) {
      serverBuilder.addService(service);
    }
    myServer = serverBuilder.build();
    myServer.start();

    myChannel = InProcessChannelBuilder.forName(myName).usePlaintext().directExecutor().build();
  }

  @Override
  protected void after() {
    myServer.shutdownNow();
    myChannel.shutdownNow();
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public ManagedChannel getChannel() {
    if (myChannel == null) {
      throw new IllegalStateException(
        "FakeGrpcChannel rule has not been initialized yet. getChannel() can't be called during static initialization.");
    }
    return myChannel;
  }
}
