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

import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.FakeLogService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.datastore.service.ProfilerService;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.BeginSessionRequest;
import com.android.tools.profiler.proto.Profiler.BeginSessionResponse;
import com.android.tools.profiler.proto.Profiler.DeleteSessionRequest;
import com.android.tools.profiler.proto.Profiler.EndSessionRequest;
import com.android.tools.profiler.proto.Profiler.EndSessionResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionMetaDataRequest;
import com.android.tools.profiler.proto.Profiler.GetSessionMetaDataResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionsRequest;
import com.android.tools.profiler.proto.Profiler.GetSessionsResponse;
import com.android.tools.profiler.proto.Profiler.ImportSessionRequest;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;

public class ProfilerServiceTest extends DataStorePollerTest {
  private static final long DEVICE_ID = 1234;
  private static final Common.Session START_SESSION_1 = Common.Session
    .newBuilder()
    .setSessionId(1)
    .setStreamId(DEVICE_ID)
    .setPid(1234)
    .setStartTimestamp(100)
    .setEndTimestamp(Long.MAX_VALUE)
    .build();
  private static final Common.Session END_SESSION_1 = START_SESSION_1.toBuilder().setEndTimestamp(200).build();
  private static final Common.Session START_SESSION_2 = Common.Session
    .newBuilder()
    .setSessionId(2)
    .setStreamId(DEVICE_ID)
    .setPid(4321)
    .setStartTimestamp(150)
    .setEndTimestamp(Long.MAX_VALUE)
    .build();
  private static final Common.Session END_SESSION_2 = START_SESSION_2.toBuilder().setEndTimestamp(250).build();

  private DataStoreService myDataStore = mock(DataStoreService.class);

  private ProfilerService myProfilerService = new ProfilerService(myDataStore, new FakeLogService());

  private FakeProfilerService myFakeService = new FakeProfilerService();
  private TestName myTestName = new TestName();
  private TestGrpcService myService = new TestGrpcService(ProfilerServiceTest.class, myTestName, myProfilerService, myFakeService);

  @Rule
  public RuleChain myChain = RuleChain.outerRule(myTestName).around(myService);

  @Before
  public void setUp() {
    when(myDataStore.getProfilerClient(anyLong())).thenReturn(ProfilerServiceGrpc.newBlockingStub(myService.getChannel()));
  }

  @After
  public void tearDown() {
    myDataStore.shutdown();
  }

  @Test
  public void testGetSessionsAfterBeginEndSession() {
    StreamObserver<GetSessionsResponse> observer = mock(StreamObserver.class);

    // GetSessions should return an empty list initially
    GetSessionsResponse response = GetSessionsResponse.getDefaultInstance();
    myProfilerService.getSessions(GetSessionsRequest.getDefaultInstance(), observer);
    validateResponse(observer, response);

    // Calling beginSession should put the Session into the ProfilerTable
    myFakeService.setSessionToReturn(START_SESSION_1);
    myProfilerService.beginSession(BeginSessionRequest.getDefaultInstance(), mock(StreamObserver.class));
    observer = mock(StreamObserver.class);
    response = GetSessionsResponse.newBuilder().addSessions(START_SESSION_1).build();
    myProfilerService.getSessions(GetSessionsRequest.getDefaultInstance(), observer);
    validateResponse(observer, response);

    // Beginning another session
    myFakeService.setSessionToReturn(START_SESSION_2);
    myProfilerService.beginSession(BeginSessionRequest.getDefaultInstance(), mock(StreamObserver.class));
    observer = mock(StreamObserver.class);
    response = GetSessionsResponse.newBuilder().addSessions(START_SESSION_1).addSessions(START_SESSION_2).build();
    myProfilerService.getSessions(GetSessionsRequest.getDefaultInstance(), observer);
    validateResponse(observer, response);

    // End the first session
    myFakeService.setSessionToReturn(END_SESSION_1);
    myProfilerService.endSession(EndSessionRequest.getDefaultInstance(), mock(StreamObserver.class));
    observer = mock(StreamObserver.class);
    response = GetSessionsResponse.newBuilder().addSessions(END_SESSION_1).addSessions(START_SESSION_2).build();
    myProfilerService.getSessions(GetSessionsRequest.getDefaultInstance(), observer);
    validateResponse(observer, response);

    // End the second session
    myFakeService.setSessionToReturn(END_SESSION_2);
    myProfilerService.endSession(EndSessionRequest.getDefaultInstance(), mock(StreamObserver.class));
    observer = mock(StreamObserver.class);
    response = GetSessionsResponse.newBuilder().addSessions(END_SESSION_1).addSessions(END_SESSION_2).build();
    myProfilerService.getSessions(GetSessionsRequest.getDefaultInstance(), observer);
    validateResponse(observer, response);
  }

  @Test
  public void importSession() {
    // Import new session
    myProfilerService.importSession(ImportSessionRequest.newBuilder().setSession(END_SESSION_1).build(), mock(StreamObserver.class));
    StreamObserver<GetSessionMetaDataResponse> metaDataObserver = mock(StreamObserver.class);
    GetSessionMetaDataResponse metaDataResponse =
      GetSessionMetaDataResponse.newBuilder().setData(Common.SessionMetaData.newBuilder().setSessionId(END_SESSION_1.getSessionId()))
        .build();
    myProfilerService
      .getSessionMetaData(GetSessionMetaDataRequest.newBuilder().setSessionId(END_SESSION_1.getSessionId()).build(), metaDataObserver);
    validateResponse(metaDataObserver, metaDataResponse);

    // Delete imported session
    StreamObserver<GetSessionsResponse> observer = mock(StreamObserver.class);
    myProfilerService
      .deleteSession(DeleteSessionRequest.newBuilder().setSessionId(END_SESSION_1.getSessionId()).build(), mock(StreamObserver.class));
    GetSessionsResponse response = GetSessionsResponse.newBuilder().build();
    myProfilerService.getSessions(GetSessionsRequest.getDefaultInstance(), observer);
    validateResponse(observer, response);
  }

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {

    private Common.Session mySessionToReturn;

    /**
     * @param session The Session object to return when calling beginSession and endSession.
     */
    public void setSessionToReturn(Common.Session session) {
      mySessionToReturn = session;
    }

    @Override
    public void beginSession(BeginSessionRequest request, StreamObserver<BeginSessionResponse> responseObserver) {
      BeginSessionResponse.Builder responseBuilder = BeginSessionResponse.newBuilder();
      if (mySessionToReturn != null) {
        responseBuilder.setSession(mySessionToReturn);
      }
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void endSession(EndSessionRequest request, StreamObserver<EndSessionResponse> responseObserver) {
      EndSessionResponse.Builder responseBuilder = EndSessionResponse.newBuilder();
      if (mySessionToReturn != null) {
        responseBuilder.setSession(mySessionToReturn);
      }
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    }
  }
}