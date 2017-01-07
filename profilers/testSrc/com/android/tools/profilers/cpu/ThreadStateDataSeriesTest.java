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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.IdeProfilerServicesStub;
import com.android.tools.profilers.StudioProfilers;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.util.containers.ImmutableList;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ThreadStateDataSeriesTest {

  private static final int FAKE_PID = 3039;

  private final FakeCpuService myService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("ThreadStateDataSeriesTest", myService);

  private CpuThreadsModel myThreadsModel;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub());
    myThreadsModel = new CpuThreadsModel(new Range(), new CpuProfilerStage(profilers), FAKE_PID);
  }

  @Test
  public void processIdMatchModelId() {
    // Create a series with arbitrary range and tid
    ThreadStateDataSeries series = createThreadSeries(new Range(), 10);

    assertEquals(FAKE_PID, series.getProcessId());
  }

  @Test
  public void emptyRange() {
    // Create a series with empty range and arbitrary tid
    ThreadStateDataSeries series = createThreadSeries(new Range(), 10);
    ImmutableList<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(new Range());
    assertNotNull(dataSeries);
    // No data within given range
    assertTrue(dataSeries.isEmpty());
  }

  @Test
  public void nonEmptyRange() {
    // Range of the threads from FakeCpuService#buildThreads
    Range range = new Range(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(15));
    // Create a series with the range that contains both thread1 and thread2 and thread2 tid
    ThreadStateDataSeries series = createThreadSeries(range, 2);
    // We don't want to get thread information from the trace
    myService.setAddTraceInfo(false);
    ImmutableList<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(range);
    assertNotNull(dataSeries);
    assertEquals(4, dataSeries.size()); // thread2 state changes are RUNNING, STOPPED, SLEEPING and DEAD

    assertEquals(CpuProfilerStage.ThreadState.RUNNING, dataSeries.get(0).value);
    // Any state different than RUNNING, SLEEPING or DEAD (expected states) is mapped to UNKNOWN
    assertEquals(CpuProfilerStage.ThreadState.UNKNOWN, dataSeries.get(1).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING, dataSeries.get(2).value);
    assertEquals(CpuProfilerStage.ThreadState.DEAD, dataSeries.get(3).value);
  }

  @Test
  public void nonEmptyRangeWithFakeTraceFailureStatus() throws IOException {
    CpuCapture capture = myService.parseTraceFile();
    assertNotNull(capture);
    // Create a series with trace file's main thread tid and the capture range
    ThreadStateDataSeries series = createThreadSeries(capture.getRange(), FakeCpuService.TRACE_TID);
    // We want the data series to consider the trace.
    myService.setAddTraceInfo(true);
    myService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.FAILURE);

    ImmutableList<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(capture.getRange());
    assertNotNull(dataSeries);

    // Even if getTrace() grpc call returns a valid trace, the response status should be SUCCESS
    // in order to the data series get the "captured" values from the trace. With a FAILURE status,
    // we should expect the series to have the original values of the thread status (i.e. RUNNING and SLEEPING)
    assertEquals(2, dataSeries.size());
    assertEquals(CpuProfilerStage.ThreadState.RUNNING, dataSeries.get(0).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING, dataSeries.get(1).value);
  }

  @Test
  public void nonEmptyRangeWithFakeTraceSuccessStatus() throws IOException {
    CpuCapture capture = myService.parseTraceFile();
    assertNotNull(capture);
    // Create a series with trace file's main thread tid and the capture range
    ThreadStateDataSeries series = createThreadSeries(capture.getRange(), FakeCpuService.TRACE_TID);
    // We want the data series to consider the trace.
    myService.setAddTraceInfo(true);
    myService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    ImmutableList<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(capture.getRange());
    assertNotNull(dataSeries);

    // We expect the portions of the thread activities that are within the capture range to be duplicated with a "_CAPTURED" suffix.
    assertEquals(4, dataSeries.size());
    assertEquals(CpuProfilerStage.ThreadState.RUNNING, dataSeries.get(0).value);
    assertEquals(CpuProfilerStage.ThreadState.RUNNING_CAPTURED, dataSeries.get(1).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING_CAPTURED, dataSeries.get(2).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING, dataSeries.get(3).value);
  }

  private ThreadStateDataSeries createThreadSeries(Range range, int tid) {
    CpuThreadsModel.RangedCpuThread rangedThread = myThreadsModel.new RangedCpuThread(range, tid, "any name");
    List<RangedSeries<CpuProfilerStage.ThreadState>> seriesList = rangedThread.getModel().getSeries();
    // A ThreadStateDataSeries is added to the model on RangedCpuThread constructor
    assertFalse(seriesList.isEmpty());
    DataSeries<CpuProfilerStage.ThreadState> dataSeries = seriesList.get(0).getDataSeries();
    assertTrue(dataSeries instanceof ThreadStateDataSeries);
    ThreadStateDataSeries threadSeries = (ThreadStateDataSeries)dataSeries;
    assertNotNull(threadSeries);
    return threadSeries;
  }

  private static class FakeCpuService extends CpuServiceGrpc.CpuServiceImplBase {

    private static final int FAKE_TRACE_ID = 28;

    /**
     * Real tid of the main thread of the trace.
     */
    private static final int TRACE_TID = 516;

    @Nullable
    private ByteString myTrace;

    private boolean myAddTraceInfo;

    private CpuCapture myCapture;

    private CpuProfiler.GetTraceResponse.Status myGetTraceResponseStatus;

    @Override
    public void getThreads(CpuProfiler.GetThreadsRequest request, StreamObserver<CpuProfiler.GetThreadsResponse> responseObserver) {
      CpuProfiler.GetThreadsResponse.Builder response = CpuProfiler.GetThreadsResponse.newBuilder();
      if (myAddTraceInfo) {
        response.addAllThreads(buildTraceThreads());
      } else {
        response.addAllThreads(buildThreads(request.getStartTimestamp(), request.getEndTimestamp()));
      }

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTrace(CpuProfiler.GetTraceRequest request, StreamObserver<CpuProfiler.GetTraceResponse> responseObserver) {
      CpuProfiler.GetTraceResponse.Builder response = CpuProfiler.GetTraceResponse.newBuilder();
      response.setStatus(myGetTraceResponseStatus);
      if (myTrace != null) {
        response.setData(myTrace);
      }

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTraceInfo(CpuProfiler.GetTraceInfoRequest request, StreamObserver<CpuProfiler.GetTraceInfoResponse> responseObserver) {
      CpuProfiler.GetTraceInfoResponse.Builder response = CpuProfiler.GetTraceInfoResponse.newBuilder();
      if (myAddTraceInfo) {
        response.addTraceInfo(CpuProfiler.TraceInfo.newBuilder().setTraceId(FAKE_TRACE_ID));
      }

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    /**
     * Create two threads that overlap for certain amount of time.
     * They are referred as thread1 and thread2 in the comments present in the tests.
     *
     * Thread1 is alive from 1s to 8s, while thread2 is alive from 6s to 15s.
     */
    private static List<CpuProfiler.GetThreadsResponse.Thread> buildThreads(long start, long end) {
      List<CpuProfiler.GetThreadsResponse.Thread> threads = new ArrayList<>();

      Range requestRange = new Range(start, end);

      Range thread1Range = new Range(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(8));
      if (!thread1Range.getIntersection(requestRange).isEmpty()) {
        List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread1 = new ArrayList<>();
        activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(1), CpuProfiler.GetThreadsResponse.State.RUNNING));
        activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(8), CpuProfiler.GetThreadsResponse.State.DEAD));
        threads.add(newThread(1, "Thread 1", activitiesThread1));
      }

      Range thread2Range = new Range(TimeUnit.SECONDS.toNanos(6), TimeUnit.SECONDS.toNanos(15));
      if (!thread2Range.getIntersection(requestRange).isEmpty()) {
        List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread2 = new ArrayList<>();
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(6), CpuProfiler.GetThreadsResponse.State.RUNNING));
        // Make sure we handle an unexpected state.
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(8), CpuProfiler.GetThreadsResponse.State.STOPPED));
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(10), CpuProfiler.GetThreadsResponse.State.SLEEPING));
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(15), CpuProfiler.GetThreadsResponse.State.DEAD));
        threads.add(newThread(2, "Thread 2", activitiesThread2));
      }

      return threads;
    }

    /**
     * Create one thread with two activities: RUNNING (2 seconds before capture start) and SLEEPING (2 seconds after capture end).
     */
    private List<CpuProfiler.GetThreadsResponse.Thread> buildTraceThreads() {
      Range range = myCapture.getRange();
      long rangeMid = (long)(range.getMax() + range.getMin()) / 2;
      long buffer = TimeUnit.SECONDS.toMicros(2);

      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = new ArrayList<>();
      activities.add(
        newActivity(TimeUnit.MICROSECONDS.toNanos((long)range.getMin() - buffer), CpuProfiler.GetThreadsResponse.State.RUNNING));
      activities.add(newActivity(TimeUnit.MICROSECONDS.toNanos(rangeMid), CpuProfiler.GetThreadsResponse.State.SLEEPING));

      return Collections.singletonList(newThread(TRACE_TID, "Trace tid", activities));
    }

    private static CpuProfiler.GetThreadsResponse.ThreadActivity newActivity(long timestampNs, CpuProfiler.GetThreadsResponse.State state) {
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

    private void setAddTraceInfo(boolean addTraceInfo) {
      myAddTraceInfo = addTraceInfo;
    }

    private void setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status getTraceResponseStatus) {
      myGetTraceResponseStatus = getTraceResponseStatus;
    }

    private CpuCapture parseTraceFile() throws IOException {
      if (myTrace == null) {
        myTrace = CpuCaptureTest.readValidTrace();
      }
      if (myCapture == null) {
        myCapture = new CpuCapture(myTrace);
      }
      return myCapture;
    }
  }
}
