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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CpuThreadStateDataSeriesTest {
  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  private StudioProfilers myStudioProfilers;

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuThreadStateDataSeriesTestChannel", myTransportService);

  @Before
  public void setUp() {
    ProfilersTestData.populateThreadData(myTransportService, ProfilersTestData.SESSION_DATA.getStreamId());
    myStudioProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), new FakeIdeProfilerServices(), myTimer);
  }

  @Test
  public void emptyRange() {
    // Create a series with empty range and arbitrary tid
    DataSeries<ThreadState> series = createThreadSeries(10);
    List<SeriesData<ThreadState>> dataSeries = series.getDataForRange(new Range());
    assertNotNull(dataSeries);
    // No data within given range
    assertTrue(dataSeries.isEmpty());
  }

  @Test
  public void nonEmptyRange() {
    // Range of the threads from FakeCpuService#buildThreads
    Range range = new Range(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(15));
    // Create a series with the range that contains both thread1 and thread2 and thread2 tid
    DataSeries<ThreadState> series = createThreadSeries(2);
    List<SeriesData<ThreadState>> dataSeries = series.getDataForRange(range);
    assertNotNull(dataSeries);
    // thread2 state changes are RUNNING, STOPPED, SLEEPING, WAITING and DEAD
    assertEquals(5, dataSeries.size());

    assertEquals(ThreadState.RUNNING, dataSeries.get(0).value);
    // Any state different than RUNNING, SLEEPING, WAITING or DEAD (expected states) is mapped to UNKNOWN
    assertEquals(ThreadState.UNKNOWN, dataSeries.get(1).value);
    assertEquals(ThreadState.SLEEPING, dataSeries.get(2).value);
    assertEquals(ThreadState.WAITING, dataSeries.get(3).value);
    assertEquals(ThreadState.DEAD, dataSeries.get(4).value);
  }

  private DataSeries<ThreadState> createThreadSeries(int tid) {
    return new CpuThreadStateDataSeries(myStudioProfilers.getClient().getTransportClient(),
                                        ProfilersTestData.SESSION_DATA.getStreamId(),
                                        ProfilersTestData.SESSION_DATA.getPid(),
                                        tid,
                                        null);
  }
}
