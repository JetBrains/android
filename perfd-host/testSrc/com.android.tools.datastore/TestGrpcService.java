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
package com.android.tools.datastore;

import com.android.tools.datastore.poller.EventDataPoller;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.rules.ExternalResource;

/**
 * JUnit rule for creating a test instance of {@link StudioProfilers} connected over a light,
 * in-process GRPC server that reads from a mock service which provides fake data. Besides creating
 * the {@link StudioProfilers} instance, it also starts up / shuts down the test server automatically.
 *
 * Within a test, use {@link #get()} to fetch a valid {@link StudioProfilers} instance.
 */
public final class TestGrpcService<S extends BindableService> extends ExternalResource {
  private static final String GRPC_NAME = "Test";
  private final S myService;
  private Server myServer;

  public TestGrpcService(S service) {
    myService = service;
  }

  @Override
  protected void before() throws Throwable {
    myServer = InProcessServerBuilder.forName(GRPC_NAME).addService(myService).build();
    myServer.start();
  }

  @Override
  protected void after() {
    myServer.shutdownNow();
  }

  public ManagedChannel getChannel() {
    return InProcessChannelBuilder.forName(GRPC_NAME)
      .usePlaintext(true)
      .build();
  }
}
