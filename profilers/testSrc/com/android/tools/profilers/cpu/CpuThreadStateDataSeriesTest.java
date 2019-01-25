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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeTransportService;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CpuThreadStateDataSeriesTest {
  @Parameterized.Parameters(name = "isUnifiedPipeline={0}")
  public static Collection<Boolean> useNewEventPipelineParameter() {
    return Arrays.asList(false, true);
  }

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeCpuService myService = new FakeCpuService();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  private CpuProfilerStage myProfilerStage;
  private boolean myIsUnifiedPipeline;

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuThreadStateDataSeriesTest", myService, myTransportService);

  public CpuThreadStateDataSeriesTest(boolean isUnifiedPipeline) {
    myIsUnifiedPipeline = isUnifiedPipeline;
  }

  @Before
  public void setUp() {
    if (myIsUnifiedPipeline) {
      myTransportService.populateThreads(ProfilersTestData.SESSION_DATA.getStreamId());
    }
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), myTimer);
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilerStage = new CpuProfilerStage(profilers);
  }

  @Test
  public void emptyRange() {
    // Create a series with empty range and arbitrary tid
    DataSeries<CpuProfilerStage.ThreadState> series = createThreadSeries(10);
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
    DataSeries<CpuProfilerStage.ThreadState> series = createThreadSeries(2);
    if (!myIsUnifiedPipeline) {
      // We don't want to get thread information from the trace
      myService.setValidTrace(false);
    }
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
    // CPU recording is not supported in new pipeline yet.
    Assume.assumeFalse(myIsUnifiedPipeline);

    CpuCapture capture = myService.parseTraceFile();
    assertNotNull(capture);
    // Create a series with trace file's main thread tid and the capture range
    DataSeries<CpuProfilerStage.ThreadState> series = createThreadSeries(FakeCpuService.TRACE_TID);
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
    // CPU recording is not supported in new pipeline yet.
    Assume.assumeFalse(myIsUnifiedPipeline);

    CpuCapture capture = myService.parseTraceFile();
    assertNotNull(capture);
    // Create a series with trace file's main thread tid and the capture range
    DataSeries<CpuProfilerStage.ThreadState> series = createThreadSeries(FakeCpuService.TRACE_TID);
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

  private DataSeries<CpuProfilerStage.ThreadState> createThreadSeries(int tid) {
    return myIsUnifiedPipeline
           ? new CpuThreadStateDataSeries(myProfilerStage.getStudioProfilers().getClient().getTransportClient(),
                                          ProfilersTestData.SESSION_DATA.getStreamId(),
                                          ProfilersTestData.SESSION_DATA.getPid(),
                                          tid)
           : new LegacyCpuThreadStateDataSeries(myProfilerStage.getStudioProfilers().getClient().getCpuClient(),
                                                ProfilersTestData.SESSION_DATA, tid);
  }
}
