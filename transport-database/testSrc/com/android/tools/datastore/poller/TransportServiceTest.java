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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.datastore.service.TransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.AgentData;
import com.android.tools.profiler.proto.Transport.AgentStatusRequest;
import com.android.tools.profiler.proto.Transport.BytesRequest;
import com.android.tools.profiler.proto.Transport.BytesResponse;
import com.android.tools.profiler.proto.Transport.GetDevicesRequest;
import com.android.tools.profiler.proto.Transport.GetDevicesResponse;
import com.android.tools.profiler.proto.Transport.GetProcessesRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesResponse;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profiler.proto.Transport.VersionRequest;
import com.android.tools.profiler.proto.Transport.VersionResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.google.common.collect.ImmutableMap;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;

public class TransportServiceTest extends DataStorePollerTest {
  private static final Common.Process INITIAL_PROCESS = Common.Process
    .newBuilder().setDeviceId(DEVICE.getDeviceId()).setPid(1234).setName("INITIAL").build();
  private static final Common.Process FINAL_PROCESS = Common.Process
    .newBuilder().setDeviceId(DEVICE.getDeviceId()).setPid(4321).setName("FINAL").build();

  private DataStoreService myDataStore = mock(DataStoreService.class);

  private TransportService myTransportService = new TransportService(myDataStore, getPollTicker()::run);

  private static final String BYTES_ID_1 = "0123456789";
  private static final String BYTES_ID_2 = "9876543210";
  private static final String BAD_ID = "0000000000";
  private static final ByteString BYTES_1 = ByteString.copyFromUtf8("FILE_1");
  private static final ByteString BYTES_2 = ByteString.copyFromUtf8("FILE_2");

  private static final Map<String, ByteString> PAYLOAD_CACHE = new ImmutableMap.Builder<String, ByteString>()
    .put(BYTES_ID_1, BYTES_1).put(BYTES_ID_2, BYTES_2).build();

  private FakeTransportService myFakeService = new FakeTransportService();
  private TestName myTestName = new TestName();
  private TestGrpcService myService = new TestGrpcService(TransportServiceTest.class, myTestName, myTransportService, myFakeService);

  @Rule
  public RuleChain myChain = RuleChain.outerRule(myTestName).around(myService);

  @Before
  public void setUp() {
    when(myDataStore.getTransportClient(anyLong())).thenReturn(TransportServiceGrpc.newBlockingStub(myService.getChannel()));
    myTransportService.connectToChannel(STREAM, myService.getChannel());
  }

  @After
  public void tearDown() {
    myDataStore.shutdown();
  }

  @Test
  public void testGetTimes() {
    StreamObserver<TimeResponse> observer = mock(StreamObserver.class);
    myTransportService.getCurrentTime(TimeRequest.getDefaultInstance(), observer);
    validateResponse(observer, TimeResponse.getDefaultInstance());
  }

  @Test
  public void testGetVersion() {
    StreamObserver<VersionResponse> observer = mock(StreamObserver.class);
    myTransportService.getVersion(VersionRequest.getDefaultInstance(), observer);
    validateResponse(observer, VersionResponse.getDefaultInstance());
  }

  @Test
  public void testGetDevices() {
    StreamObserver<GetDevicesResponse> observer = mock(StreamObserver.class);
    GetDevicesResponse expected = GetDevicesResponse.newBuilder()
      .addDevice(DEVICE)
      .build();
    myTransportService.getDevices(GetDevicesRequest.getDefaultInstance(), observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testDeviceDisconnect() {
    myService.shutdownServer();
    getPollTicker().run();
    StreamObserver<GetProcessesResponse> observer = mock(StreamObserver.class);
    GetProcessesResponse expected = GetProcessesResponse
      .newBuilder().addProcess(INITIAL_PROCESS.toBuilder().setState(Common.Process.State.DEAD)).build();
    GetProcessesRequest request = GetProcessesRequest.newBuilder().setDeviceId(DEVICE.getDeviceId()).build();
    myTransportService.getProcesses(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetProcesses() {
    StreamObserver<GetProcessesResponse> observer = mock(StreamObserver.class);
    GetProcessesRequest request = GetProcessesRequest.newBuilder().setDeviceId(DEVICE.getDeviceId()).build();
    GetProcessesResponse expected = GetProcessesResponse.newBuilder().addProcess(INITIAL_PROCESS).build();
    myTransportService.getProcesses(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetDeadProcesses() {
    StreamObserver<GetProcessesResponse> observer = mock(StreamObserver.class);
    GetProcessesRequest request = GetProcessesRequest.newBuilder().setDeviceId(DEVICE.getDeviceId()).build();
    GetProcessesResponse expected = GetProcessesResponse.newBuilder().addProcess(INITIAL_PROCESS).build();
    myTransportService.getProcesses(request, observer);
    validateResponse(observer, expected);

    // Change the process list for the second tick.
    // We expect to get back both processes, the initial process
    // Should be set to a dead state.
    myFakeService.setProcessToReturn(FINAL_PROCESS);
    getPollTicker().run();
    observer = mock(StreamObserver.class);
    expected = GetProcessesResponse
      .newBuilder().addProcess(INITIAL_PROCESS.toBuilder().setState(Common.Process.State.DEAD)).addProcess(FINAL_PROCESS).build();
    myTransportService.getProcesses(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetFile() {
    StreamObserver<BytesResponse> observer1 = mock(StreamObserver.class);
    BytesRequest request1 = BytesRequest.newBuilder().setId(BYTES_ID_1).build();
    BytesResponse response1 = BytesResponse.newBuilder().setContents(BYTES_1).build();
    myTransportService.getBytes(request1, observer1);
    validateResponse(observer1, response1);

    StreamObserver<BytesResponse> observer2 = mock(StreamObserver.class);
    BytesRequest request2 = BytesRequest.newBuilder().setId(BYTES_ID_2).build();
    BytesResponse response2 = BytesResponse.newBuilder().setContents(BYTES_2).build();
    myTransportService.getBytes(request2, observer2);
    validateResponse(observer2, response2);

    StreamObserver<BytesResponse> observerNoMatch = mock(StreamObserver.class);
    BytesRequest requestBad =
      BytesRequest.newBuilder().setId(BAD_ID).build();
    BytesResponse responseNoMatch = BytesResponse.getDefaultInstance();
    myTransportService.getBytes(requestBad, observerNoMatch);
    validateResponse(observerNoMatch, responseNoMatch);
  }

  @Test
  public void testGetFileCached() {
    // Pull data into the database cache.
    StreamObserver<BytesResponse> observer1 = mock(StreamObserver.class);
    BytesRequest request1 = BytesRequest.newBuilder().setId(BYTES_ID_1).build();
    BytesResponse response1 = BytesResponse.newBuilder().setContents(BYTES_1).build();
    myTransportService.getBytes(request1, observer1);
    validateResponse(observer1, response1);

    StreamObserver<BytesResponse> observer2 = mock(StreamObserver.class);
    BytesRequest request2 = BytesRequest.newBuilder().setId(BYTES_ID_2).build();
    BytesResponse response2 = BytesResponse.newBuilder().setContents(BYTES_2).build();
    myTransportService.getBytes(request2, observer2);
    validateResponse(observer2, response2);

    // Disconnect the client
    when(myDataStore.getTransportClient(anyLong())).thenReturn(null);

    // Validate that we get back the expected bytes
    observer1 = mock(StreamObserver.class);
    myTransportService.getBytes(request1, observer1);
    validateResponse(observer1, response1);

    observer2 = mock(StreamObserver.class);
    myTransportService.getBytes(request2, observer2);
    validateResponse(observer2, response2);

    // Validate that bad instances still return default.
    StreamObserver<BytesResponse> observerNoMatch = mock(StreamObserver.class);
    BytesRequest requestBad =
      BytesRequest.newBuilder().setId(BAD_ID).build();
    BytesResponse responseNoMatch = BytesResponse.getDefaultInstance();
    myTransportService.getBytes(requestBad, observerNoMatch);
    validateResponse(observerNoMatch, responseNoMatch);
  }

  @Test
  public void agentStatus() {
    getPollTicker().run();
    StreamObserver<AgentData> observer = mock(StreamObserver.class);
    myTransportService
      .getAgentStatus(AgentStatusRequest.newBuilder().setDeviceId(DEVICE.getDeviceId()).setPid(INITIAL_PROCESS.getPid()).build(), observer);
    AgentData response = AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build();
    validateResponse(observer, response);
  }

  private static class FakeTransportService extends TransportServiceGrpc.TransportServiceImplBase {

    private Common.Process myProcessToReturn = INITIAL_PROCESS;

    public void setProcessToReturn(Common.Process process) {
      myProcessToReturn = process;
    }

    @Override
    public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> responseObserver) {
      responseObserver.onNext(TimeResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getVersion(VersionRequest request, StreamObserver<VersionResponse> responseObserver) {
      responseObserver.onNext(VersionResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getBytes(BytesRequest request, StreamObserver<BytesResponse> responseObserver) {
      BytesResponse.Builder builder = BytesResponse.newBuilder();
      ByteString bytes = PAYLOAD_CACHE.get(request.getId());
      if (bytes != null) {
        builder.setContents(bytes);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getDevices(GetDevicesRequest request, StreamObserver<GetDevicesResponse> responseObserver) {
      responseObserver.onNext(GetDevicesResponse.newBuilder().addDevice(DEVICE).build());
      responseObserver.onCompleted();
    }

    @Override
    public void getProcesses(GetProcessesRequest request, StreamObserver<GetProcessesResponse> responseObserver) {
      responseObserver.onNext(GetProcessesResponse.newBuilder().addProcess(myProcessToReturn).build());
      responseObserver.onCompleted();
    }

    @Override
    public void getAgentStatus(AgentStatusRequest request, StreamObserver<AgentData> responseObserver) {
      responseObserver.onNext(AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build());
      responseObserver.onCompleted();
    }
  }
}