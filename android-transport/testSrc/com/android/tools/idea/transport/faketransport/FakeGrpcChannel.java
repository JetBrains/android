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
import java.util.UUID;
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

  /**
   * @param namePrefix A readable name which you can use to identify the server created by this
   *                   class if something goes wrong. Often, this will be the name of your test
   *                   class. To ensure the name will be unique across all tests, it will
   *                   additionally be suffixed with a unique ID. Use {@link #getName()} to get
   *                   the full, unique name.
   */
  public FakeGrpcChannel(String namePrefix, BindableService... services) {
    // It can be problematic if GRPC channels with the same name are started at the same time
    // across tests boundaries. This can happen, for example, if multiple tests are run in parallel
    // with a copy/pasted name. By appending a UUID, we guarantee that the in-memory GRPC channel
    // being spun up for this test is guaranteed not to be shared with other tests by accident.
    myName = namePrefix + "_" + UUID.randomUUID();
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

    try {
      myServer.awaitTermination();
    }
    catch (InterruptedException ignore) {}
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
