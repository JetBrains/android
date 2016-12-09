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

import com.android.tools.datastore.poller.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.netty.NettyChannelBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RunnableFuture;

/**
 * Primary class that initializes the Datastore. This class currently manages connections to perfd and sets up the DataStore service.
 */
public class DataStoreService {
  private static final Logger LOG = Logger.getInstance(DataStoreService.class.getCanonicalName());
  private static final int MAX_MESSAGE_SIZE = 512 * 1024 * 1024 - 1;
  private ManagedChannel myChannel;
  private ServerBuilder myServerBuilder;
  private Server myServer;
  private List<ServicePassThrough> myServices = new ArrayList<>();
  private LegacyAllocationTracker myLegacyAllocationTracker;

  public DataStoreService(String name) {
    try {
      myServerBuilder = InProcessServerBuilder.forName(name);
      createPollers();
      myServer = myServerBuilder.build();
      myServer.start();
    }
    catch (IOException ex) {
      LOG.error(ex.getMessage());
    }
  }

  /**
   * Entry point for the datastore pollers and passthrough services are created,
   * and registered as the set of features the datastore supports.
   */
  public void createPollers() {
    registerService(new ProfilerService(this));
    registerService(new EventDataPoller());
    registerService(new CpuDataPoller());
    registerService(new MemoryDataPoller(this));
    registerService(new NetworkDataPoller());
  }

  /**
   * Register's the service with the DataStore and manages the list of pass through to initialize a connection to the appropriate device.
   *
   * @param service The service to register with the datastore. This service will be setup as a listener for studio to talk to.
   */
  private void registerService(ServicePassThrough service) {
    myServices.add(service);
    // Build server and start listening for RPC calls for the registered service
    myServerBuilder.addService(service.getService());
  }

  /**
   * This function connects all the services registered in the datastore to the device.
   */
  private void connectServices() {
    for (ServicePassThrough service : myServices) {
      // Tell service how to connect to device RPC to start polling.
      service.connectService(myChannel);
      RunnableFuture<Void> runner = service.getRunner();
      if (runner != null) {
        ApplicationManager.getApplication().executeOnPooledThread(runner);
      }
    }
  }

  /**
   * When a new device is connected this function tells the DataStore how to connect to that device and creates a channel for the device.
   *
   * @param devicePort forwarded port for the datastore to connect to perfd on.
   */
  public void connect(int devicePort) {
    disconnect();
    ClassLoader stashedContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(ManagedChannelBuilder.class.getClassLoader());
    myChannel = NettyChannelBuilder
      .forAddress("localhost", devicePort)
      .usePlaintext(true)
      .maxMessageSize(MAX_MESSAGE_SIZE)
      .build();
    Thread.currentThread().setContextClassLoader(stashedContextClassLoader);
    connectServices();
  }

  /**
   * Disconnect the datastore from the connected device.
   */
  public void disconnect() {
    // TODO: Shutdown service connections.
    if (myChannel != null) {
      myChannel.shutdown();
    }
    myChannel = null;
  }

  public void shutdown() {
    myServer.shutdownNow();
  }

  /**
   * Since older releases of Android and uninstrumented apps will not have JVMTI allocation tracking, we therefore need to support the older
   * JDWP allocation tracking functionality.
   */
  public void setLegacyAllocationTracker(@NotNull LegacyAllocationTracker legacyAllocationTracker) {
    myLegacyAllocationTracker = legacyAllocationTracker;
  }

  @Nullable
  public LegacyAllocationTracker getLegacyAllocationTracker() {
    return myLegacyAllocationTracker;
  }
}
