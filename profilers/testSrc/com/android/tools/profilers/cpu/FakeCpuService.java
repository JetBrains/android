/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Fake implementation of {@link CpuServiceGrpc.CpuServiceImplBase}.
 * This class is used by the tests of the {@link com.android.tools.profilers.cpu} package.
 */
public class FakeCpuService extends CpuServiceGrpc.CpuServiceImplBase {

  public static final int TOTAL_ELAPSED_TIME = 100;

  public static final long FAKE_TRACE_ID = 6L;

  public static final int FAKE_STOPPING_DURATION_MS = 123;

  private Trace.TraceStartStatus.Status myStartProfilingStatus = Trace.TraceStartStatus.Status.SUCCESS;

  private Trace.TraceStopStatus.Status myStopProfilingStatus = Trace.TraceStopStatus.Status.SUCCESS;

  private Common.Session mySession;

  private int myAppTimeMs;

  private int mySystemTimeMs;

  private boolean myEmptyUsageData;

  private long myTraceId = FAKE_TRACE_ID;

  private long myTraceDurationNs = TimeUnit.SECONDS.toNanos(1);

  private TraceType myProfilerType = TraceType.ART;

  private List<CpuProfiler.GetThreadsResponse.Thread> myThreads = new ArrayList<>();

  // LinkedHashMap to preserve insertion order when starting and stopping captures.
  private Map<Long, Trace.TraceInfo> myTraceInfoMap = new LinkedHashMap<>();

  /**
   * Session used in start/stop capturing gRPC requests in this fake service.
   */
  private Common.Session myStartStopCapturingSession;

  private Trace.TraceInfo myStartedTraceInfo = Trace.TraceInfo.getDefaultInstance();

  @Override
  public void startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest request,
                                StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> responseObserver) {
    CpuProfiler.CpuProfilingAppStartResponse.Builder response = CpuProfiler.CpuProfilingAppStartResponse.newBuilder();
    Trace.TraceStartStatus.Builder status = Trace.TraceStartStatus.newBuilder();
    status.setStatus(myStartProfilingStatus);
    if (!myStartProfilingStatus.equals(Trace.TraceStartStatus.Status.SUCCESS)) {
      status.setErrorMessage("StartProfilingApp error");
    }
    else {
      myProfilerType = TraceType.from(request.getConfiguration());

      myStartedTraceInfo = Trace.TraceInfo.newBuilder()
        .setTraceId(myTraceId)
        .setFromTimestamp(myTraceId)
        .setToTimestamp(-1)
        .setConfiguration(request.getConfiguration())
        .setStartStatus(status.build())
        .build();
    }
    myStartStopCapturingSession = request.getSession();

    response.setStatus(status.build());
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopProfilingApp(CpuProfiler.CpuProfilingAppStopRequest request,
                               StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> responseObserver) {
    CpuProfiler.CpuProfilingAppStopResponse.Builder response = CpuProfiler.CpuProfilingAppStopResponse.newBuilder();
    Trace.TraceStopStatus.Builder status = Trace.TraceStopStatus.newBuilder();
    status.setStatus(myStopProfilingStatus);
    status.setStoppingDurationNs(TimeUnit.MILLISECONDS.toNanos(FAKE_STOPPING_DURATION_MS));
    if (!myStopProfilingStatus.equals(Trace.TraceStopStatus.Status.SUCCESS)) {
      status.setErrorMessage("StopProfilingApp error");
    }
    myStartStopCapturingSession = request.getSession();
    response.setTraceId(myStartedTraceInfo.getTraceId());
    addTraceInfo(myStartedTraceInfo.toBuilder()
                   .setToTimestamp(myStartedTraceInfo.getFromTimestamp() + myTraceDurationNs)
                   .setStopStatus(status.build())
                   .build());
    myStartedTraceInfo = Trace.TraceInfo.getDefaultInstance();

    response.setStatus(status.build());
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public TraceType getTraceType() {
    return myProfilerType;
  }

  public void setTraceType(TraceType profilerType) {
    myProfilerType = profilerType;
  }

  /**
   * Sets the id which will be used as the trace id for the next StartProfilingApp/StopProfilingApp calls.
   */
  public void setTraceId(long id) {
    myTraceId = id;
  }

  /**
   * Sets the duration which should be used to compute the end timestamp for the next completed capture.
   */
  public void setTraceDurationNs(long traceDurationNs) {
    myTraceDurationNs = traceDurationNs;
  }

  public void setStartProfilingStatus(Trace.TraceStartStatus.Status status) {
    myStartProfilingStatus = status;
  }

  public void setStopProfilingStatus(Trace.TraceStopStatus.Status status) {
    myStopProfilingStatus = status;
  }

  @Override
  public void startMonitoringApp(CpuProfiler.CpuStartRequest request, StreamObserver<CpuProfiler.CpuStartResponse> responseObserver) {
    CpuProfiler.CpuStartResponse.Builder response = CpuProfiler.CpuStartResponse.newBuilder();
    mySession = request.getSession();

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(CpuProfiler.CpuStopRequest request, StreamObserver<CpuProfiler.CpuStopResponse> responseObserver) {
    CpuProfiler.CpuStopResponse.Builder response = CpuProfiler.CpuStopResponse.newBuilder();
    mySession = request.getSession();

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public Common.Session getSession() {
    return mySession;
  }

  public Common.Session getStartStopCapturingSession() {
    return myStartStopCapturingSession;
  }

  @Override
  public void getTraceInfo(CpuProfiler.GetTraceInfoRequest request, StreamObserver<CpuProfiler.GetTraceInfoResponse> responseObserver) {
    CpuProfiler.GetTraceInfoResponse.Builder response = CpuProfiler.GetTraceInfoResponse.newBuilder();
    response.addAllTraceInfo(myTraceInfoMap.values());
    if (!Trace.TraceInfo.getDefaultInstance().equals(myStartedTraceInfo)) {
      response.addTraceInfo(myStartedTraceInfo);
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getData(CpuProfiler.CpuDataRequest request, StreamObserver<CpuProfiler.CpuDataResponse> responseObserver) {
    CpuProfiler.CpuDataResponse.Builder response = CpuProfiler.CpuDataResponse.newBuilder();
    if (!myEmptyUsageData) {
      // Add first usage data
      Cpu.CpuUsageData.Builder cpuUsageData = Cpu.CpuUsageData.newBuilder();
      cpuUsageData.setElapsedTimeInMillisec(0);
      cpuUsageData.setSystemCpuTimeInMillisec(0);
      cpuUsageData.setAppCpuTimeInMillisec(0);
      cpuUsageData.setEndTimestamp(0).build();
      response.addData(cpuUsageData);

      // Add second usage data.
      cpuUsageData = Cpu.CpuUsageData.newBuilder();
      cpuUsageData.setElapsedTimeInMillisec(TOTAL_ELAPSED_TIME);
      cpuUsageData.setSystemCpuTimeInMillisec(mySystemTimeMs);
      cpuUsageData.setAppCpuTimeInMillisec(myAppTimeMs);
      cpuUsageData.setEndTimestamp(0).build();
      response.addData(cpuUsageData);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public void setAppTimeMs(int appTimeMs) {
    myAppTimeMs = appTimeMs;
  }

  public void setSystemTimeMs(int systemTimeMs) {
    mySystemTimeMs = systemTimeMs;
  }

  public void setEmptyUsageData(boolean emptyUsageData) {
    myEmptyUsageData = emptyUsageData;
  }

  public void addThreads(int tid, String name, List<CpuProfiler.GetThreadsResponse.ThreadActivity> threads) {
    myThreads.add(newThread(tid, name, threads));
  }

  public void addTraceInfo(Trace.TraceInfo info) {
    myTraceInfoMap.put(info.getTraceId(), info);
  }

  public void clearTraceInfo() {
    myTraceInfoMap.clear();
  }

  @Override
  public void getThreads(CpuProfiler.GetThreadsRequest request, StreamObserver<CpuProfiler.GetThreadsResponse> responseObserver) {
    CpuProfiler.GetThreadsResponse.Builder response = CpuProfiler.GetThreadsResponse.newBuilder();
    List<CpuProfiler.GetThreadsResponse.Thread> threads = new ArrayList<>();
    threads.addAll(buildThreads(request.getStartTimestamp(), request.getEndTimestamp()));
    threads.addAll(myThreads);
    response.addAllThreads(threads);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  /**
   * Create two threads that overlap for certain amount of time.
   * They are referred as thread1 and thread2 in the comments present in the tests.
   * <p>
   * Thread1 is alive from 1s to 8s, while thread2 is alive from 6s to 15s.
   */
  private static List<CpuProfiler.GetThreadsResponse.Thread> buildThreads(long start, long end) {
    List<CpuProfiler.GetThreadsResponse.Thread> threads = new ArrayList<>();

    Range requestRange = new Range(start, end);

    Range thread1Range = new Range(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(8));
    if (thread1Range.intersectsWith(requestRange)) {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread1 = new ArrayList<>();
      activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(1), Cpu.CpuThreadData.State.RUNNING));
      activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(8), Cpu.CpuThreadData.State.DEAD));
      threads.add(newThread(1, "Thread 1", activitiesThread1));
    }

    Range thread2Range = new Range(TimeUnit.SECONDS.toNanos(6), TimeUnit.SECONDS.toNanos(15));
    if (thread2Range.intersectsWith(requestRange)) {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread2 = new ArrayList<>();
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(6), Cpu.CpuThreadData.State.RUNNING));
      // Make sure we handle an unexpected state.
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(8), Cpu.CpuThreadData.State.STOPPED));
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(10), Cpu.CpuThreadData.State.SLEEPING));
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(12), Cpu.CpuThreadData.State.WAITING));
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(15), Cpu.CpuThreadData.State.DEAD));
      threads.add(newThread(2, "Thread 2", activitiesThread2));
    }

    return threads;
  }

  private static CpuProfiler.GetThreadsResponse.ThreadActivity newActivity(long timestampNs, Cpu.CpuThreadData.State state) {
    CpuProfiler.GetThreadsResponse.ThreadActivity.Builder activity = CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder();
    activity.setNewState(state);
    activity.setTimestamp(timestampNs);
    return activity.build();
  }

  private static CpuProfiler.GetThreadsResponse.Thread newThread(
    int tid, String name, List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities) {
    CpuProfiler.GetThreadsResponse.Thread.Builder thread = CpuProfiler.GetThreadsResponse.Thread.newBuilder();
    thread.setTid(tid);
    thread.setName(name);
    thread.addAllActivities(activities);
    return thread.build();
  }
}


