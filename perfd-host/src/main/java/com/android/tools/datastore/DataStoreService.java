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

import com.android.annotations.VisibleForTesting;
import com.android.tools.datastore.service.*;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Primary class that initializes the Datastore. This class currently manages connections to perfd and sets up the DataStore service.
 */
public class DataStoreService {
  private static final Logger LOG = Logger.getInstance(DataStoreService.class.getCanonicalName());
  private DataStoreDatabase myDatabase;
  private ManagedChannel myChannel;
  private ServerBuilder myServerBuilder;
  private Server myServer;
  private List<ServicePassThrough> myServices = new ArrayList<>();
  private Consumer<Runnable> myFetchExecutor;
  private ProfilerService myProfilerService;

  /**
   * @param fetchExecutor A callback which is given a {@link Runnable} for each datastore service.
   *                      The runnable, when run, begins polling the target service. You probably
   *                      want to run it on a background thread.
   */
  public DataStoreService(String serviceName, String dbPath, Consumer<Runnable> fetchExecutor) {
    try {
      myFetchExecutor = fetchExecutor;
      myDatabase = new DataStoreDatabase(dbPath);
      myServerBuilder = InProcessServerBuilder.forName(serviceName).directExecutor();
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
    myProfilerService = new ProfilerService(this, myFetchExecutor);
    registerService(myProfilerService);
    registerService(new EventService(myFetchExecutor));
    registerService(new CpuService(myFetchExecutor));
    registerService(new MemoryService(myFetchExecutor));
    registerService(new NetworkService(myFetchExecutor));
  }

  /**
   * Register's the service with the DataStore and manages the list of pass through to initialize a connection to the appropriate device.
   *
   * @param service The service to register with the datastore. This service will be setup as a listener for studio to talk to.
   */
  private void registerService(ServicePassThrough service) {
    myServices.add(service);
    myDatabase.registerTable(service.getDatastoreTable());
    // Build server and start listening for RPC calls for the registered service
    myServerBuilder.addService(service.bindService());
  }

  /**
   * This function connects all the services registered in the datastore to the device.
   */
  private void connectServices() {
    for (ServicePassThrough service : myServices) {
      // Tell service how to connect to device RPC to start polling.
      service.connectService(myChannel);
    }
  }

  /**
   * Note - Previously connect/disconnect was done through a ProfilerService rpc call in which this receives only a port number. Passing
   * in a ManagedChannel directly allows the caller to optimize the channel best suited for the server (In-process vs Netty channels).
   */
  public void connect(@NotNull ManagedChannel channel) {
    myChannel = channel;
    myProfilerService.startMonitoring(channel);
    connectServices();
  }

  /**
   * Disconnect from the specified channel.
   * TODO: currently we just support the same channel that was passed into connect. mutli-device workflow needs to handle this.
   */
  public void disconnect(@Nullable ManagedChannel channel) {
    assert channel == myChannel;
    if (myChannel != null) {
      myChannel.shutdownNow();
    }
    myProfilerService.stopMonitoring(channel);
    myChannel = null;
  }

  public void shutdown() {
    myServer.shutdownNow();
    myDatabase.disconnect();
  }

  @VisibleForTesting
  List<ServicePassThrough> getRegisteredServices() {
    return myServices;
  }
}
