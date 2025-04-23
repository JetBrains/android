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

import static com.android.tools.datastore.DataStoreDatabase.Characteristic.DURABLE;
import static com.android.tools.datastore.DataStoreDatabase.Characteristic.PERFORMANT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.test.testutils.TestUtils;
import com.android.tools.datastore.DataStoreService.BackingNamespace;
import com.android.tools.datastore.database.DeviceProcessTable;
import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.datastore.service.CpuService;
import com.android.tools.datastore.service.EventService;
import com.android.tools.datastore.service.MemoryService;
import com.android.tools.datastore.service.ProfilerService;
import com.android.tools.datastore.service.TransportService;
import com.android.tools.idea.io.grpc.ManagedChannel;
import com.android.tools.idea.io.grpc.Server;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder;
import com.android.tools.idea.io.grpc.inprocess.InProcessServerBuilder;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import com.android.tools.profiler.proto.Common.AgentData;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.android.tools.profiler.proto.Transport.AgentStatusRequest;
import com.android.tools.profiler.proto.Transport.GetDevicesRequest;
import com.android.tools.profiler.proto.Transport.GetDevicesResponse;
import com.android.tools.profiler.proto.Transport.GetProcessesRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesResponse;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profiler.proto.Transport.VersionRequest;
import com.android.tools.profiler.proto.Transport.VersionResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DataStoreServiceTest extends DataStorePollerTest {
  private static final String SERVICE_NAME = "DataStoreServiceTest";
  private static final VersionResponse EXPECTED_VERSION = VersionResponse.newBuilder().setVersion("TEST").build();
  private DataStoreService myDataStore;
  private String myServicePath;
  private Server myService;

  @Rule
  public ExpectedException myExpectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    myServicePath = TestUtils.createTempDirDeletedOnExit().toString();
    myDataStore = new DataStoreService(SERVICE_NAME, myServicePath, getPollTicker()::run, new FakeLogService());
    myService = InProcessServerBuilder
      .forName(myServicePath)
      .addService(new FakeTransportService().bindService())
      .addService(new ProfilerServiceStub().bindService())
      .addService(new EventServiceStub().bindService())
      .addService(new CpuServiceStub().bindService())
      .addService(new MemoryServiceStub().bindService())
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
    ManagedChannel channel = InProcessChannelBuilder.forName(myServicePath).build();
    ProfilerServiceGrpc.newBlockingStub(channel);
  }

  @Test
  public void testProperServicesSetup() {
    Set<Class> expectedServices = new HashSet<>();
    expectedServices.add(TransportService.class);
    expectedServices.add(ProfilerService.class);
    expectedServices.add(EventService.class);
    expectedServices.add(CpuService.class);
    expectedServices.add(MemoryService.class);

    List<ServicePassThrough> services = myDataStore.getRegisteredServices();
    for (ServicePassThrough service : services) {
      assertTrue(expectedServices.contains(service.getClass()));
      expectedServices.remove(service.getClass());
    }
    assertEquals(0, expectedServices.size());
  }

  @Test
  public void testConnectServices() {
    ManagedChannel channel = InProcessChannelBuilder.forName(myServicePath).build();
    myDataStore.connect(STREAM, channel);
    TransportServiceGrpc.TransportServiceBlockingStub stub = myDataStore.getTransportClient(DEVICE.getDeviceId());
    VersionResponse response = stub.getVersion(VersionRequest.newBuilder().setStreamId(DEVICE.getDeviceId()).build());
    assertEquals(EXPECTED_VERSION, response);
  }

  @Test
  public void testDisconnectServices() {
    ManagedChannel channel = InProcessChannelBuilder.forName(myServicePath).build();
    myDataStore.connect(STREAM, channel);
    TransportServiceGrpc.TransportServiceBlockingStub stub =
      TransportServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(SERVICE_NAME).usePlaintext().build());
    VersionResponse response = stub.getVersion(VersionRequest.newBuilder().setStreamId(DEVICE.getDeviceId()).build());
    assertEquals(EXPECTED_VERSION, response);
    myDataStore.disconnect(DEVICE.getDeviceId());
    myExpectedException.expect(StatusRuntimeException.class);
    stub.getVersion(VersionRequest.getDefaultInstance());
  }

  @Test
  public void testRegisterDb() {
    FakeDataStoreService dataStoreService = new FakeDataStoreService("DataStoreServiceTestFake", myServicePath, getPollTicker()::run);
    dataStoreService.assertCorrectness();
    dataStoreService.shutdown();
  }

  @Test
  public void testSQLFailureCallsbackToExceptionHandler() {
    // Teardown datastore created in startup to unregister callbacks.
    myDataStore.shutdown();
    FakeDataStoreService dataStoreService =
      new FakeDataStoreService("testSQLFailureCallsbackToExceptionHandler", myServicePath, getPollTicker()::run);

    // Use an array making this object mutable by the lambda.
    final Throwable[] expectedException = new Throwable[1];
    dataStoreService.setNoPiiExceptionHandler((t) -> expectedException[0] = t);
    TransportServiceGrpc.TransportServiceBlockingStub stub = TransportServiceGrpc
      .newBlockingStub(InProcessChannelBuilder.forName("testSQLFailureCallsbackToExceptionHandler").usePlaintext().build());

    // Test that a normal RPC call does not trigger an exception
    stub.getAgentStatus(AgentStatusRequest.getDefaultInstance());
    assertThat(expectedException[0]).isNull();

    // Close the connection then test that we get the expected SQLException
    dataStoreService.getPassThrough().dropTransportTable();
    stub.getAgentStatus(AgentStatusRequest.getDefaultInstance());
    assertThat(expectedException[0]).isInstanceOf(SQLException.class);

    // Because we throw an error connection closes in the middle of the test, trying to call shutdown on the service will log an exception.
    // The exception is thrown when the connections attempts to commit the database but the connection is closed,
    // then we close the connection.
    myExpectedException.expect(AssertionError.class);
    dataStoreService.shutdown();
  }

  private static class MemoryServiceStub extends MemoryServiceGrpc.MemoryServiceImplBase {
  }

  private static class EventServiceStub extends EventServiceGrpc.EventServiceImplBase {
  }

  private static class CpuServiceStub extends CpuServiceGrpc.CpuServiceImplBase {
  }

  private static class ProfilerServiceStub extends ProfilerServiceGrpc.ProfilerServiceImplBase {
  }

  private static class FakeTransportService extends TransportServiceGrpc.TransportServiceImplBase {
    @Override
    public void getVersion(VersionRequest request, StreamObserver<VersionResponse> responseObserver) {
      responseObserver.onNext(EXPECTED_VERSION);
      responseObserver.onCompleted();
    }

    @Override
    public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> responseObserver) {
      responseObserver.onNext(TimeResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getDevices(GetDevicesRequest request, StreamObserver<GetDevicesResponse> responseObserver) {
      responseObserver.onNext(GetDevicesResponse.newBuilder().addDevice(DEVICE).build());
      responseObserver.onCompleted();
    }

    @Override
    public void getProcesses(GetProcessesRequest request, StreamObserver<GetProcessesResponse> responseObserver) {
      responseObserver.onNext(GetProcessesResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }

  private static class FakeDataStoreService extends DataStoreService {
    private final String myDatastoreDirectory;
    private FakeServicePassThrough myPassthrough;
    private List<String> myCreatedDbPaths;
    private List<DataStoreDatabase.Characteristic> myCreatedCharacteristics;

    public FakeDataStoreService(@NotNull String serviceName,
                                @NotNull String datastoreDirectory,
                                Consumer<Runnable> fetchExecutor) {
      super(serviceName, datastoreDirectory, fetchExecutor, new FakeLogService());
      myDatastoreDirectory = datastoreDirectory;
    }

    @Override
    public void createPollers() {
      myPassthrough = new FakeServicePassThrough();
      registerService(myPassthrough);
    }

    @NotNull
    @Override
    public DataStoreDatabase createDatabase(@NotNull String dbPath,
                                            @NotNull DataStoreDatabase.Characteristic characteristic,
                                            @NotNull Consumer<Throwable> noPiiExceptionHandler) {
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
      return super.createDatabase(dbPath, characteristic, noPiiExceptionHandler);
    }

    public void assertCorrectness() {
      assert myPassthrough != null;
      myPassthrough.assertCorrectness();
      for (int i = 0; i < myPassthrough.getBackingNamespaces().size(); i++) {
        assertEquals(myDatastoreDirectory + myPassthrough.getBackingNamespaces().get(i).myNamespace, myCreatedDbPaths.get(i));
        assertEquals(myPassthrough.getBackingNamespaces().get(i).myCharacteristic, myCreatedCharacteristics.get(i));
      }
    }

    public FakeServicePassThrough getPassThrough() {
      return myPassthrough;
    }
  }

  private static class FakeServicePassThrough extends TransportServiceGrpc.TransportServiceImplBase implements ServicePassThrough {
    @NotNull private final List<BackingNamespace> myNamespaces = Arrays.asList(
      new BackingNamespace("durable", DURABLE), new BackingNamespace("inmemory", PERFORMANT));

    @NotNull private final Map<BackingNamespace, Connection> myReceivedBackingStores = new HashMap<>();

    @NotNull private final UnifiedEventsTable myTable = new UnifiedEventsTable();
    @NotNull private final DeviceProcessTable myLegacyTable = new DeviceProcessTable();

    private Connection myConnection;

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
      myTable.initialize(connection);
      myLegacyTable.initialize(connection);
      myConnection = connection;
    }

    @Override
    public void getAgentStatus(AgentStatusRequest request, StreamObserver<AgentData> responseObserver) {
      responseObserver.onNext(myLegacyTable.getAgentStatus(request));
      responseObserver.onCompleted();
    }

    public void dropTransportTable() {
      try (Statement stmt = myConnection.createStatement()) {
        stmt.execute("DROP TABLE ProcessesTable");
      }
      catch (SQLException ex) {
        // do nothing
      }
    }

    public void assertCorrectness() {
      assertEquals(2, myReceivedBackingStores.size());
      assertTrue(myReceivedBackingStores.containsKey(myNamespaces.get(0)));
      assertTrue(myReceivedBackingStores.containsKey(myNamespaces.get(1)));
    }
  }
}
