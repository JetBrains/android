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

import com.android.tools.adtui.model.*;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ThreadStateDataSeriesTest {

  private static final int FAKE_PID = 3039;

  private final FakeCpuService myService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("ThreadStateDataSeriesTest", myService, new FakeProfilerService());

  private CpuThreadsModel myThreadsModel;

  @Before
  public void setUp() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myThreadsModel = new CpuThreadsModel(new Range(), new CpuProfilerStage(profilers), FAKE_PID, ProfilersTestData.SESSION_DATA);
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
    List<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(new Range());
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
    myService.setValidTrace(false);
    List<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(range);
    assertNotNull(dataSeries);
    // thread2 state changes are RUNNING, STOPPED, SLEEPING, WAITING and DEAD
    assertEquals(5, dataSeries.size());

    assertEquals(CpuProfilerStage.ThreadState.RUNNING, dataSeries.get(0).value);
    // Any state different than RUNNING, SLEEPING, WAITING or DEAD (expected states) is mapped to UNKNOWN
    assertEquals(CpuProfilerStage.ThreadState.UNKNOWN, dataSeries.get(1).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING, dataSeries.get(2).value);
    assertEquals(CpuProfilerStage.ThreadState.WAITING, dataSeries.get(3).value);
    assertEquals(CpuProfilerStage.ThreadState.DEAD, dataSeries.get(4).value);
  }

  @Test
  public void nonEmptyRangeWithFakeTraceSuccessStatus() throws IOException, ExecutionException, InterruptedException {
    CpuCapture capture = myService.parseTraceFile();
    assertNotNull(capture);
    // Create a series with trace file's main thread tid and the capture range
    ThreadStateDataSeries series = createThreadSeries(capture.getRange(), FakeCpuService.TRACE_TID);
    // We want the data series to consider the trace.
    myService.setValidTrace(true);
    // Start the capture 2 seconds before the first thread activity
    myService.setTraceThreadActivityBuffer(-2);
    myService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    List<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(capture.getRange());
    assertNotNull(dataSeries);

    // We expect the portions of the thread activities that are within the capture range to be duplicated with a "_CAPTURED" suffix.
    assertEquals(4, dataSeries.size());
    assertEquals(CpuProfilerStage.ThreadState.RUNNING, dataSeries.get(0).value);
    assertEquals(CpuProfilerStage.ThreadState.RUNNING_CAPTURED, dataSeries.get(1).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING_CAPTURED, dataSeries.get(2).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING, dataSeries.get(3).value);
  }

  @Test
  public void captureBeforeFirstActivity() throws IOException, ExecutionException, InterruptedException {
    CpuCapture capture = myService.parseTraceFile();
    assertNotNull(capture);
    // Create a series with trace file's main thread tid and the capture range
    ThreadStateDataSeries series = createThreadSeries(capture.getRange(), FakeCpuService.TRACE_TID);
    // We want the data series to consider the trace.
    myService.setValidTrace(true);
    // Start the capture 1 second after the first thread activity
    myService.setTraceThreadActivityBuffer(1);
    myService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    List<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(capture.getRange());
    assertNotNull(dataSeries);

    // We expect the portions of the thread activities that are within the capture range to be duplicated with a "_CAPTURED" suffix.
    // The first activity happens inside the capture, therefore it has only the "_CAPTURED" state.
    assertEquals(3, dataSeries.size());
    assertEquals(CpuProfilerStage.ThreadState.RUNNING_CAPTURED, dataSeries.get(0).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING_CAPTURED, dataSeries.get(1).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING, dataSeries.get(2).value);
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
}
