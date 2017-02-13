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

import com.android.tools.datastore.service.*;
import com.android.tools.profiler.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class DataStoreServiceTest extends DataStorePollerTest {

  private static final String SERVICE_PATH = "/tmp/DataStoreServiceTest.sql";
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
}