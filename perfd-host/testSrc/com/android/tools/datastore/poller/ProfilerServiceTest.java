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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.datastore.service.ProfilerService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProfilerServiceTest extends DataStorePollerTest {

  private static final String DEVICE_SERIAL = "SomeSerialId";
  private static final String BOOT_ID = "SOME BOOT ID";
  private static final Common.Session SESSION = Common.Session.newBuilder()
    .setBootId(BOOT_ID)
    .setDeviceSerial(DEVICE_SERIAL)
    .build();

  private static final Profiler.Process INITIAL_PROCESS = Profiler.Process.newBuilder()
    .setPid(1234)
    .setName("INITIAL")
    .build();
  private static final Profiler.Process FINAL_PROCESS = Profiler.Process.newBuilder()
    .setPid(4321)
    .setName("FINAL")
    .build();

  private DataStoreService myDataStore = mock(DataStoreService.class);

  private ProfilerService myProfilerService = new ProfilerService(myDataStore, getPollTicker()::run, new HashMap<>());

  private static final String BYTES_ID_1 = "0123456789";
  private static final String BYTES_ID_2 = "9876543210";
  private static final String BAD_ID = "0000000000";
  private static final ByteString BYTES_1 = ByteString.copyFromUtf8("FILE_1");
  private static final ByteString BYTES_2 = ByteString.copyFromUtf8("FILE_2");

  private static final Map<String, ByteString> PAYLOAD_CACHE = new ImmutableMap.Builder<String, ByteString>().
    put(BYTES_ID_1, BYTES_1).
    put(BYTES_ID_2, BYTES_2).
    build();

  private FakeProfilerService myFakeService = new FakeProfilerService();
  private TestName myTestName = new TestName();
  private TestGrpcService<FakeProfilerService> myService =
    new TestGrpcService<>(ProfilerServiceTest.class, myTestName, myProfilerService, myFakeService);

  @Rule
  public RuleChain myChain = RuleChain.outerRule(myTestName).around(myService);

  @Before
  public void setUp() throws Exception {
    myProfilerService.startMonitoring(myService.getChannel());
    when(myDataStore.getProfilerClient(any())).thenReturn(ProfilerServiceGrpc.newBlockingStub(myService.getChannel()));
  }

  @After
  public void tearDown() throws Exception {
    myDataStore.shutdown();
  }

  @Test
  public void testGetTimes() throws Exception {
    StreamObserver<Profiler.TimeResponse> observer = mock(StreamObserver.class);
    myProfilerService.getCurrentTime(Profiler.TimeRequest.getDefaultInstance(), observer);
    validateResponse(observer, Profiler.TimeResponse.getDefaultInstance());
  }

  @Test
  public void testGetVersion() throws Exception {
    StreamObserver<Profiler.VersionResponse> observer = mock(StreamObserver.class);
    myProfilerService.getVersion(Profiler.VersionRequest.getDefaultInstance(), observer);
    validateResponse(observer, Profiler.VersionResponse.getDefaultInstance());
  }

  @Test
  public void testGetDevices() throws Exception {
    StreamObserver<Profiler.GetDevicesResponse> observer = mock(StreamObserver.class);
    Profiler.GetDevicesResponse expected = Profiler.GetDevicesResponse.newBuilder()
      .addDevice(Profiler.Device.newBuilder()
                   .setSerial(DEVICE_SERIAL)
                   .setBootId(BOOT_ID)
                   .build())
      .build();
    myProfilerService.getDevices(Profiler.GetDevicesRequest.getDefaultInstance(), observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testDeviceDisconnect() throws Exception {
    myService.shutdownServer();
    getPollTicker().run();
    StreamObserver<Profiler.GetProcessesResponse> observer = mock(StreamObserver.class);
    Profiler.GetProcessesResponse expected = Profiler.GetProcessesResponse.newBuilder()
      .addProcess(INITIAL_PROCESS.toBuilder().setState(Profiler.Process.State.DEAD))
      .build();
    Profiler.GetProcessesRequest request = Profiler.GetProcessesRequest.newBuilder()
      .setSession(SESSION)
      .build();
    myProfilerService.getProcesses(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetProcesses() throws Exception {
    StreamObserver<Profiler.GetProcessesResponse> observer = mock(StreamObserver.class);
    Profiler.GetProcessesRequest request = Profiler.GetProcessesRequest.newBuilder()
      .setSession(SESSION)
      .build();
    Profiler.GetProcessesResponse expected = Profiler.GetProcessesResponse.newBuilder()
      .addProcess(INITIAL_PROCESS)
      .build();
    myProfilerService.getProcesses(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetDeadProcesses() throws Exception {
    StreamObserver<Profiler.GetProcessesResponse> observer = mock(StreamObserver.class);
    Profiler.GetProcessesRequest request = Profiler.GetProcessesRequest.newBuilder()
      .setSession(SESSION)
      .build();
    Profiler.GetProcessesResponse expected = Profiler.GetProcessesResponse.newBuilder()
      .addProcess(INITIAL_PROCESS)
      .build();
    myProfilerService.getProcesses(request, observer);
    validateResponse(observer, expected);

    // Change the process list for the second tick.
    // We expect to get back both processes, the initial process
    // Should be set to a dead state.
    myFakeService.setProcessToReturn(FINAL_PROCESS);
    getPollTicker().run();
    observer = mock(StreamObserver.class);
    expected = Profiler.GetProcessesResponse.newBuilder()
      .addProcess(INITIAL_PROCESS.toBuilder().setState(Profiler.Process.State.DEAD))
      .addProcess(FINAL_PROCESS)
      .build();
    myProfilerService.getProcesses(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetFile() throws Exception {
    StreamObserver<Profiler.BytesResponse> observer1 = mock(StreamObserver.class);
    Profiler.BytesRequest request1 = Profiler.BytesRequest.newBuilder().setId(BYTES_ID_1).build();
    Profiler.BytesResponse response1 = Profiler.BytesResponse.newBuilder().setContents(BYTES_1).build();
    myProfilerService.getBytes(request1, observer1);
    validateResponse(observer1, response1);

    StreamObserver<Profiler.BytesResponse> observer2 = mock(StreamObserver.class);
    Profiler.BytesRequest request2 = Profiler.BytesRequest.newBuilder().setId(BYTES_ID_2).build();
    Profiler.BytesResponse response2 = Profiler.BytesResponse.newBuilder().setContents(BYTES_2).build();
    myProfilerService.getBytes(request2, observer2);
    validateResponse(observer2, response2);

    StreamObserver<Profiler.BytesResponse> observerNoMatch = mock(StreamObserver.class);
    Profiler.BytesRequest requestBad =
      Profiler.BytesRequest.newBuilder().setId(BAD_ID).build();
    Profiler.BytesResponse responseNoMatch = Profiler.BytesResponse.getDefaultInstance();
    myProfilerService.getBytes(requestBad, observerNoMatch);
    validateResponse(observerNoMatch, responseNoMatch);
  }

  @Test
  public void testGetFileCached() throws Exception {
    // Pull data into the database cache.
    StreamObserver<Profiler.BytesResponse> observer1 = mock(StreamObserver.class);
    Profiler.BytesRequest request1 = Profiler.BytesRequest.newBuilder().setId(BYTES_ID_1).build();
    Profiler.BytesResponse response1 = Profiler.BytesResponse.newBuilder().setContents(BYTES_1).build();
    myProfilerService.getBytes(request1, observer1);
    validateResponse(observer1, response1);

    StreamObserver<Profiler.BytesResponse> observer2 = mock(StreamObserver.class);
    Profiler.BytesRequest request2 = Profiler.BytesRequest.newBuilder().setId(BYTES_ID_2).build();
    Profiler.BytesResponse response2 = Profiler.BytesResponse.newBuilder().setContents(BYTES_2).build();
    myProfilerService.getBytes(request2, observer2);
    validateResponse(observer2, response2);

    // Disconnect the client
    when(myDataStore.getProfilerClient(any())).thenReturn(null);

    // Validate that we get back the expected bytes
    observer1 = mock(StreamObserver.class);
    myProfilerService.getBytes(request1, observer1);
    validateResponse(observer1, response1);

    observer2 = mock(StreamObserver.class);
    myProfilerService.getBytes(request2, observer2);
    validateResponse(observer2, response2);

    // Validate that bad instances still return default.
    StreamObserver<Profiler.BytesResponse> observerNoMatch = mock(StreamObserver.class);
    Profiler.BytesRequest requestBad =
      Profiler.BytesRequest.newBuilder().setId(BAD_ID).build();
    Profiler.BytesResponse responseNoMatch = Profiler.BytesResponse.getDefaultInstance();
    myProfilerService.getBytes(requestBad, observerNoMatch);
    validateResponse(observerNoMatch, responseNoMatch);
  }

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {

    Profiler.Process myProcessToReturn = INITIAL_PROCESS;

    public void setProcessToReturn(Profiler.Process process) {
      myProcessToReturn = process;
    }

    @Override
    public void getCurrentTime(Profiler.TimeRequest request, StreamObserver<Profiler.TimeResponse> responseObserver) {
      responseObserver.onNext(Profiler.TimeResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> responseObserver) {
      responseObserver.onNext(Profiler.VersionResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getBytes(Profiler.BytesRequest request, StreamObserver<Profiler.BytesResponse> responseObserver) {
      Profiler.BytesResponse.Builder builder = Profiler.BytesResponse.newBuilder();
      ByteString bytes = PAYLOAD_CACHE.get(request.getId());
      if (bytes != null) {
        builder.setContents(bytes);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
      responseObserver.onNext(Profiler.GetDevicesResponse.newBuilder().addDevice(Profiler.Device.newBuilder()
                                                                                   .setSerial(DEVICE_SERIAL)
                                                                                   .setBootId(BOOT_ID)
                                                                                   .build()).build());
      responseObserver.onCompleted();
    }

    @Override
    public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> responseObserver) {
      responseObserver.onNext(Profiler.GetProcessesResponse.newBuilder().addProcess(myProcessToReturn).build());
      responseObserver.onCompleted();
    }

    @Override
    public void getAgentStatus(Profiler.AgentStatusRequest request, StreamObserver<Profiler.AgentStatusResponse> responseObserver) {
      responseObserver.onNext(Profiler.AgentStatusResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }
}
