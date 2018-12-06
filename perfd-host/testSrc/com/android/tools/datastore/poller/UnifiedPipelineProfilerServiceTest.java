/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.FakeLogService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.datastore.service.ProfilerService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Common.Stream;
import com.android.tools.profiler.proto.Profiler.Command;
import com.android.tools.profiler.proto.Profiler.EventGroup;
import com.android.tools.profiler.proto.Profiler.ExecuteRequest;
import com.android.tools.profiler.proto.Profiler.ExecuteResponse;
import com.android.tools.profiler.proto.Profiler.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Profiler.GetEventGroupsResponse;
import com.android.tools.profiler.proto.Profiler.GetEventsRequest;
import com.android.tools.profiler.proto.Profiler.TimeRequest;
import com.android.tools.profiler.proto.Profiler.TimeResponse;
import com.android.tools.profiler.proto.Profiler.VersionRequest;
import com.android.tools.profiler.proto.Profiler.VersionResponse;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * ProfilerServiceTest for scenarios related to the new data pipeline, which goes through the
 * {@link ProfilerService#startPolling(Stream, Channel)} instead of the old {@link ProfilerService#startMonitoring(Channel)} API.
 */
public class UnifiedPipelineProfilerServiceTest extends DataStorePollerTest {
  private static final long DEVICE_ID = 1234;

  private DataStoreService myDataStore = mock(DataStoreService.class);

  private ProfilerService myProfilerService = new ProfilerService(myDataStore, getPollTicker()::run, new FakeLogService());

  private FakeProfilerService myFakeService = new FakeProfilerService();
  private Channel myChannel;
  private TestName myTestName = new TestName();
  private TestGrpcService myService =
    new TestGrpcService(UnifiedPipelineProfilerServiceTest.class, myTestName, myProfilerService, myFakeService);

  @Rule
  public RuleChain myChain = RuleChain.outerRule(myTestName).around(myService);

  @Before
  public void setUp() {
    myChannel = myService.getChannel();
    // Stream id is analogous to device id.
    Stream stream = Stream.newBuilder().setType(Stream.Type.DEVICE).setStreamId(DEVICE_ID).build();
    myProfilerService.startPolling(stream, myChannel);
  }

  @After
  public void tearDown() {
    myDataStore.shutdown();
  }

  @Test
  public void testGetTimes() {
    StreamObserver<TimeResponse> observer = mock(StreamObserver.class);
    myProfilerService.getCurrentTime(TimeRequest.newBuilder().setStreamId(DEVICE_ID).build(), observer);
    validateResponse(observer, TimeResponse.getDefaultInstance());
  }

  @Test
  public void testGetVersion() {
    StreamObserver<VersionResponse> observer = mock(StreamObserver.class);
    myProfilerService.getVersion(VersionRequest.newBuilder().setStreamId(DEVICE_ID).build(), observer);
    validateResponse(observer, VersionResponse.getDefaultInstance());
  }

  @Test
  public void streamConnectAndDisconnect() {
    // Force the poller to tick.
    getPollTicker().run();
    // Get events from poller to validate we have a connection.
    StreamObserver<GetEventGroupsResponse> observer = mock(StreamObserver.class);
    myProfilerService.getEventGroups(GetEventGroupsRequest.newBuilder().setKind(Event.Kind.STREAM).build(), observer);
    EventGroup expectedGroup = EventGroup.newBuilder()
      .setGroupId(DEVICE_ID)
      .addEvents(Event.newBuilder()
                   .setGroupId(DEVICE_ID)
                   .setKind(Event.Kind.STREAM)
                   .setStream(Common.StreamData.newBuilder()
                              .setStreamConnected(Common.StreamData.StreamConnected.newBuilder()
                                                    .setStream(Common.Stream.newBuilder()
                                                                 .setStreamId(DEVICE_ID).setType(Stream.Type.DEVICE)))))
      .build();

    ArgumentCaptor<GetEventGroupsResponse> response = ArgumentCaptor.forClass(GetEventGroupsResponse.class);
    verify(observer, times(1)).onNext(response.capture());
    assertThat(response.getValue().getGroupsCount()).isEqualTo(1);
    EventGroup actualGroup = response.getValue().getGroups(0);
    assertThat(actualGroup.getEventsCount()).isEqualTo(expectedGroup.getEventsCount());
    validateEventNoTimestamp(expectedGroup.getEvents(0), actualGroup.getEvents(0));

    // Disconnect service.
    myProfilerService.stopMonitoring(myChannel);
    Mockito.reset(observer);
    response = ArgumentCaptor.forClass(GetEventGroupsResponse.class);
    myProfilerService.getEventGroups(GetEventGroupsRequest.newBuilder().setKind(Event.Kind.STREAM).build(), observer);
    verify(observer, times(1)).onNext(response.capture());
    expectedGroup = expectedGroup.toBuilder().addEvents(Event.newBuilder()
                                                          .setGroupId(DEVICE_ID)
                                                          .setKind(Event.Kind.STREAM)
                                                          .setIsEnded(true)
                                                          .setTimestamp(100)).build();
    assertThat(response.getValue().getGroupsCount()).isEqualTo(1);

    actualGroup = response.getValue().getGroups(0);
    assertThat(actualGroup.getEventsCount()).isEqualTo(expectedGroup.getEventsCount());
    validateEventNoTimestamp(expectedGroup.getEvents(0), actualGroup.getEvents(0));
    validateEventNoTimestamp(expectedGroup.getEvents(1), actualGroup.getEvents(1));
  }

  @Test
  public void executeRedirectsProperly() {
    StreamObserver<ExecuteResponse> observer = mock(StreamObserver.class);
    Command sentCommand = Command.newBuilder().setStreamId(DEVICE_ID).setType(Command.CommandType.BEGIN_SESSION).build();
    myProfilerService.execute(
      ExecuteRequest.newBuilder().setCommand(sentCommand).build(),
      observer);
    assertThat(sentCommand).isEqualTo(myFakeService.getLastCommandReceived());
    // Test executing a command on an invalid stream.
    myProfilerService.execute(
      ExecuteRequest.newBuilder()
        .setCommand(Command.newBuilder().setStreamId(DEVICE_ID).setType(Command.CommandType.BEGIN_SESSION)).build(),
      observer);
    assertThat(sentCommand).isEqualTo(myFakeService.getLastCommandReceived());
  }

  private void validateEventNoTimestamp(Event expected, Event actual) {
    actual = actual.toBuilder().setTimestamp(expected.getTimestamp()).build();
    assertThat(expected).isEqualTo(actual);
  }

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {

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
                                .setSessionId(1)
                                .build());
      responseObserver.onCompleted();
    }

    @Override
    public void getEventGroups(GetEventGroupsRequest request, StreamObserver<GetEventGroupsResponse> responseObserver) {
      super.getEventGroups(request, responseObserver);
    }
  }
}
