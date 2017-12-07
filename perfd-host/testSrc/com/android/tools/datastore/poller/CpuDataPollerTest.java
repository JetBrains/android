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
import com.android.tools.datastore.service.CpuService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.profiler.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CpuDataPollerTest extends DataStorePollerTest {

  private static final int TEST_APP_ID = 1234;
  private static final int THREAD_ID = 4321;
  private static final int THREAD_ID_2 = 2222;
  private static final String THREAD_NAME = "Thread1";
  private static final String THREAD_NAME_2 = "Thread2";
  private static final int TRACE_ID = 1111;
  private static final ByteString TRACE_DATA = ByteString.copyFrom("Test Data", Charset.defaultCharset());
  private static final long BASE_TIME_NS = TimeUnit.DAYS.toNanos(1);
  private static final long ONE_SECOND_MS = TimeUnit.SECONDS.toMillis(1);
  private static final long TEN_SECONDS_MS = TimeUnit.SECONDS.toMillis(10);
  private static final GetThreadsResponse.Thread THREAD1 = GetThreadsResponse.Thread.newBuilder()
    .setTid(THREAD_ID)
    .setName(THREAD_NAME)
    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(GetThreadsResponse.State.WAITING)
                     .setTimestamp(delayFromBase(0))
                     .build())
    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(GetThreadsResponse.State.RUNNING)
                     .setTimestamp(delayFromBase(4))
                     .build())
    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(GetThreadsResponse.State.STOPPED)
                     .setTimestamp(delayFromBase(5))
                     .build())
    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(GetThreadsResponse.State.DEAD)
                     .setTimestamp(delayFromBase(15))
                     .build())
    .build();
  private static final GetThreadsResponse.Thread THREAD2 = GetThreadsResponse.Thread.newBuilder()
    .setTid(THREAD_ID_2)
    .setName(THREAD_NAME_2)
    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(GetThreadsResponse.State.RUNNING)
                     .setTimestamp(delayFromBase(3))
                     .build())
    .build();

  private static final Common.CommonData STARTUP_BASIC_INFO = Common.CommonData.newBuilder()
    .setProcessId(TEST_APP_ID)
    .setEndTimestamp(BASE_TIME_NS)
    .build();

  private static final CpuUsageData CPU_USAGE_DATA = CpuUsageData.newBuilder()
    .setAppCpuTimeInMillisec(ONE_SECOND_MS)
    .setElapsedTimeInMillisec(TEN_SECONDS_MS)
    .setSystemCpuTimeInMillisec(ONE_SECOND_MS * 2)
    .build();

  private DataStoreService myDataStoreService = mock(DataStoreService.class);
  private CpuService myCpuService = new CpuService(myDataStoreService, getPollTicker()::run);

  public TestName myTestName = new TestName();
  public TestGrpcService<FakeCpuService> myService =
    new TestGrpcService<>(CpuDataPollerTest.class, myTestName, myCpuService, new FakeCpuService(), new FakeProfilerService());

  @Rule
  public RuleChain myChain = RuleChain.outerRule(myTestName).around(myService);

  @Before
  public void setUp() throws Exception {
    when(myDataStoreService.getCpuClient(any())).thenReturn(CpuServiceGrpc.newBlockingStub(myService.getChannel()));
    when(myDataStoreService.getProfilerClient(any())).thenReturn(ProfilerServiceGrpc.newBlockingStub(myService.getChannel()));
    startMonitoringApp();
  }

  @After
  public void tearDown() {
    stopMonitoringApp();
  }

  private void startMonitoringApp() {
    CpuStartRequest request = CpuStartRequest.newBuilder()
      .setProcessId(TEST_APP_ID).setSession(DataStorePollerTest.SESSION).build();
    myCpuService.startMonitoringApp(request, mock(StreamObserver.class));
  }

  private void stopMonitoringApp() {
    CpuStopRequest request = CpuStopRequest.newBuilder()
      .setProcessId(TEST_APP_ID).setSession(DataStorePollerTest.SESSION).build();
    myCpuService.stopMonitoringApp(request, mock(StreamObserver.class));
  }

  @Test
  public void testCheckAppProfilingStateWithNullClientShouldReturnDefaultInstance() {
    when(myDataStoreService.getCpuClient(any())).thenReturn(null);
    ProfilingStateRequest request = ProfilingStateRequest.newBuilder()
      .setProcessId(TEST_APP_ID).setTimestamp(BASE_TIME_NS).setSession(DataStorePollerTest.SESSION).build();

    StreamObserver<ProfilingStateResponse> observer = mock(StreamObserver.class);

    myCpuService.checkAppProfilingState(request, observer);
    validateResponse(observer, ProfilingStateResponse.getDefaultInstance());
  }

  @Test
  public void testAppStoppedRequestHandled() {
    stopMonitoringApp();
    StreamObserver<CpuProfilingAppStartResponse> observer = mock(StreamObserver.class);
    myCpuService.startProfilingApp(CpuProfilingAppStartRequest.getDefaultInstance(), observer);
    validateResponse(observer, CpuProfilingAppStartResponse.getDefaultInstance());
  }

  @Test
  public void testGetDataInRange() {
    CpuDataRequest request = CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(0)
      .setEndTimestamp(delayFromBase(10))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    CpuDataResponse expectedResponse = CpuDataResponse.newBuilder()
      .addData(CpuProfilerData.newBuilder()
                 .setBasicInfo(STARTUP_BASIC_INFO)
                 .setCpuUsage(CPU_USAGE_DATA)
                 .build())
      .build();
    StreamObserver<CpuDataResponse> observer = mock(StreamObserver.class);
    myCpuService.getData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetDataCachedResponse() {
    StreamObserver<CpuDataResponse> observer = mock(StreamObserver.class);

    CpuDataRequest request = CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    // Workaround to modify the expected response inside doAnswer.
    CpuDataResponse[] expectedResponse = new CpuDataResponse[1];
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuDataResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuDataResponse);
      // Store the response.
      expectedResponse[0] = (CpuDataResponse)invocation.getArguments()[0];
      return null;
    }).when(observer).onNext(any(CpuDataResponse.class));
    myCpuService.getData(request, observer);

    // Create a different request with the same arguments of the previous one.
    CpuDataRequest request2 = CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuDataResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuDataResponse);
      // Make sure that getData respond with the same object, as it should have been cached.
      assertTrue(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuDataResponse.class));
    myCpuService.getData(request2, observer);

    // Create yet another request with some different arguments than the previous ones.
    CpuDataRequest request3 = CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(21))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuDataResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuDataResponse);
      // Make sure that getData respond with a different object, as the cached object had a different end timestamp.
      assertFalse(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuDataResponse.class));
    myCpuService.getData(request3, observer);
  }

  @Test
  public void testGetDataExcludeStart() {
    CpuDataRequest request = CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(delayFromBase(11))
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    CpuDataResponse expectedResponse = CpuDataResponse.newBuilder()
      .build();
    StreamObserver<CpuDataResponse> observer = mock(StreamObserver.class);
    myCpuService.getData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void traceProfilerTypeShouldBeCorrectlySet() {
    CpuProfilerType traceType = CpuProfilerType.SIMPLEPERF;
    CpuProfilingAppStartRequest startRequest = CpuProfilingAppStartRequest.newBuilder()
      .setProfilerType(traceType)
      .build();
    StreamObserver<CpuProfilingAppStartResponse> startObserver = mock(StreamObserver.class);
    myCpuService.startProfilingApp(startRequest, startObserver);
    CpuProfilingAppStopRequest stopRequest = CpuProfilingAppStopRequest.newBuilder()
      .setProfilerType(traceType)
      .build();
    StreamObserver<CpuProfilingAppStopResponse> stopObserver = mock(StreamObserver.class);
    myCpuService.stopProfilingApp(stopRequest, stopObserver);

    GetTraceRequest request = GetTraceRequest.newBuilder()
      .setTraceId(TRACE_ID)
      .build();
    GetTraceResponse expectedResponse = GetTraceResponse.newBuilder()
      .setStatus(GetTraceResponse.Status.SUCCESS)
      .setData(TRACE_DATA)
      .setProfilerType(traceType)
      .build();
    StreamObserver<GetTraceResponse> observer = mock(StreamObserver.class);
    myCpuService.getTrace(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceValid() {
    CpuProfilingAppStartRequest startRequest = CpuProfilingAppStartRequest.getDefaultInstance();
    StreamObserver<CpuProfilingAppStartResponse> startObserver = mock(StreamObserver.class);
    myCpuService.startProfilingApp(startRequest, startObserver);
    CpuProfilingAppStopRequest stopRequest = CpuProfilingAppStopRequest.getDefaultInstance();
    StreamObserver<CpuProfilingAppStopResponse> stopObserver = mock(StreamObserver.class);
    myCpuService.stopProfilingApp(stopRequest, stopObserver);

    GetTraceRequest request = GetTraceRequest.newBuilder()
      .setTraceId(TRACE_ID)
      .build();
    GetTraceResponse expectedResponse = GetTraceResponse.newBuilder()
      .setStatus(GetTraceResponse.Status.SUCCESS)
      .setData(TRACE_DATA)
      .build();
    StreamObserver<GetTraceResponse> observer = mock(StreamObserver.class);
    myCpuService.getTrace(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetThreadsInRange() {
    GetThreadsRequest request = GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    GetThreadsResponse expectedResponse = GetThreadsResponse.newBuilder()
      // Threads are returned ordered by id
      .addThreads(GetThreadsResponse.Thread.newBuilder()
                    .setTid(THREAD_ID_2)
                    .setName(THREAD_NAME_2)
                    // Actual activity
                    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(GetThreadsResponse.State.RUNNING)
                                     .setTimestamp(delayFromBase(3))
                                     .build())
                    .build())
      .addThreads(GetThreadsResponse.Thread.newBuilder()
                    .setTid(THREAD_ID)
                    .setName(THREAD_NAME)
                    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(GetThreadsResponse.State.WAITING)
                                     .setTimestamp(delayFromBase(0))
                                     .build())
                    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(GetThreadsResponse.State.RUNNING)
                                     .setTimestamp(delayFromBase(4))
                                     .build())
                    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(GetThreadsResponse.State.STOPPED)
                                     .setTimestamp(delayFromBase(5))
                                     .build())
                    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(GetThreadsResponse.State.DEAD)
                                     .setTimestamp(delayFromBase(15))
                                     .build())
                    .build())
      .build();
    StreamObserver<GetThreadsResponse> observer = mock(StreamObserver.class);
    myCpuService.getThreads(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetThreadsCachedResponse() {
    StreamObserver<GetThreadsResponse> observer = mock(StreamObserver.class);

    GetThreadsRequest request = GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    // Workaround to modify the expected response inside doAnswer.
    GetThreadsResponse[] expectedResponse = new GetThreadsResponse[1];
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetThreadsResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetThreadsResponse);
      // Store the response.
      expectedResponse[0] = (GetThreadsResponse)invocation.getArguments()[0];
      return null;
    }).when(observer).onNext(any(GetThreadsResponse.class));
    myCpuService.getThreads(request, observer);

    // Create a different request with the same arguments of the previous one.
    GetThreadsRequest request2 = GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetThreadsResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetThreadsResponse);
      // Make sure that getThreads respond with the same object, as it should have been cached.
      assertTrue(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(GetThreadsResponse.class));
    myCpuService.getThreads(request2, observer);

    // Create yet another request with some different arguments than the previous ones.
    GetThreadsRequest request3 = GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(21))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetThreadsResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetThreadsResponse);
      // Make sure that getThreads respond with a different object, as the cached object had a different end timestamp.
      assertFalse(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(GetThreadsResponse.class));
    myCpuService.getThreads(request3, observer);
  }

  @Test
  public void getDeadThreadBeforeRange() {
    long startTimestamp = delayFromBase(20);
    GetThreadsRequest request = GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(startTimestamp)
      .setEndTimestamp(delayFromBase(40))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    GetThreadsResponse expectedResponse = GetThreadsResponse.newBuilder()
      // THREAD_ID is not returned because it died before the requested range start
      .addThreads(GetThreadsResponse.Thread.newBuilder()
                    .setTid(THREAD_ID_2)
                    .setName(THREAD_NAME_2)
                    .addActivities(GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(GetThreadsResponse.State.RUNNING)
                                     // As there was no activity during the request range, the state of the thread
                                     // in the last initial states snapshot is set to the start timestamp of the
                                     // request.
                                     .setTimestamp(startTimestamp)
                                     .build())
                    .build())
      .build();
    StreamObserver<GetThreadsResponse> observer = mock(StreamObserver.class);
    myCpuService.getThreads(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceInfo() {
    SaveTraceInfoRequest saveRequest = SaveTraceInfoRequest.newBuilder()
      .setTraceInfo(TraceInfo.newBuilder()
                      .setFromTimestamp(BASE_TIME_NS)
                      .setToTimestamp(BASE_TIME_NS)
                      .setTraceId(TRACE_ID))
      .setProcessId(TEST_APP_ID)
      .setSession(SESSION)
      .build();
    myCpuService.saveTraceInfo(saveRequest, mock(StreamObserver.class));

    GetTraceInfoRequest request = GetTraceInfoRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setSession(SESSION)
      .setFromTimestamp(BASE_TIME_NS)
      .setToTimestamp(Long.MAX_VALUE)
      .build();
    GetTraceInfoResponse expectedResponse = GetTraceInfoResponse.newBuilder()
      .addTraceInfo(TraceInfo.newBuilder()
                      .setFromTimestamp(BASE_TIME_NS)
                      .setToTimestamp(BASE_TIME_NS)
                      .setTraceId(TRACE_ID))
      .build();
    StreamObserver<GetTraceInfoResponse> observer = mock(StreamObserver.class);
    myCpuService.getTraceInfo(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceInfoCachedResponse() {
    StreamObserver<GetTraceInfoResponse> observer = mock(StreamObserver.class);

    GetTraceInfoRequest request = GetTraceInfoRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setFromTimestamp(BASE_TIME_NS)
      .setToTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    // Workaround to modify the expected response inside doAnswer.
    GetTraceInfoResponse[] expectedResponse = new GetTraceInfoResponse[1];
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetTraceInfoResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetTraceInfoResponse);
      // Store the response.
      expectedResponse[0] = (GetTraceInfoResponse)invocation.getArguments()[0];
      return null;
    }).when(observer).onNext(any(GetTraceInfoResponse.class));
    myCpuService.getTraceInfo(request, observer);

    // Create a different request with the same arguments of the previous one.
    GetTraceInfoRequest request2 = GetTraceInfoRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setFromTimestamp(BASE_TIME_NS)
      .setToTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetTraceInfoResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetTraceInfoResponse);
      // Make sure that getTraceInfo respond with the same object, as it should have been cached.
      assertTrue(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(GetTraceInfoResponse.class));
    myCpuService.getTraceInfo(request2, observer);

    // Create yet another request with some different arguments than the previous ones.
    GetTraceInfoRequest request3 = GetTraceInfoRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setFromTimestamp(BASE_TIME_NS)
      .setToTimestamp(delayFromBase(21))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetTraceInfoResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetTraceInfoResponse);
      // Make sure that getThreads respond with a different object, as the cached object had a different end timestamp.
      assertFalse(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(GetTraceInfoResponse.class));
    myCpuService.getTraceInfo(request3, observer);
  }

  private static long delayFromBase(int seconds) {
    return BASE_TIME_NS + TimeUnit.SECONDS.toNanos(seconds);
  }

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {

    @Override
    public void getCurrentTime(Profiler.TimeRequest request, StreamObserver<Profiler.TimeResponse> responseObserver) {
      responseObserver.onNext(Profiler.TimeResponse.newBuilder().setTimestampNs(BASE_TIME_NS).build());
      responseObserver.onCompleted();
    }
  }

  private static class FakeCpuService extends CpuServiceGrpc.CpuServiceImplBase {

    @Override
    public void getData(CpuDataRequest request, StreamObserver<CpuDataResponse> responseObserver) {
      CpuDataResponse response = CpuDataResponse.newBuilder()
        .addData(CpuProfilerData.newBuilder()
                   .setBasicInfo(STARTUP_BASIC_INFO)
                   .setCpuUsage(CPU_USAGE_DATA)
                   .build()
        )
        .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void getThreads(GetThreadsRequest request, StreamObserver<GetThreadsResponse> responseObserver) {
      GetThreadsResponse.Builder response = GetThreadsResponse.newBuilder();
      response.addThreads(THREAD1).addThreads(THREAD2);

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void startMonitoringApp(CpuStartRequest request, StreamObserver<CpuStartResponse> responseObserver) {
      responseObserver.onNext(CpuStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopMonitoringApp(CpuStopRequest request, StreamObserver<CpuStopResponse> responseObserver) {
      responseObserver.onNext(CpuStopResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void startProfilingApp(CpuProfilingAppStartRequest request,
                                  StreamObserver<CpuProfilingAppStartResponse> responseObserver) {
      responseObserver.onNext(CpuProfilingAppStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopProfilingApp(CpuProfilingAppStopRequest request,
                                 StreamObserver<CpuProfilingAppStopResponse> responseObserver) {
      responseObserver.onNext(CpuProfilingAppStopResponse.newBuilder()
                                .setTraceId(TRACE_ID)
                                .setTrace(TRACE_DATA)
                                .build());
      responseObserver.onCompleted();
    }
  }
}
