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
import com.android.tools.profiler.proto.*;
import com.google.protobuf3jarjar.ByteString;
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
  private static final CpuProfiler.GetThreadsResponse.Thread THREAD1 = CpuProfiler.GetThreadsResponse.Thread.newBuilder()
    .setTid(THREAD_ID)
    .setName(THREAD_NAME)
    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(CpuProfiler.GetThreadsResponse.State.WAITING)
                     .setTimestamp(delayFromBase(0))
                     .build())
    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING)
                     .setTimestamp(delayFromBase(4))
                     .build())
    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(CpuProfiler.GetThreadsResponse.State.STOPPED)
                     .setTimestamp(delayFromBase(5))
                     .build())
    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(CpuProfiler.GetThreadsResponse.State.DEAD)
                     .setTimestamp(delayFromBase(15))
                     .build())
    .build();
  private static final CpuProfiler.GetThreadsResponse.Thread THREAD2 = CpuProfiler.GetThreadsResponse.Thread.newBuilder()
    .setTid(THREAD_ID_2)
    .setName(THREAD_NAME_2)
    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                     .setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING)
                     .setTimestamp(delayFromBase(3))
                     .build())
    .build();

  private static final Common.CommonData STARTUP_BASIC_INFO = Common.CommonData.newBuilder()
    .setProcessId(TEST_APP_ID)
    .setEndTimestamp(BASE_TIME_NS)
    .build();

  private static final CpuProfiler.CpuUsageData CPU_USAGE_DATA = CpuProfiler.CpuUsageData.newBuilder()
    .setAppCpuTimeInMillisec(ONE_SECOND_MS)
    .setElapsedTimeInMillisec(TEN_SECONDS_MS)
    .setSystemCpuTimeInMillisec(ONE_SECOND_MS * 2)
    .build();

  private DataStoreService myDataStoreService = mock(DataStoreService.class);
  private CpuService myCpuService = new CpuService(myDataStoreService, getPollTicker()::run, new HashMap<>());

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
    CpuProfiler.CpuStartRequest request = CpuProfiler.CpuStartRequest.newBuilder()
      .setProcessId(TEST_APP_ID).setSession(DataStorePollerTest.SESSION).build();
    myCpuService.startMonitoringApp(request, mock(StreamObserver.class));
  }

  private void stopMonitoringApp() {
    CpuProfiler.CpuStopRequest request = CpuProfiler.CpuStopRequest.newBuilder()
      .setProcessId(TEST_APP_ID).setSession(DataStorePollerTest.SESSION).build();
    myCpuService.stopMonitoringApp(request, mock(StreamObserver.class));
  }

  @Test
  public void testCheckAppProfilingStateWithNullClientShouldReturnDefaultInstance() {
    when(myDataStoreService.getCpuClient(any())).thenReturn(null);
    CpuProfiler.ProfilingStateRequest request = CpuProfiler.ProfilingStateRequest.newBuilder()
      .setProcessId(TEST_APP_ID).setTimestamp(BASE_TIME_NS).setSession(DataStorePollerTest.SESSION).build();

    StreamObserver<CpuProfiler.ProfilingStateResponse> observer = mock(StreamObserver.class);

    myCpuService.checkAppProfilingState(request, observer);
    validateResponse(observer, CpuProfiler.ProfilingStateResponse.getDefaultInstance());
  }

  @Test
  public void testAppStoppedRequestHandled() {
    stopMonitoringApp();
    StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> observer = mock(StreamObserver.class);
    myCpuService.startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest.getDefaultInstance(), observer);
    validateResponse(observer, CpuProfiler.CpuProfilingAppStartResponse.getDefaultInstance());
  }

  @Test
  public void testGetDataInRange() {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(0)
      .setEndTimestamp(delayFromBase(10))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    CpuProfiler.CpuDataResponse expectedResponse = CpuProfiler.CpuDataResponse.newBuilder()
      .addData(CpuProfiler.CpuProfilerData.newBuilder()
                 .setBasicInfo(STARTUP_BASIC_INFO)
                 .setCpuUsage(CPU_USAGE_DATA)
                 .build())
      .build();
    StreamObserver<CpuProfiler.CpuDataResponse> observer = mock(StreamObserver.class);
    myCpuService.getData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetDataCachedResponse() {
    StreamObserver<CpuProfiler.CpuDataResponse> observer = mock(StreamObserver.class);

    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    // Workaround to modify the expected response inside doAnswer.
    CpuProfiler.CpuDataResponse[] expectedResponse = new CpuProfiler.CpuDataResponse[1];
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuProfiler.CpuDataResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuProfiler.CpuDataResponse);
      // Store the response.
      expectedResponse[0] = (CpuProfiler.CpuDataResponse)invocation.getArguments()[0];
      return null;
    }).when(observer).onNext(any(CpuProfiler.CpuDataResponse.class));
    myCpuService.getData(request, observer);

    // Create a different request with the same arguments of the previous one.
    CpuProfiler.CpuDataRequest request2 = CpuProfiler.CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuProfiler.CpuDataResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuProfiler.CpuDataResponse);
      // Make sure that getData respond with the same object, as it should have been cached.
      assertTrue(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuProfiler.CpuDataResponse.class));
    myCpuService.getData(request2, observer);

    // Create yet another request with some different arguments than the previous ones.
    CpuProfiler.CpuDataRequest request3 = CpuProfiler.CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(21))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuProfiler.CpuDataResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuProfiler.CpuDataResponse);
      // Make sure that getData respond with a different object, as the cached object had a different end timestamp.
      assertFalse(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuProfiler.CpuDataResponse.class));
    myCpuService.getData(request3, observer);
  }

  @Test
  public void testGetDataExcludeStart() {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(delayFromBase(11))
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    CpuProfiler.CpuDataResponse expectedResponse = CpuProfiler.CpuDataResponse.newBuilder()
      .build();
    StreamObserver<CpuProfiler.CpuDataResponse> observer = mock(StreamObserver.class);
    myCpuService.getData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void traceProfilerTypeShouldBeCorrectlySet() {
    CpuProfiler.CpuProfilerType traceType = CpuProfiler.CpuProfilerType.SIMPLE_PERF;
    CpuProfiler.CpuProfilingAppStartRequest startRequest = CpuProfiler.CpuProfilingAppStartRequest.newBuilder()
      .setProfilerType(traceType)
      .build();
    StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> startObserver = mock(StreamObserver.class);
    myCpuService.startProfilingApp(startRequest, startObserver);
    CpuProfiler.CpuProfilingAppStopRequest stopRequest = CpuProfiler.CpuProfilingAppStopRequest.newBuilder()
      .setProfilerType(traceType)
      .build();
    StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> stopObserver = mock(StreamObserver.class);
    myCpuService.stopProfilingApp(stopRequest, stopObserver);

    CpuProfiler.GetTraceRequest request = CpuProfiler.GetTraceRequest.newBuilder()
      .setTraceId(TRACE_ID)
      .build();
    CpuProfiler.GetTraceResponse expectedResponse = CpuProfiler.GetTraceResponse.newBuilder()
      .setStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
      .setData(TRACE_DATA)
      .setProfilerType(traceType)
      .build();
    StreamObserver<CpuProfiler.GetTraceResponse> observer = mock(StreamObserver.class);
    myCpuService.getTrace(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceValid() {
    CpuProfiler.CpuProfilingAppStartRequest startRequest = CpuProfiler.CpuProfilingAppStartRequest.getDefaultInstance();
    StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> startObserver = mock(StreamObserver.class);
    myCpuService.startProfilingApp(startRequest, startObserver);
    CpuProfiler.CpuProfilingAppStopRequest stopRequest = CpuProfiler.CpuProfilingAppStopRequest.getDefaultInstance();
    StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> stopObserver = mock(StreamObserver.class);
    myCpuService.stopProfilingApp(stopRequest, stopObserver);

    CpuProfiler.GetTraceRequest request = CpuProfiler.GetTraceRequest.newBuilder()
      .setTraceId(TRACE_ID)
      .build();
    CpuProfiler.GetTraceResponse expectedResponse = CpuProfiler.GetTraceResponse.newBuilder()
      .setStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
      .setData(TRACE_DATA)
      .build();
    StreamObserver<CpuProfiler.GetTraceResponse> observer = mock(StreamObserver.class);
    myCpuService.getTrace(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetThreadsInRange() {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    CpuProfiler.GetThreadsResponse expectedResponse = CpuProfiler.GetThreadsResponse.newBuilder()
      // Threads are returned ordered by id
      .addThreads(CpuProfiler.GetThreadsResponse.Thread.newBuilder()
                    .setTid(THREAD_ID_2)
                    .setName(THREAD_NAME_2)
                    // Actual activity
                    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING)
                                     .setTimestamp(delayFromBase(3))
                                     .build())
                    .build())
      .addThreads(CpuProfiler.GetThreadsResponse.Thread.newBuilder()
                    .setTid(THREAD_ID)
                    .setName(THREAD_NAME)
                    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(CpuProfiler.GetThreadsResponse.State.WAITING)
                                     .setTimestamp(delayFromBase(0))
                                     .build())
                    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING)
                                     .setTimestamp(delayFromBase(4))
                                     .build())
                    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(CpuProfiler.GetThreadsResponse.State.STOPPED)
                                     .setTimestamp(delayFromBase(5))
                                     .build())
                    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(CpuProfiler.GetThreadsResponse.State.DEAD)
                                     .setTimestamp(delayFromBase(15))
                                     .build())
                    .build())
      .build();
    StreamObserver<CpuProfiler.GetThreadsResponse> observer = mock(StreamObserver.class);
    myCpuService.getThreads(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetThreadsCachedResponse() {
    StreamObserver<CpuProfiler.GetThreadsResponse> observer = mock(StreamObserver.class);

    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    // Workaround to modify the expected response inside doAnswer.
    CpuProfiler.GetThreadsResponse[] expectedResponse = new CpuProfiler.GetThreadsResponse[1];
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuProfiler.GetThreadsResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuProfiler.GetThreadsResponse);
      // Store the response.
      expectedResponse[0] = (CpuProfiler.GetThreadsResponse)invocation.getArguments()[0];
      return null;
    }).when(observer).onNext(any(CpuProfiler.GetThreadsResponse.class));
    myCpuService.getThreads(request, observer);

    // Create a different request with the same arguments of the previous one.
    CpuProfiler.GetThreadsRequest request2 = CpuProfiler.GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuProfiler.GetThreadsResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuProfiler.GetThreadsResponse);
      // Make sure that getThreads respond with the same object, as it should have been cached.
      assertTrue(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuProfiler.GetThreadsResponse.class));
    myCpuService.getThreads(request2, observer);

    // Create yet another request with some different arguments than the previous ones.
    CpuProfiler.GetThreadsRequest request3 = CpuProfiler.GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(delayFromBase(21))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuProfiler.GetThreadsResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuProfiler.GetThreadsResponse);
      // Make sure that getThreads respond with a different object, as the cached object had a different end timestamp.
      assertFalse(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuProfiler.GetThreadsResponse.class));
    myCpuService.getThreads(request3, observer);
  }

  @Test
  public void getDeadThreadBeforeRange() {
    long startTimestamp = delayFromBase(20);
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTimestamp(startTimestamp)
      .setEndTimestamp(delayFromBase(40))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    CpuProfiler.GetThreadsResponse expectedResponse = CpuProfiler.GetThreadsResponse.newBuilder()
      // THREAD_ID is not returned because it died before the requested range start
      .addThreads(CpuProfiler.GetThreadsResponse.Thread.newBuilder()
                    .setTid(THREAD_ID_2)
                    .setName(THREAD_NAME_2)
                    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING)
                                     // As there was no activity during the request range, the state of the thread
                                     // in the last initial states snapshot is set to the start timestamp of the
                                     // request.
                                     .setTimestamp(startTimestamp)
                                     .build())
                    .build())
      .build();
    StreamObserver<CpuProfiler.GetThreadsResponse> observer = mock(StreamObserver.class);
    myCpuService.getThreads(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceInfo() {
    CpuProfiler.SaveTraceInfoRequest saveRequest = CpuProfiler.SaveTraceInfoRequest.newBuilder()
      .setTraceInfo(CpuProfiler.TraceInfo.newBuilder()
                      .setFromTimestamp(BASE_TIME_NS)
                      .setToTimestamp(BASE_TIME_NS)
                      .setTraceId(TRACE_ID))
      .setProcessId(TEST_APP_ID)
      .setSession(SESSION)
      .build();
    myCpuService.saveTraceInfo(saveRequest, mock(StreamObserver.class));

    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setSession(SESSION)
      .setFromTimestamp(BASE_TIME_NS)
      .setToTimestamp(Long.MAX_VALUE)
      .build();
    CpuProfiler.GetTraceInfoResponse expectedResponse = CpuProfiler.GetTraceInfoResponse.newBuilder()
      .addTraceInfo(CpuProfiler.TraceInfo.newBuilder()
                      .setFromTimestamp(BASE_TIME_NS)
                      .setToTimestamp(BASE_TIME_NS)
                      .setTraceId(TRACE_ID))
      .build();
    StreamObserver<CpuProfiler.GetTraceInfoResponse> observer = mock(StreamObserver.class);
    myCpuService.getTraceInfo(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceInfoCachedResponse() {
    StreamObserver<CpuProfiler.GetTraceInfoResponse> observer = mock(StreamObserver.class);

    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setFromTimestamp(BASE_TIME_NS)
      .setToTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    // Workaround to modify the expected response inside doAnswer.
    CpuProfiler.GetTraceInfoResponse[] expectedResponse = new CpuProfiler.GetTraceInfoResponse[1];
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuProfiler.GetTraceInfoResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuProfiler.GetTraceInfoResponse);
      // Store the response.
      expectedResponse[0] = (CpuProfiler.GetTraceInfoResponse)invocation.getArguments()[0];
      return null;
    }).when(observer).onNext(any(CpuProfiler.GetTraceInfoResponse.class));
    myCpuService.getTraceInfo(request, observer);

    // Create a different request with the same arguments of the previous one.
    CpuProfiler.GetTraceInfoRequest request2 = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setFromTimestamp(BASE_TIME_NS)
      .setToTimestamp(delayFromBase(20))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuProfiler.GetTraceInfoResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuProfiler.GetTraceInfoResponse);
      // Make sure that getTraceInfo respond with the same object, as it should have been cached.
      assertTrue(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuProfiler.GetTraceInfoResponse.class));
    myCpuService.getTraceInfo(request2, observer);

    // Create yet another request with some different arguments than the previous ones.
    CpuProfiler.GetTraceInfoRequest request3 = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setFromTimestamp(BASE_TIME_NS)
      .setToTimestamp(delayFromBase(21))
      .setSession(DataStorePollerTest.SESSION)
      .build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuProfiler.GetTraceInfoResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuProfiler.GetTraceInfoResponse);
      // Make sure that getThreads respond with a different object, as the cached object had a different end timestamp.
      assertFalse(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuProfiler.GetTraceInfoResponse.class));
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
    public void getData(CpuProfiler.CpuDataRequest request, StreamObserver<CpuProfiler.CpuDataResponse> responseObserver) {
      CpuProfiler.CpuDataResponse response = CpuProfiler.CpuDataResponse.newBuilder()
        .addData(CpuProfiler.CpuProfilerData.newBuilder()
                   .setBasicInfo(STARTUP_BASIC_INFO)
                   .setCpuUsage(CPU_USAGE_DATA)
                   .build()
        )
        .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void getThreads(CpuProfiler.GetThreadsRequest request, StreamObserver<CpuProfiler.GetThreadsResponse> responseObserver) {
      CpuProfiler.GetThreadsResponse.Builder response = CpuProfiler.GetThreadsResponse.newBuilder();
      response.addThreads(THREAD1).addThreads(THREAD2);

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void startMonitoringApp(CpuProfiler.CpuStartRequest request, StreamObserver<CpuProfiler.CpuStartResponse> responseObserver) {
      responseObserver.onNext(CpuProfiler.CpuStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopMonitoringApp(CpuProfiler.CpuStopRequest request, StreamObserver<CpuProfiler.CpuStopResponse> responseObserver) {
      responseObserver.onNext(CpuProfiler.CpuStopResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest request,
                                  StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> responseObserver) {
      responseObserver.onNext(CpuProfiler.CpuProfilingAppStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopProfilingApp(CpuProfiler.CpuProfilingAppStopRequest request,
                                 StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> responseObserver) {
      responseObserver.onNext(CpuProfiler.CpuProfilingAppStopResponse.newBuilder()
                                .setTraceId(TRACE_ID)
                                .setTrace(TRACE_DATA)
                                .build());
      responseObserver.onCompleted();
    }
  }
}
