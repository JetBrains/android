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

import com.android.tools.datastore.DataStoreService.BackingNamespace;
import com.android.tools.datastore.service.*;
import com.android.tools.profiler.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.sql.Connection;
import java.util.*;
import java.util.function.Consumer;

import static com.android.tools.datastore.DataStoreDatabase.Characteristic.DURABLE;
import static com.android.tools.datastore.DataStoreDatabase.Characteristic.PERFORMANT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DataStoreServiceTest extends DataStorePollerTest {

  private static final String SERVICE_PATH = "/tmp/";
  private static final String SERVICE_NAME = "DataStoreServiceTest";
  private static final String SERVER_NAME = "TestServer";
  private static final Profiler.VersionResponse EXPECTED_VERSION = Profiler.VersionResponse.newBuilder().setVersion("TEST").build();
  private DataStoreService myDataStore;
  private Server myService;

  @Rule
  public ExpectedException myExpectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    myDataStore = new DataStoreService(SERVICE_NAME, SERVICE_PATH, getPollTicker()::run);
    myService = InProcessServerBuilder.forName(SERVER_NAME)
      .addService(new FakeProfilerService().bindService())
      .addService(new EventServiceStub().bindService())
      .addService(new CpuServiceStub().bindService())
      .addService(new MemoryServiceStub().bindService())
      .addService(new NetworkServiceStub().bindService())
      .build();
    myService.start();
  }

  @After
  public void tearDown() {
    myService.shutdownNow();
    myDataStore.shutdown();
  }

  @Test
  public void testServiceSetupWithExpectedName() {
    ManagedChannel channel = InProcessChannelBuilder.forName(SERVICE_PATH).build();
    ProfilerServiceGrpc.newBlockingStub(channel);
  }

  @Test
  public void testProperServicesSetup() {
    Set<Class> expectedServices = new HashSet<>();
    expectedServices.add(ProfilerService.class);
    expectedServices.add(EventService.class);
    expectedServices.add(CpuService.class);
    expectedServices.add(NetworkService.class);
    expectedServices.add(MemoryService.class);

    List<ServicePassThrough> services = myDataStore.getRegisteredServices();
    for (ServicePassThrough service : services) {
      assertEquals(expectedServices.contains(service.getClass()), true);
      expectedServices.remove(service.getClass());
    }
    assertEquals(expectedServices.size(), 0);
  }

  @Test
  public void testConnectServices() {
    ManagedChannel channel = InProcessChannelBuilder.forName(SERVER_NAME).build();
    myDataStore.connect(channel);
    ProfilerServiceGrpc.ProfilerServiceBlockingStub stub =
      myDataStore.getProfilerClient(DataStorePollerTest.SESSION);
    Profiler.VersionResponse response = stub.getVersion(Profiler.VersionRequest.newBuilder().setSession(DataStorePollerTest.SESSION).build());
    assertEquals(response, EXPECTED_VERSION);
  }

  @Test
  public void testDisconnectServices() {
    ManagedChannel channel = InProcessChannelBuilder.forName(SERVER_NAME).build();
    myDataStore.connect(channel);
    ProfilerServiceGrpc.ProfilerServiceBlockingStub stub =
      ProfilerServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(SERVICE_NAME).usePlaintext(true).build());
    Profiler.VersionResponse response = stub.getVersion(Profiler.VersionRequest.newBuilder().setSession(DataStorePollerTest.SESSION).build());
    assertEquals(response, EXPECTED_VERSION);
    myDataStore.disconnect(DataStorePollerTest.SESSION);
    myExpectedException.expect(StatusRuntimeException.class);
    stub.getVersion(Profiler.VersionRequest.getDefaultInstance());
  }

  @Test
  public void testRegisterDb() {
    FakeDataStoreService dataStoreService = new FakeDataStoreService("DataStoreServiceTestFake", SERVICE_PATH, getPollTicker()::run);
    dataStoreService.assertCorrectness();
  }

  private static class MemoryServiceStub extends MemoryServiceGrpc.MemoryServiceImplBase {
  }

  private static class EventServiceStub extends EventServiceGrpc.EventServiceImplBase {
  }

  private static class CpuServiceStub extends CpuServiceGrpc.CpuServiceImplBase {
  }

  private static class NetworkServiceStub extends NetworkServiceGrpc.NetworkServiceImplBase {
  }

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
    @Override
    public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> responseObserver) {
      responseObserver.onNext(EXPECTED_VERSION);
      responseObserver.onCompleted();
    }

    @Override
    public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
      String serial = DataStorePollerTest.SESSION.getDeviceSerial();
      String bootid = DataStorePollerTest.SESSION.getBootId();
      Profiler.Device device = Profiler.Device.newBuilder()
        .setSerial(serial)
        .setBootId(bootid)
        .build();
      responseObserver.onNext(Profiler.GetDevicesResponse.newBuilder().addDevice(device).build());
      responseObserver.onCompleted();
    }

    @Override
    public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> responseObserver) {
      responseObserver.onNext(Profiler.GetProcessesResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }

  private static class FakeDataStoreService extends DataStoreService {
    private String myDatastoreDirectory;
    private FakeServicePassthrough myPassthrough;
    private List<String> myCreatedDbPaths;
    private List<DataStoreDatabase.Characteristic> myCreatedCharacteristics;

    public FakeDataStoreService(@NotNull String serviceName,
                                @NotNull String datastoreDirectory,
                                Consumer<Runnable> fetchExecutor) {
      super(serviceName, datastoreDirectory, fetchExecutor);
      myDatastoreDirectory = datastoreDirectory;
    }

    @Override
    public void createPollers() {
      myPassthrough = new FakeServicePassthrough();
      registerService(myPassthrough);
    }

    @NotNull
    @Override
    DataStoreDatabase createDatabase(@NotNull String dbPath, @NotNull DataStoreDatabase.Characteristic characteristic) {
      if (myCreatedDbPaths == null) {
        // This method is being called from the parent class's constructor, so we need to lazily create it.
        // Also, calling an overridden method in the parent constructor is super bad form. But we're lucky we can get away with it here.
        myCreatedDbPaths = new ArrayList<>();
      }
      if (myCreatedCharacteristics == null) {
        myCreatedCharacteristics = new ArrayList<>();
      }
      myCreatedDbPaths.add(dbPath);
      myCreatedCharacteristics.add(characteristic);
      return super.createDatabase(dbPath, characteristic);
    }

    public void assertCorrectness() {
      assert myPassthrough != null;
      myPassthrough.assertCorrectness();
      for (int i = 0; i < myPassthrough.getBackingNamespaces().size(); i++) {
        assertEquals(myDatastoreDirectory + myPassthrough.getBackingNamespaces().get(i).myNamespace, myCreatedDbPaths.get(i));
        assertEquals(myPassthrough.getBackingNamespaces().get(i).myCharacteristic, myCreatedCharacteristics.get(i));
      }
    }
  }

  private static class FakeServicePassthrough implements ServicePassThrough {
    @NotNull private final List<BackingNamespace> myNamespaces = Arrays.asList(
      new BackingNamespace("durable", DURABLE), new BackingNamespace("inmemory", PERFORMANT));

    @NotNull private final Map<BackingNamespace, Connection> myReceivedBackingStores = new HashMap<>();

    @NotNull
    @Override
    public ServerServiceDefinition bindService() {
      return ServerServiceDefinition.builder("FakeServicePassthrough").build();
    }

    @NotNull
    @Override
    public List<BackingNamespace> getBackingNamespaces() {
      return myNamespaces;
    }

    @Override
    public void setBackingStore(@NotNull BackingNamespace namespace, @NotNull Connection connection) {
      assert myNamespaces.contains(namespace) && !myReceivedBackingStores.containsKey(namespace) && !myReceivedBackingStores
        .containsValue(connection);
      myReceivedBackingStores.put(namespace, connection);
    }

    public void assertCorrectness() {
      assertEquals(2, myReceivedBackingStores.size());
      assertTrue(myReceivedBackingStores.containsKey(myNamespaces.get(0)));
      assertTrue(myReceivedBackingStores.containsKey(myNamespaces.get(1)));
    }
  }
}
