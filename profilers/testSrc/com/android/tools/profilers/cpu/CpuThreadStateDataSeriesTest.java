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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.network.FakeNetworkService;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
  private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();
  private final FakeCpuService myService = new FakeCpuService();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  private CpuProfilerStage myProfilerStage;
  private boolean myIsUnifiedPipeline;

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myService, myTransportService, new FakeProfilerService(myTimer),
                        new FakeMemoryService(), new FakeEventService(), FakeNetworkService.newBuilder().build());

  public CpuThreadStateDataSeriesTest(boolean isUnifiedPipeline) {
    myIsUnifiedPipeline = isUnifiedPipeline;
  }

  @Before
  public void setUp() {
    if (myIsUnifiedPipeline) {
      ProfilersTestData.populateThreadData(myTransportService, ProfilersTestData.SESSION_DATA.getStreamId());
    }
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getName()), myIdeProfilerServices, myTimer);
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(FAKE_DEVICE, FAKE_PROCESS);
    myProfilerStage = new CpuProfilerStage(profilers);
    myProfilerStage.enter();
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
  public void nonEmptyRangeWithFakeTraceSuccessStatus() throws Exception {
    // CPU recording is not supported in new pipeline yet.
    Assume.assumeFalse(myIsUnifiedPipeline);

    // Generate and select a capture in the stage
    CpuProfilerTestUtils.captureSuccessfully(myProfilerStage, myService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    CpuCapture capture = myProfilerStage.getCapture();
    assertNotNull(capture);
    int tid = capture.getMainThreadId();

    // Create a series with trace file's main thread tid and the capture range
    DataSeries<CpuProfilerStage.ThreadState> series = createThreadSeries(tid);

    // Create the thread activities to be 2 seconds before the capture start and 2 seconds before the capture finishes
    myService.addThreads(tid, "main", Arrays.asList(
      CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
        .setTimestamp(TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMin()) - TimeUnit.SECONDS.toNanos(2))
        .setNewState(Cpu.CpuThreadData.State.RUNNING).build(),
      CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
        .setTimestamp(TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMax()) - TimeUnit.SECONDS.toNanos(2))
        .setNewState(Cpu.CpuThreadData.State.SLEEPING).build()));
    List<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(new Range(Long.MIN_VALUE, Long.MAX_VALUE));
    assertNotNull(dataSeries);
    // We expect the portions of the thread activities that are within the capture range to be duplicated with a "_CAPTURED" suffix.
    assertEquals(4, dataSeries.size());
    assertEquals(CpuProfilerStage.ThreadState.RUNNING, dataSeries.get(0).value);
    assertEquals(CpuProfilerStage.ThreadState.RUNNING_CAPTURED, dataSeries.get(1).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING_CAPTURED, dataSeries.get(2).value);
    assertEquals(CpuProfilerStage.ThreadState.SLEEPING, dataSeries.get(3).value);
  }

  @Test
  public void captureBeforeFirstActivity() throws Exception {
    // CPU recording is not supported in new pipeline yet.
    Assume.assumeFalse(myIsUnifiedPipeline);

    // Generate and select a capture in the stage
    CpuProfilerTestUtils.captureSuccessfully(myProfilerStage, myService, myTransportService, CpuProfilerTestUtils.readValidTrace());
    CpuCapture capture = myProfilerStage.getCapture();
    assertNotNull(capture);
    int tid = capture.getMainThreadId();

    // Create a series with trace file's main thread tid and the capture range
    DataSeries<CpuProfilerStage.ThreadState> series = createThreadSeries(tid);

    // Create the thread activities to be 1 second after the capture
    myService.addThreads(tid, "main", Arrays.asList(
      CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
        .setTimestamp(TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMin()) + TimeUnit.SECONDS.toNanos(1))
        .setNewState(Cpu.CpuThreadData.State.RUNNING).build(),
      CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
        .setTimestamp(TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMin()) + TimeUnit.SECONDS.toNanos(2))
        .setNewState(Cpu.CpuThreadData.State.SLEEPING).build()));

    List<SeriesData<CpuProfilerStage.ThreadState>> dataSeries = series.getDataForXRange(new Range(Long.MIN_VALUE, Long.MAX_VALUE));
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
                                          tid,
                                          myProfilerStage.getCapture())
           : new LegacyCpuThreadStateDataSeries(myProfilerStage.getStudioProfilers().getClient().getCpuClient(),
                                                ProfilersTestData.SESSION_DATA,
                                                tid,
                                                myProfilerStage.getCapture());
  }
}
