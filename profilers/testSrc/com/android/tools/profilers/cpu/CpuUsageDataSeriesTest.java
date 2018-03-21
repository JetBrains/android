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
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.ProfilersTestData;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CpuUsageDataSeriesTest {

  private static final Range ANY_RANGE = new Range(0, 100);

  private final FakeCpuService myService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuUsageDataSeriesTest", myService);

  private CpuUsageDataSeries mySeries;

  @Test
  public void thisProcessCpuUsage() {
    mySeries = new CpuUsageDataSeries(myGrpcChannel.getClient().getCpuClient(), false, ProfilersTestData.SESSION_DATA);
    int systemTime = (int)(0.6 * FakeCpuService.TOTAL_ELAPSED_TIME);
    int appTime = (int)(0.4 * FakeCpuService.TOTAL_ELAPSED_TIME);
    myService.setSystemTimeMs(systemTime);
    myService.setAppTimeMs(appTime);
    List<SeriesData<Long>> seriesData = mySeries.getDataForXRange(ANY_RANGE);
    assertEquals(1, seriesData.size());
    SeriesData<Long> appUsageData = seriesData.get(0);
    assertNotNull(appUsageData);
    assertEquals(40, (long)appUsageData.value); // 40% of total elapsed time

    systemTime = (int)(0.6 * FakeCpuService.TOTAL_ELAPSED_TIME);
    appTime = (int)(0.8 * FakeCpuService.TOTAL_ELAPSED_TIME);
    myService.setSystemTimeMs(systemTime);
    myService.setAppTimeMs(appTime);
    seriesData = mySeries.getDataForXRange(ANY_RANGE);
    assertEquals(1, seriesData.size());
    appUsageData = seriesData.get(0);
    assertNotNull(appUsageData);
    // App usage shouldn't be greater than system usage. If that happens, we cap the value to
    // the system total usage.
    assertEquals(systemTime, (long)appUsageData.value);

    appTime = (int)(-0.2 * FakeCpuService.TOTAL_ELAPSED_TIME);
    myService.setAppTimeMs(appTime);
    seriesData = mySeries.getDataForXRange(ANY_RANGE);
    assertEquals(1, seriesData.size());
    appUsageData = seriesData.get(0);
    assertNotNull(appUsageData);
    // App usage shouldn't be less than 0%. If that happens, we cap the value to 0%.
    assertEquals(0, (long)appUsageData.value);
  }

  @Test
  public void otherProcessesCpuUsage() {
    mySeries = new CpuUsageDataSeries(myGrpcChannel.getClient().getCpuClient(), true, ProfilersTestData.SESSION_DATA);
    int systemTime = (int)(0.6 * FakeCpuService.TOTAL_ELAPSED_TIME);
    myService.setSystemTimeMs(systemTime);
    List<SeriesData<Long>> seriesData = mySeries.getDataForXRange(ANY_RANGE);
    assertEquals(1, seriesData.size());
    SeriesData<Long> systemUsageData = seriesData.get(0);
    assertNotNull(systemUsageData);
    assertEquals(60, (long)systemUsageData.value); // 60% of total elapsed time

    systemTime = (int)(1.5 * FakeCpuService.TOTAL_ELAPSED_TIME);
    myService.setSystemTimeMs(systemTime);
    seriesData = mySeries.getDataForXRange(ANY_RANGE);
    assertEquals(1, seriesData.size());
    systemUsageData = seriesData.get(0);
    assertNotNull(systemUsageData);
    // System usage shouldn't be greater than 100%. If that happens, we cap the value to 100%.
    assertEquals(100, (long)systemUsageData.value);

    systemTime = (int)(-0.5 * FakeCpuService.TOTAL_ELAPSED_TIME);
    myService.setSystemTimeMs(systemTime);
    seriesData = mySeries.getDataForXRange(ANY_RANGE);
    assertEquals(1, seriesData.size());
    systemUsageData = seriesData.get(0);
    assertNotNull(systemUsageData);
    // System usage shouldn't be less than 0%. If that happens, we cap the value to 0%.
    assertEquals(0, (long)systemUsageData.value);
  }

  @Test
  public void emptyData() {
    mySeries = new CpuUsageDataSeries(myGrpcChannel.getClient().getCpuClient(), false, ProfilersTestData.SESSION_DATA);
    assertNotNull(mySeries);
    assertFalse(mySeries.getDataForXRange(ANY_RANGE).isEmpty());
    myService.setEmptyUsageData(true);
    assertTrue(mySeries.getDataForXRange(ANY_RANGE).isEmpty());
  }
}
