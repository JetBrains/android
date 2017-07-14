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
import com.android.tools.analytics.UsageTracker;
import com.android.tools.datastore.service.*;
import com.android.tools.profiler.proto.*;
import com.google.wireless.android.sdk.stats.AndroidProfilerDbStats;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.*;
import io.grpc.inprocess.InProcessServerBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.android.tools.datastore.DataStoreDatabase.Characteristic.DURABLE;

/**
 * Primary class that initializes the Datastore. This class currently manages connections to perfd and sets up the DataStore service.
 */
public class DataStoreService {

  /**
   * DB report timings are set to occur relatively infrequently, as they include a fair amount of
   * data (~100 bytes). Ideally, we would just send a single reporting event, when the user stopped
   * profiling, but in that case, we'd possibly lose some data when a user's application crashed,
   * and we'd almost never count users who leave their IDE open forever. So, instead, we adopt a
   * slow but steady sampling strategy.
   */
  private static final long REPORT_INITIAL_DELAY = TimeUnit.MINUTES.toMillis(15);
  private static final long REPORT_PERIOD = TimeUnit.HOURS.toMillis(1);

  public static class BackingNamespace {
    public static final BackingNamespace DEFAULT_SHARED_NAMESPACE = new BackingNamespace("default.sql", DURABLE);

    @NotNull public final String myNamespace;
    @NotNull public final DataStoreDatabase.Characteristic myCharacteristic;

    public BackingNamespace(@NotNull String namespace, @NotNull DataStoreDatabase.Characteristic characteristic) {
      myNamespace = namespace;
      myCharacteristic = characteristic;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(new Object[]{myNamespace, myCharacteristic});
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof BackingNamespace)) {
        return false;
      }

      BackingNamespace other = (BackingNamespace)obj;
      return myNamespace.equals(other.myNamespace) && myCharacteristic == other.myCharacteristic;
    }
  }

  private static final Logger LOG = Logger.getInstance(DataStoreService.class.getCanonicalName());
  private final String myDatastoreDirectory;
  private final Map<BackingNamespace, DataStoreDatabase> myDatabases = new HashMap<>();
  private final HashMap<Common.Session, Long> mySessionIdLookup = new HashMap<>();
  private final ServerBuilder myServerBuilder;
  private final Server myServer;
  private final List<ServicePassThrough> myServices = new ArrayList<>();
  private final Consumer<Runnable> myFetchExecutor;
  private ProfilerService myProfilerService;
  private final ServerInterceptor myInterceptor;
  private final Map<Common.Session, DataStoreClient> myConnectedClients = new HashMap<>();

  private final Timer myReportTimer;

  /**
   * @param fetchExecutor A callback which is given a {@link Runnable} for each datastore service.
   *                      The runnable, when run, begins polling the target service. You probably
   *                      want to run it on a background thread.
   */
  public DataStoreService(@NotNull String serviceName, @NotNull String datastoreDirectory, Consumer<Runnable> fetchExecutor) {
    this(serviceName, datastoreDirectory, fetchExecutor, null);
  }

  public DataStoreService(@NotNull String serviceName,
                          @NotNull String datastoreDirectory,
                          @NotNull Consumer<Runnable> fetchExecutor,
                          ServerInterceptor interceptor) {
    myFetchExecutor = fetchExecutor;
    myInterceptor = interceptor;
    myDatastoreDirectory = datastoreDirectory;
    myServerBuilder = InProcessServerBuilder.forName(serviceName).directExecutor();
    createPollers();
    myServer = myServerBuilder.build();
    try {
      myServer.start();
    }
    catch (IOException ex) {
      LOG.error(ex.getMessage());
    }

    myReportTimer = new Timer("DataStoreReportTimer");
    myReportTimer.schedule(new ReportTimerTask(), REPORT_INITIAL_DELAY, REPORT_PERIOD);
  }

  /**
   * Entry point for the datastore pollers and passthrough services are created,
   * and registered as the set of features the datastore supports.
   */
  public void createPollers() {
    myProfilerService = new ProfilerService(this, myFetchExecutor, mySessionIdLookup);
    registerService(myProfilerService);
    registerService(new EventService(this, myFetchExecutor, mySessionIdLookup));
    registerService(new CpuService(this, myFetchExecutor, mySessionIdLookup));
    registerService(new MemoryService(this, myFetchExecutor, mySessionIdLookup));
    registerService(new NetworkService(this, myFetchExecutor, mySessionIdLookup));
  }

  @VisibleForTesting
  @NotNull
  DataStoreDatabase createDatabase(@NotNull String dbPath, @NotNull DataStoreDatabase.Characteristic characteristic) {
    return new DataStoreDatabase(dbPath, characteristic);
  }

  /**
   * Register's the service with the DataStore and manages the list of pass through to initialize a connection to the appropriate device.
   *
   * @param service The service to register with the datastore. This service will be setup as a listener for studio to talk to.
   */
  @VisibleForTesting
  void registerService(@NotNull ServicePassThrough service) {
    myServices.add(service);
    List<BackingNamespace> namespaces = service.getBackingNamespaces();
    namespaces.forEach(namespace -> {
      assert !namespace.myNamespace.isEmpty();
      DataStoreDatabase db = myDatabases.computeIfAbsent(namespace, backingNamespace -> createDatabase(
        myDatastoreDirectory + backingNamespace.myNamespace, backingNamespace.myCharacteristic));
      service.setBackingStore(namespace, db.getConnection());
    });

    // Build server and start listening for RPC calls for the registered service
    if (myInterceptor != null) {
      myServerBuilder.addService(ServerInterceptors.intercept(service.bindService(), myInterceptor));
    }
    else {
      myServerBuilder.addService(service.bindService());
    }
  }

  /**
   * When a new device is connected this function tells the DataStore how to connect to that device and creates a channel for the device.
   *
   * @param channel communication channel for the datastore to connect to perfd on.
   */
  public void connect(@NotNull ManagedChannel channel) {
    myProfilerService.startMonitoring(channel);
  }

  /**
   * Disconnect from the specified channel.
   */
  public void disconnect(@NotNull Common.Session session) {
    if (myConnectedClients.containsKey(session)) {
      DataStoreClient client = myConnectedClients.remove(session);
      client.shutdownNow();
      myProfilerService.stopMonitoring(client.getChannel());
    }
  }

  public void shutdown() {
    myReportTimer.cancel();
    myServer.shutdownNow();
    for (DataStoreClient client : myConnectedClients.values()) {
      client.shutdownNow();
    }
    myConnectedClients.clear();
    myDatabases.forEach((name, db) -> db.disconnect());
  }

  @VisibleForTesting
  List<ServicePassThrough> getRegisteredServices() {
    return myServices;
  }

  public void setConnectedClients(Common.Session session, Channel channel) {
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
    @NotNull private final Channel myChannel;
    @NotNull private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerClient;
    @NotNull private final MemoryServiceGrpc.MemoryServiceBlockingStub myMemoryClient;
    @NotNull private final CpuServiceGrpc.CpuServiceBlockingStub myCpuClient;
    @NotNull private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkClient;
    @NotNull private final EventServiceGrpc.EventServiceBlockingStub myEventClient;

    public DataStoreClient(@NotNull Channel channel) {
      myChannel = channel;
      myProfilerClient = ProfilerServiceGrpc.newBlockingStub(channel);
      myMemoryClient = MemoryServiceGrpc.newBlockingStub(channel);
      myCpuClient = CpuServiceGrpc.newBlockingStub(channel);
      myNetworkClient = NetworkServiceGrpc.newBlockingStub(channel);
      myEventClient = EventServiceGrpc.newBlockingStub(channel);
    }

    @NotNull
    public Channel getChannel() {
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
      // The check is needed because the test replace the channel with a PassthroughChannel instead of a managed channel.
      if (myChannel instanceof ManagedChannel) {
        ((ManagedChannel)myChannel).shutdownNow();
      }
    }
  }

  private final class ReportTimerTask extends TimerTask {
    private long myStartTime = System.nanoTime();

    @Override
    public void run() {
      AndroidProfilerDbStats.Builder dbStats = AndroidProfilerDbStats.newBuilder();
      // Cast to int. Unlikely we'll ever have more than 2 billion seconds (e.g. ~60 years) here...
      dbStats.setAgeSec((int)TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - myStartTime));
      collectReport(dbStats);

      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ANDROID_PROFILER_DB_STATS)
        .setAndroidProfilerDbStats(dbStats);

      UsageTracker.getInstance().log(event);
    }

    private void collectReport(AndroidProfilerDbStats.Builder dbStats) {
      try {
        File dbFile = new File(myDatastoreDirectory, BackingNamespace.DEFAULT_SHARED_NAMESPACE.myNamespace);
        dbStats.setTotalDiskMb((int)(dbFile.length() / 1024 / 1024)); // Bytes -> MB

        for (DataStoreDatabase db : myDatabases.values()) {
          try (
            Statement tableStatement = db.getConnection().createStatement();
            ResultSet tableResults = tableStatement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (tableResults.next()) {
              String tableName = tableResults.getString(1);
              try (
                Statement sizeStatement = db.getConnection().createStatement();
                ResultSet sizeResult = sizeStatement.executeQuery(String.format("SELECT COUNT(*) FROM %s", tableName))) {
                int tableSize = sizeResult.getInt(1);
                dbStats.addTablesBuilder().setName(tableName).setNumRecords(tableSize).build();
              }
            }
          }
        }
      }
      catch (SQLException ignored) {
      }
    }
  }
}
