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
import com.android.tools.profiler.proto.*;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Primary class that initializes the Datastore. This class currently manages connections to perfd and sets up the DataStore service.
 */
public class DataStoreService {
  private static final Logger LOG = Logger.getInstance(DataStoreService.class.getCanonicalName());
  private DataStoreDatabase myDatabase;
  private ServerBuilder myServerBuilder;
  private Server myServer;
  private List<ServicePassThrough> myServices = new ArrayList<>();
  private Consumer<Runnable> myFetchExecutor;
  private ProfilerService myProfilerService;
  private Map<Common.Session, DataStoreClient> myConnectedClients = new HashMap<>();

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
    registerService(new EventService(this, myFetchExecutor));
    registerService(new CpuService(this, myFetchExecutor));
    registerService(new MemoryService(this, myFetchExecutor));
    registerService(new NetworkService(this, myFetchExecutor));
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
   * When a new device is connected this function tells the DataStore how to connect to that device and creates a channel for the device.
   *
   * @param devicePort forwarded port for the datastore to connect to perfd on.
   */
  public void connect(@NotNull ManagedChannel channel) {
    myProfilerService.startMonitoring(channel);
  }

  /**
   * Disconnect from the specified channel.
   */
  public void disconnect(@NotNull Common.Session session) {
    if(myConnectedClients.containsKey(session)) {
      ManagedChannel channel = myConnectedClients.remove(session).getChannel();
      channel.shutdownNow();
      myProfilerService.stopMonitoring(channel);
    }
  }

  public void shutdown() {
    myServer.shutdownNow();
    for(DataStoreClient client : myConnectedClients.values()) {
      client.shutdownNow();
    }
    myConnectedClients.clear();
    myDatabase.disconnect();
  }

  @VisibleForTesting
  List<ServicePassThrough> getRegisteredServices() {
    return myServices;
  }

  public void setConnectedClients(Common.Session session, ManagedChannel channel) {
    if (!myConnectedClients.containsKey(session)) {
      myConnectedClients.put(session, new DataStoreClient(channel));
    }
  }

  public CpuServiceGrpc.CpuServiceBlockingStub getCpuClient(Common.Session session) {
    return myConnectedClients.containsKey(session) ? myConnectedClients.get(session).getCpuClient() : null;
  }

  public EventServiceGrpc.EventServiceBlockingStub getEventClient(Common.Session session) {
    return myConnectedClients.containsKey(session) ? myConnectedClients.get(session).getEventClient() : null;
  }

  public NetworkServiceGrpc.NetworkServiceBlockingStub getNetworkClient(Common.Session session) {
    return myConnectedClients.containsKey(session) ? myConnectedClients.get(session).getNetworkClient() : null;
  }

  public MemoryServiceGrpc.MemoryServiceBlockingStub getMemoryClient(Common.Session session) {
    return myConnectedClients.containsKey(session) ? myConnectedClients.get(session).getMemoryClient() : null;
  }

  public ProfilerServiceGrpc.ProfilerServiceBlockingStub getProfilerClient(Common.Session session) {
    return myConnectedClients.containsKey(session) ? myConnectedClients.get(session).getProfilerClient() : null;
  }


  /**
   * This class is used to manage the stub to each service per device.
   */
  private static class DataStoreClient {

    @NotNull private final ManagedChannel myChannel;
    @NotNull private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerClient;
    @NotNull private final MemoryServiceGrpc.MemoryServiceBlockingStub myMemoryClient;
    @NotNull private final CpuServiceGrpc.CpuServiceBlockingStub myCpuClient;
    @NotNull private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkClient;
    @NotNull private final EventServiceGrpc.EventServiceBlockingStub myEventClient;

    public DataStoreClient(@NotNull ManagedChannel channel) {
      myChannel = channel;
      myProfilerClient = ProfilerServiceGrpc.newBlockingStub(channel);
      myMemoryClient = MemoryServiceGrpc.newBlockingStub(channel);
      myCpuClient = CpuServiceGrpc.newBlockingStub(channel);
      myNetworkClient = NetworkServiceGrpc.newBlockingStub(channel);
      myEventClient = EventServiceGrpc.newBlockingStub(channel);
    }
    public ManagedChannel getChannel() {
      return myChannel;
    }

    @NotNull
    public ProfilerServiceGrpc.ProfilerServiceBlockingStub getProfilerClient() {
      return myProfilerClient;
    }

    @NotNull
    public MemoryServiceGrpc.MemoryServiceBlockingStub getMemoryClient() {
      return myMemoryClient;
    }

    @NotNull
    public CpuServiceGrpc.CpuServiceBlockingStub getCpuClient() {
      return myCpuClient;
    }

    @NotNull
    public NetworkServiceGrpc.NetworkServiceBlockingStub getNetworkClient() {
      return myNetworkClient;
    }

    @NotNull
    public EventServiceGrpc.EventServiceBlockingStub getEventClient() {
      return myEventClient;
    }

    public void shutdownNow() {
      myChannel.shutdownNow();
    }
  }
}
