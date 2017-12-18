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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.ProfilersTestData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static org.junit.Assert.assertEquals;

public class CpuThreadCountDataSeriesTest {

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuThreadCountDataSeriesTest", new FakeCpuService());

  private CpuThreadCountDataSeries myDataSeries;

  @Before
  public void setUp() {
    myDataSeries = new CpuThreadCountDataSeries(myGrpcChannel.getClient().getCpuClient(), ProfilersTestData.SESSION_DATA);
  }

  @Test
  public void noAliveThreadsInRange() {
    double rangeMin = TimeUnit.SECONDS.toMicros(20);
    double rangeMax = TimeUnit.SECONDS.toMicros(25);
    Range range = new Range(rangeMin, rangeMax);
    List<SeriesData<Long>> seriesDataList = myDataSeries.getDataForXRange(range);
    // When no threads are find within the requested range, we add the threads count (0)
    // to both range's min and max
    assertEquals(2, seriesDataList.size());
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(rangeMin, seriesData.x, 0);
    assertEquals(0, (long)seriesData.value);

    seriesData = seriesDataList.get(1);
    assertNotNull(seriesData);
    assertEquals(rangeMax, seriesData.x, 0);
    assertEquals(0, (long)seriesData.value);
  }

  @Test
  public void oneAliveThreadInRange() {
    Range range = new Range(TimeUnit.SECONDS.toMicros(10), TimeUnit.SECONDS.toMicros(11));
    List<SeriesData<Long>> seriesDataList = myDataSeries.getDataForXRange(range);
    assertEquals(2, seriesDataList.size());

    // Threads count by thread2 state change to RUNNING
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(6), seriesData.x, 0);
    assertEquals(1, (long)seriesData.value);

    // Threads count by thread2 state change to DEAD
    seriesData = seriesDataList.get(1);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(15), seriesData.x, 0);
    assertEquals(0, (long)seriesData.value);
  }

  @Test
  public void multipleAliveThreadInRange() {
    Range range = new Range(TimeUnit.SECONDS.toMicros(6), TimeUnit.SECONDS.toMicros(10));
    List<SeriesData<Long>> seriesDataList = myDataSeries.getDataForXRange(range);
    assertEquals(4, seriesDataList.size());

    // Threads count by thread1 state change to RUNNING
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(1), seriesData.x, 0);
    assertEquals(1, (long)seriesData.value); // Only thread1 is alive

    // Threads count by thread2 state change to RUNNING
    seriesData = seriesDataList.get(1);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(6), seriesData.x, 0);
    assertEquals(2, (long)seriesData.value); // Both threads are alive

    // Threads count by thread1 state change to DEAD
    seriesData = seriesDataList.get(2);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(8), seriesData.x, 0);
    assertEquals(1, (long)seriesData.value); // Only thread2 remains alive

    // Threads count by thread2 state change to DEAD
    seriesData = seriesDataList.get(3);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(15), seriesData.x, 0);
    assertEquals(0, (long)seriesData.value); // Both threads are dead now
  }

  @Test
  public void threadDiesBeforeRangeMax() {
    Range range = new Range(TimeUnit.SECONDS.toMicros(10), TimeUnit.SECONDS.toMicros(20));
    List<SeriesData<Long>> seriesDataList = myDataSeries.getDataForXRange(range);
    assertEquals(3, seriesDataList.size());

    // Threads count by thread2 state change to RUNNING
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(6), seriesData.x, 0);
    assertEquals(1, (long)seriesData.value); // thread2 is alive

    // Threads count by thread2 state change to DEAD
    seriesData = seriesDataList.get(1);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(15), seriesData.x, 0);
    assertEquals(0, (long)seriesData.value); // thread2 is dead now

    // Threads count by range.getMax(). This value is added when range.getMax()
    // is greater than the timestamp of the thread state change to DEAD
    seriesData = seriesDataList.get(2);
    assertNotNull(seriesData);
    assertEquals(range.getMax(), seriesData.x, 0);
    assertEquals(0, (long)seriesData.value); // thread2 is still dead
  }
}
