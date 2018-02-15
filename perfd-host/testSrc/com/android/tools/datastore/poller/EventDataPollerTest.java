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
import com.android.tools.datastore.service.EventService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;

import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventDataPollerTest extends DataStorePollerTest {

  private static final String ACTIVITY_NAME = "ActivityOne";
  private static final int ACTIVITY_HASH = ACTIVITY_NAME.hashCode();
  private static final int ACTION_ID = 4321;
  private static final long START_TIME = TimeUnit.DAYS.toNanos(1);
  private static final long ONE_SECOND = TimeUnit.SECONDS.toNanos(1);
  private static final EventProfiler.SystemData NO_END_SYSTEM_DATA = EventProfiler.SystemData.newBuilder()
    .setPid(TEST_APP_ID)
    .setActionId(ACTION_ID)
    .setStartTimestamp(START_TIME + ONE_SECOND)
    .setEndTimestamp(0)
    .setEventId(1)
    .build();
  private static final EventProfiler.SystemData LONG_SYSTEM_DATA = EventProfiler.SystemData.newBuilder()
    .setPid(TEST_APP_ID)
    .setActionId(ACTION_ID)
    .setStartTimestamp(START_TIME - ONE_SECOND)
    .setEndTimestamp(START_TIME)
    .setEventId(2)
    .build();
  private static final EventProfiler.ActivityData SIMPLE_ACTIVITY_DATA = EventProfiler.ActivityData.newBuilder()
    .setPid(TEST_APP_ID)
    .setName(ACTIVITY_NAME)
    .setHash(ACTIVITY_HASH)
    .addStateChanges(
      EventProfiler.ActivityStateData.newBuilder()
        .setState(EventProfiler.ActivityStateData.ActivityState.CREATED)
        .setTimestamp(START_TIME)
        .build()
    ).build();
  private static final EventProfiler.ActivityData ACTIVITY_DATA_UPDATE = EventProfiler.ActivityData.newBuilder()
    .setPid(TEST_APP_ID)
    .setName(ACTIVITY_NAME)
    .setHash(ACTIVITY_HASH)
    .addStateChanges(
      EventProfiler.ActivityStateData.newBuilder()
        .setState(EventProfiler.ActivityStateData.ActivityState.STARTED)
        .setTimestamp(START_TIME + ONE_SECOND)
        .build())
    .addStateChanges(
      EventProfiler.ActivityStateData.newBuilder()
        .setState(EventProfiler.ActivityStateData.ActivityState.PAUSED)
        .setTimestamp(START_TIME + ONE_SECOND * 2)
        .build())
    .build();

  private DataStoreService myDataStoreService = mock(DataStoreService.class);
  private EventService myEventDataPoller = new EventService(myDataStoreService, getPollTicker()::run);

  private TestName myTestName = new TestName();
  private TestGrpcService myService = new TestGrpcService(EventDataPollerTest.class, myTestName, myEventDataPoller, new EventServiceMock());
  @Rule
  public RuleChain myChain = RuleChain.outerRule(myTestName).around(myService);

  @Before
  public void setUp() throws Exception {
    when(myDataStoreService.getEventClient(any())).thenReturn(EventServiceGrpc.newBlockingStub(myService.getChannel()));
    startMonitoringApp();
    getPollTicker().run();
  }

  @After
  public void tearDown() {
    stopMonitoringApp();
  }

  private void startMonitoringApp() {
    EventProfiler.EventStartRequest request = EventProfiler.EventStartRequest.newBuilder().setSession(DataStorePollerTest.SESSION).build();
    myEventDataPoller.startMonitoringApp(request, mock(StreamObserver.class));
  }

  private void stopMonitoringApp() {
    EventProfiler.EventStopRequest request = EventProfiler.EventStopRequest.newBuilder().setSession(DataStorePollerTest.SESSION).build();
    myEventDataPoller.stopMonitoringApp(request, mock(StreamObserver.class));
  }

  @Test
  public void testAppStoppedRequestHandled() {
    when(myDataStoreService.getEventClient(any())).thenReturn(null);
    EventProfiler.EventStopRequest stopRequest =
      EventProfiler.EventStopRequest.newBuilder().setSession(DataStorePollerTest.SESSION).build();
    StreamObserver<EventProfiler.EventStopResponse> stopObserver = mock(StreamObserver.class);
    myEventDataPoller.stopMonitoringApp(stopRequest, stopObserver);
    validateResponse(stopObserver, EventProfiler.EventStopResponse.getDefaultInstance());
    EventProfiler.EventStartRequest startRequest =
      EventProfiler.EventStartRequest.newBuilder().setSession(DataStorePollerTest.SESSION).build();
    StreamObserver<EventProfiler.EventStartResponse> startObserver = mock(StreamObserver.class);
    myEventDataPoller.startMonitoringApp(startRequest, startObserver);
    validateResponse(startObserver, EventProfiler.EventStartResponse.getDefaultInstance());
  }

  @Test
  public void testGetSystemDataInRange() throws Exception {
    EventProfiler.EventDataRequest request = EventProfiler.EventDataRequest.newBuilder()
      .setSession(SESSION)
      .setStartTimestamp(START_TIME - ONE_SECOND)
      .setEndTimestamp(START_TIME)
      .build();
    EventProfiler.SystemDataResponse expectedResponse = EventProfiler.SystemDataResponse.newBuilder()
      .addData(LONG_SYSTEM_DATA)
      .build();

    StreamObserver<EventProfiler.SystemDataResponse> observer = mock(StreamObserver.class);
    myEventDataPoller.getSystemData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetSystemDataNoEnd() throws Exception {
    EventProfiler.EventDataRequest request = EventProfiler.EventDataRequest.newBuilder()
      .setSession(DataStorePollerTest.SESSION)
      .setStartTimestamp(START_TIME + ONE_SECOND * 2)
      .setEndTimestamp(START_TIME + ONE_SECOND * 5)
      .build();
    EventProfiler.SystemDataResponse expectedResponse = EventProfiler.SystemDataResponse.newBuilder()
      .addData(NO_END_SYSTEM_DATA)
      .build();

    StreamObserver<EventProfiler.SystemDataResponse> observer = mock(StreamObserver.class);
    myEventDataPoller.getSystemData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetSystemDataInvalidSession() throws Exception {
    EventProfiler.EventDataRequest request = EventProfiler.EventDataRequest.newBuilder()
      .setSession(Common.Session.getDefaultInstance())
      .setStartTimestamp(Long.MIN_VALUE)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    EventProfiler.SystemDataResponse expectedResponse = EventProfiler.SystemDataResponse.newBuilder()
      .build();

    StreamObserver<EventProfiler.SystemDataResponse> observer = mock(StreamObserver.class);
    myEventDataPoller.getSystemData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetActivityDataInRange() throws Exception {
    EventProfiler.EventDataRequest request = EventProfiler.EventDataRequest.newBuilder()
      .setSession(SESSION)
      .setStartTimestamp(START_TIME - ONE_SECOND)
      .setEndTimestamp(START_TIME)
      .build();
    EventProfiler.ActivityDataResponse expectedResponse = EventProfiler.ActivityDataResponse.newBuilder()
      .addData(SIMPLE_ACTIVITY_DATA.toBuilder()
                 .addStateChanges(EventProfiler.ActivityStateData.newBuilder()
                                    .setState(EventProfiler.ActivityStateData.ActivityState.STARTED)
                                    .setTimestamp(START_TIME + ONE_SECOND)
                                    .build())
                 .setFragmentData(EventProfiler.FragmentData.getDefaultInstance())
                 .build())
      .build();

    StreamObserver<EventProfiler.ActivityDataResponse> observer = mock(StreamObserver.class);
    myEventDataPoller.getActivityData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetActivityDataInvalidSession() throws Exception {
    EventProfiler.EventDataRequest request = EventProfiler.EventDataRequest.newBuilder()
      .setSession(Common.Session.getDefaultInstance())
      .setStartTimestamp(Long.MIN_VALUE)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    EventProfiler.ActivityDataResponse expectedResponse = EventProfiler.ActivityDataResponse.newBuilder()
      .build();

    StreamObserver<EventProfiler.ActivityDataResponse> observer = mock(StreamObserver.class);
    myEventDataPoller.getActivityData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  private static class EventServiceMock extends EventServiceGrpc.EventServiceImplBase {
    @Override
    public void getActivityData(EventProfiler.EventDataRequest request,
                                StreamObserver<EventProfiler.ActivityDataResponse> responseObserver) {
      EventProfiler.ActivityDataResponse activityResponse = EventProfiler.ActivityDataResponse.newBuilder()
        .addData(SIMPLE_ACTIVITY_DATA)
        .addData(ACTIVITY_DATA_UPDATE)
        .build();
      responseObserver.onNext(activityResponse);
      responseObserver.onCompleted();
    }

    @Override
    public void getSystemData(EventProfiler.EventDataRequest request, StreamObserver<EventProfiler.SystemDataResponse> responseObserver) {
      EventProfiler.SystemDataResponse systemResponse = EventProfiler.SystemDataResponse.newBuilder()
        //Add an event that doesn't stop
        .addData(NO_END_SYSTEM_DATA)
        //Add an event that last 1 second.
        .addData(LONG_SYSTEM_DATA)

        .build();
      responseObserver.onNext(systemResponse);
      responseObserver.onCompleted();
    }

    @Override
    public void startMonitoringApp(EventProfiler.EventStartRequest request,
                                   StreamObserver<EventProfiler.EventStartResponse> responseObserver) {
      responseObserver.onNext(EventProfiler.EventStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopMonitoringApp(EventProfiler.EventStopRequest request,
                                  StreamObserver<EventProfiler.EventStopResponse> responseObserver) {
      responseObserver.onNext(EventProfiler.EventStopResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }
}
