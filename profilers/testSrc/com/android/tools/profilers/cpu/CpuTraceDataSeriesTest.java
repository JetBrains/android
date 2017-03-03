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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.StudioProfilers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class CpuTraceDataSeriesTest {

  private final FakeCpuService myService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuTraceDataSeriesTest", myService, new FakeProfilerService());

  private CpuProfilerStage.CpuTraceDataSeries mySeries;

  @Before
  public void setUp() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    CpuProfilerStage stage = new CpuProfilerStage(profilers);
    mySeries = stage.getCpuTraceDataSeries();
  }

  @Test
  public void emptySeries() {
    Range maxRange = new Range(-Double.MAX_VALUE, -Double.MAX_VALUE);
    myService.setValidTrace(false);
    assertTrue(mySeries.getDataForXRange(maxRange).isEmpty());
  }

  @Test
  public void validTraceFailureStatus() throws IOException {
    Range maxRange = new Range(-Double.MAX_VALUE, -Double.MAX_VALUE);
    myService.setValidTrace(true);
    myService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.FAILURE);
    myService.parseTraceFile();
    // Even if a valid trace is returned from GetTrace, a SUCCESS status is required
    // for the trace to be added to the series.
    assertTrue(mySeries.getDataForXRange(maxRange).isEmpty());
  }

  @Test
  public void validTraceSuccessStatus() throws IOException {
    Range maxRange = new Range(-Double.MAX_VALUE, -Double.MAX_VALUE);
    myService.setValidTrace(true);
    myService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    CpuCapture expectedCapture = myService.parseTraceFile();

    List<SeriesData<CpuCapture>> seriesData = mySeries.getDataForXRange(maxRange);
    assertEquals(1, seriesData.size());
    SeriesData data = seriesData.get(0);
    assertNotNull(data);
    assertEquals((long)expectedCapture.getRange().getMin(), data.x);

    assertTrue(data.value instanceof CpuCapture);
    CpuCapture capture = (CpuCapture)data.value;

    // As CpuCapture doesn't have an equals method, compare its values.
    // Check main thread is the same
    assertEquals(expectedCapture.getMainThreadId(), capture.getMainThreadId());
    // Check that capture has the same threads of expected capture
    assertFalse(capture.getThreads().isEmpty());
    assertEquals(expectedCapture.getThreads().size(), capture.getThreads().size());
    for (ThreadInfo thread : expectedCapture.getThreads()) {
      assertTrue(capture.containsThread(thread.getId()));
    }
    // Verify duration is also equal
    assertEquals(expectedCapture.getDuration(), capture.getDuration());
    // As Range also doesn't have an equals method, compare max and min
    assertNotNull(capture.getRange());
    assertNotNull(expectedCapture.getRange());
    assertEquals(expectedCapture.getRange().getMin(), capture.getRange().getMin(), 0);
    assertEquals(expectedCapture.getRange().getMax(), capture.getRange().getMax(), 0);
  }
}
