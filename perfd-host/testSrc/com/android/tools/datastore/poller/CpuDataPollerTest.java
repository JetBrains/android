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
import com.android.tools.profiler.proto.*;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.profiler.proto.CpuProfiler;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class CpuDataPollerTest extends DataStorePollerTest {

  private static final int TEST_APP_ID = 1234;
  private static final int THREAD_ID = 4321;
  private static final int THREAD_ID_2 = 2222;
  private static final String THREAD_NAME = "Thread1";
  private static final String THREAD_NAME_2 = "Thread2";
  private static final int TRACE_ID = 1111;
  private static final ByteString TRACE_DATA = ByteString.copyFrom("Test Data", Charset.defaultCharset());
  private static final long BASE_TIME_NS = System.nanoTime();
  private static final long ONE_SECOND_MS = TimeUnit.SECONDS.toMillis(1);
  private static final long TEN_SECONDS_MS = TimeUnit.SECONDS.toMillis(10);
  private static final CpuProfiler.ThreadActivities THREAD_ACTIVITIES = CpuProfiler.ThreadActivities.newBuilder()
    .addActivities(CpuProfiler.ThreadActivity.newBuilder()
                     .setNewState(CpuProfiler.ThreadActivity.State.WAITING)
                     .setName(THREAD_NAME)
                     .setTid(THREAD_ID)
                     .build())
    .addActivities(CpuProfiler.ThreadActivity.newBuilder()
                     .setNewState(CpuProfiler.ThreadActivity.State.STOPPED)
                     .setName(THREAD_NAME)
                     .setTid(THREAD_ID)
                     .build())
    .addActivities(CpuProfiler.ThreadActivity.newBuilder()
                     .setNewState(CpuProfiler.ThreadActivity.State.RUNNING)
                     .setName(THREAD_NAME_2)
                     .setTid(THREAD_ID_2)
                     .build())
    .build();

  private static final Common.CommonData STARTUP_BASIC_INFO = Common.CommonData.newBuilder()
    .setAppId(TEST_APP_ID)
    .setEndTimestamp(BASE_TIME_NS)
    .build();

  private static final CpuProfiler.CpuUsageData CPU_USAGE_DATA = CpuProfiler.CpuUsageData.newBuilder()
    .setAppCpuTimeInMillisec(ONE_SECOND_MS)
    .setElapsedTimeInMillisec(TEN_SECONDS_MS)
    .setSystemCpuTimeInMillisec(ONE_SECOND_MS * 2)
    .build();

  private CpuDataPoller myCpuDataPoller;
  @Rule
  public TestGrpcService<FakeCpuService> myService = new TestGrpcService<>(new FakeCpuService(), new FakeProfilerService());

  @Before
  public void setUp() throws Exception {
    myCpuDataPoller = new CpuDataPoller();
    myCpuDataPoller.connectService(myService.getChannel());
    myCpuDataPoller.poll();
  }

  @Test
  public void testGetDataInRange() {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTimestamp(0)
      .setEndTimestamp(BASE_TIME_NS)
      .build();
    CpuProfiler.CpuDataResponse expectedResponse = CpuProfiler.CpuDataResponse.newBuilder()
      .addData(CpuProfiler.CpuProfilerData.newBuilder()
                 .setBasicInfo(STARTUP_BASIC_INFO)
                 .setCpuUsage(CPU_USAGE_DATA)
                 .build())
      .addData(CpuProfiler.CpuProfilerData.newBuilder()
                 .setBasicInfo(STARTUP_BASIC_INFO)
                 .setThreadActivities(THREAD_ACTIVITIES)
                 .build())
      .build();
    StreamObserver<CpuProfiler.CpuDataResponse> observer = mock(StreamObserver.class);
    myCpuDataPoller.getData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetDataExcludeStart() {
    CpuProfiler.CpuDataRequest request = CpuProfiler.CpuDataRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    CpuProfiler.CpuDataResponse expectedResponse = CpuProfiler.CpuDataResponse.newBuilder()
      .build();
    StreamObserver<CpuProfiler.CpuDataResponse> observer = mock(StreamObserver.class);
    myCpuDataPoller.getData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceValid() {
    CpuProfiler.CpuProfilingAppStartRequest startRequest = CpuProfiler.CpuProfilingAppStartRequest.getDefaultInstance();
    StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> startObserver = mock(StreamObserver.class);
    myCpuDataPoller.startProfilingApp(startRequest, startObserver);
    CpuProfiler.CpuProfilingAppStopRequest stopRequest = CpuProfiler.CpuProfilingAppStopRequest.getDefaultInstance();
    StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> stopObserver = mock(StreamObserver.class);
    myCpuDataPoller.stopProfilingApp(stopRequest, stopObserver);

    CpuProfiler.GetTraceRequest request = CpuProfiler.GetTraceRequest.newBuilder()
      .setTraceId(TRACE_ID)
      .build();
    CpuProfiler.GetTraceResponse expectedResponse = CpuProfiler.GetTraceResponse.newBuilder()
      .setStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
      .setData(TRACE_DATA)
      .build();
    StreamObserver<CpuProfiler.GetTraceResponse> observer = mock(StreamObserver.class);
    myCpuDataPoller.getTrace(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetThreadsInRange() {
    CpuProfiler.GetThreadsRequest request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTimestamp(BASE_TIME_NS)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    CpuProfiler.GetThreadsResponse expectedResponse = CpuProfiler.GetThreadsResponse.newBuilder()
      .addThreads(CpuProfiler.GetThreadsResponse.Thread.newBuilder()
                    .setTid(THREAD_ID_2)
                    .setName(THREAD_NAME_2)
                    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING)
                                     .build())
                    .build())
      .addThreads(CpuProfiler.GetThreadsResponse.Thread.newBuilder()
                    .setTid(THREAD_ID)
                    .setName(THREAD_NAME)
                    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(CpuProfiler.GetThreadsResponse.State.WAITING)
                                     .build())
                    .addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                     .setNewState(CpuProfiler.GetThreadsResponse.State.STOPPED)
                                     .build())
                    .build())
      .build();
    StreamObserver<CpuProfiler.GetThreadsResponse> observer = mock(StreamObserver.class);
    myCpuDataPoller.getThreads(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceInfo() {
    CpuProfiler.CpuProfilingAppStartRequest startRequest = CpuProfiler.CpuProfilingAppStartRequest.getDefaultInstance();
    StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> startObserver = mock(StreamObserver.class);
    myCpuDataPoller.startProfilingApp(startRequest, startObserver);
    CpuProfiler.CpuProfilingAppStopRequest stopRequest = CpuProfiler.CpuProfilingAppStopRequest.getDefaultInstance();
    StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> stopObserver = mock(StreamObserver.class);
    myCpuDataPoller.stopProfilingApp(stopRequest, stopObserver);
    CpuProfiler.GetTraceInfoRequest request = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setAppId(TEST_APP_ID)
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
    myCpuDataPoller.getTraceInfo(request, observer);
    validateResponse(observer, expectedResponse);
  }

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {

    @Override
    public void getTimes(Profiler.TimesRequest request, StreamObserver<Profiler.TimesResponse> responseObserver) {
      responseObserver.onNext(Profiler.TimesResponse.newBuilder().setTimestampNs(BASE_TIME_NS).build());
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
        .addData(CpuProfiler.CpuProfilerData.newBuilder()
                   .setBasicInfo(STARTUP_BASIC_INFO)
                   .setThreadActivities(THREAD_ACTIVITIES)
                   .build())
        .build();
      responseObserver.onNext(response);
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
