/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.datastore.service;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.profiler.proto.Commands.Command;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Common.Stream;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.ExecuteRequest;
import com.android.tools.profiler.proto.Transport.ExecuteResponse;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profiler.proto.Transport.GetEventsRequest;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profiler.proto.Transport.VersionRequest;
import com.android.tools.profiler.proto.Transport.VersionResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.idea.io.grpc.Channel;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class UnifiedPipelineTransportServiceTest extends DataStorePollerTest {
  private DataStoreService myDataStore = mock(DataStoreService.class);

  private TransportService myTransportService = new TransportService(myDataStore, new UnifiedEventsTable(), getPollTicker()::run);

  private FakeTransportService myFakeService = new FakeTransportService();
  private Channel myChannel;
  private TestName myTestName = new TestName();
  private TestGrpcService myService =
    new TestGrpcService(UnifiedPipelineTransportServiceTest.class, myTestName, myTransportService, myFakeService);

  @Rule
  public RuleChain myChain = RuleChain.outerRule(myTestName).around(myService);

  @Before
  public void setUp() {
    when(myDataStore.getTransportClient(TEST_DEVICE_ID)).thenReturn(TransportServiceGrpc.newBlockingStub(myService.getChannel()));
    myChannel = myService.getChannel();
    myTransportService.connectToChannel(STREAM, myChannel);
  }

  @After
  public void tearDown() {
    myDataStore.shutdown();
  }

  @Test
  public void testGetTimes() {
    StreamObserver<TimeResponse> observer = mock(StreamObserver.class);
    myTransportService.getCurrentTime(TimeRequest.newBuilder().setStreamId(TEST_DEVICE_ID).build(), observer);
    validateResponse(observer, TimeResponse.getDefaultInstance());
  }

  @Test
  public void testGetVersion() {
    StreamObserver<VersionResponse> observer = mock(StreamObserver.class);
    myTransportService.getVersion(VersionRequest.newBuilder().setStreamId(TEST_DEVICE_ID).build(), observer);
    validateResponse(observer, VersionResponse.getDefaultInstance());
  }

  @Test
  public void streamConnectAndDisconnect() {
    // Get events from poller to validate we have a connection.
    StreamObserver<GetEventGroupsResponse> observer = mock(StreamObserver.class);
    myTransportService.getEventGroups(
      GetEventGroupsRequest.newBuilder()
        .setKind(Event.Kind.STREAM)
        .setStreamId(DataStoreService.DATASTORE_RESERVED_STREAM_ID)
        .build(),
      observer);
    EventGroup expectedGroup = EventGroup.newBuilder()
      .setGroupId(TEST_DEVICE_ID)
      .addEvents(
        Event.newBuilder()
          .setGroupId(TEST_DEVICE_ID)
          .setKind(Event.Kind.STREAM)
          .setStream(
            Common.StreamData.newBuilder()
              .setStreamConnected(
                Common.StreamData.StreamConnected.newBuilder()
                  .setStream(STREAM))))
      .build();

    ArgumentCaptor<GetEventGroupsResponse> response = ArgumentCaptor.forClass(GetEventGroupsResponse.class);
    verify(observer, times(1)).onNext(response.capture());
    assertThat(response.getValue().getGroupsCount()).isEqualTo(1);
    EventGroup actualGroup = response.getValue().getGroups(0);
    assertThat(actualGroup.getEventsCount()).isEqualTo(expectedGroup.getEventsCount());
    validateEventNoTimestamp(expectedGroup.getEvents(0), actualGroup.getEvents(0));

    // Disconnect service.
    myTransportService.disconnectFromChannel(myChannel);
    Mockito.reset(observer);
    response = ArgumentCaptor.forClass(GetEventGroupsResponse.class);
    myTransportService.getEventGroups(
      GetEventGroupsRequest.newBuilder()
        .setKind(Event.Kind.STREAM)
        .setStreamId(DataStoreService.DATASTORE_RESERVED_STREAM_ID)
        .build(),
      observer);
    verify(observer, times(1)).onNext(response.capture());
    expectedGroup = expectedGroup.toBuilder().addEvents(
      Event.newBuilder()
        .setGroupId(TEST_DEVICE_ID)
        .setKind(Event.Kind.STREAM)
        .setIsEnded(true)
        .setTimestamp(100))
      .build();
    assertThat(response.getValue().getGroupsCount()).isEqualTo(1);

    actualGroup = response.getValue().getGroups(0);
    assertThat(actualGroup.getEventsCount()).isEqualTo(expectedGroup.getEventsCount());
    validateEventNoTimestamp(expectedGroup.getEvents(0), actualGroup.getEvents(0));
    validateEventNoTimestamp(expectedGroup.getEvents(1), actualGroup.getEvents(1));
  }

  @Test
  public void executeRedirectsProperly() {
    StreamObserver<ExecuteResponse> observer = mock(StreamObserver.class);
    Command sentCommand = Command.newBuilder().setStreamId(
      TEST_DEVICE_ID).setType(Command.CommandType.BEGIN_SESSION).setPid(1).build();
    myTransportService.execute(
      ExecuteRequest.newBuilder().setCommand(sentCommand).build(),
      observer);
    Command expectedCommand = sentCommand.toBuilder().setCommandId(myTransportService.myNextCommandId.get()).build();
    assertThat(expectedCommand).isEqualTo(myFakeService.getLastCommandReceived());
    // Test executing a command on an invalid stream.
    myTransportService.execute(
      ExecuteRequest.newBuilder()
        .setCommand(Command.newBuilder().setStreamId(0xDEADBEEF).setType(Command.CommandType.BEGIN_SESSION).setPid(1)).build(),
      observer);
    assertThat(expectedCommand).isEqualTo(myFakeService.getLastCommandReceived());
  }

  private void validateEventNoTimestamp(Event expected, Event actual) {
    actual = actual.toBuilder().setTimestamp(expected.getTimestamp()).build();
    assertThat(expected).isEqualTo(actual);
  }

  private static class FakeTransportService extends TransportServiceGrpc.TransportServiceImplBase {

    private Command myLastCommandReceived;

    public Command getLastCommandReceived() {
      return myLastCommandReceived;
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
    public void getDevices(Transport.GetDevicesRequest request, StreamObserver<Transport.GetDevicesResponse> responseObserver) {
      responseObserver.onNext(Transport.GetDevicesResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getProcesses(Transport.GetProcessesRequest request, StreamObserver<Transport.GetProcessesResponse> responseObserver) {
      responseObserver.onNext(Transport.GetProcessesResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void execute(ExecuteRequest request, StreamObserver<ExecuteResponse> responseObserver) {
      myLastCommandReceived = request.getCommand();
      responseObserver.onNext(ExecuteResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getEvents(GetEventsRequest request, StreamObserver<Event> responseObserver) {
      responseObserver.onNext(Event.newBuilder()
                                .setKind(Event.Kind.PROCESS)
                                .setTimestamp(100)
                                .setPid(1)
                                .build());
      responseObserver.onCompleted();
    }

    @Override
    public void getEventGroups(GetEventGroupsRequest request, StreamObserver<GetEventGroupsResponse> responseObserver) {
      super.getEventGroups(request, responseObserver);
    }
  }
}
