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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.NullMonitorStage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.FakeGrpcChannel;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.NetworkProfiler.*;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;

public class NetworkMonitorTest {
  private static final ImmutableList<NetworkProfilerData> FAKE_DATA =
    new ImmutableList.Builder<NetworkProfilerData>()
      .add(FakeNetworkService.newSpeedData(0, 1, 2))
      .add(FakeNetworkService.newConnectionData(2, 4))
      .build();

  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("NetworkMonitorTest", FakeNetworkService.newBuilder().setNetworkDataList(FAKE_DATA).build());
  private NetworkMonitor myMonitor;
  private StudioProfilers myProfilers;
  private FakeTimer myTimer;

  @Before
  public void setUp() {
    myTimer = new FakeTimer();
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), myTimer);
    myMonitor = new NetworkMonitor(myProfilers);
    myProfilers.getTimeline().getViewRange().set(0, TimeUnit.SECONDS.toMicros(10));
    myMonitor.enter();
  }

  @Test
  public void getName() {
    assertEquals("NETWORK", myMonitor.getName());
  }

  @Test
  public void getNetworkUsage() {
    List<RangedContinuousSeries> series = myMonitor.getNetworkUsage().getSeries();
    assertEquals(2, series.size());
    assertEquals("Receiving", series.get(0).getName());
    assertEquals(1, series.get(0).getSeries().size());
    assertEquals(0, series.get(0).getSeries().get(0).x);
    assertEquals(2, series.get(0).getSeries().get(0).value.longValue());

    assertEquals("Sending", series.get(1).getName());
    assertEquals(1, series.get(1).getSeries().size());
    assertEquals(0, series.get(1).getSeries().get(0).x);
    assertEquals(1, series.get(1).getSeries().get(0).value.longValue());
  }

  @Test
  public void getTrafficAxis() {
    AxisComponentModel axis = myMonitor.getTrafficAxis();
    assertNotNull(axis);
    assertEquals(myMonitor.getNetworkUsage().getTrafficRange(), axis.getRange());
  }

  @Test
  public void getLegends() {
    NetworkMonitor.NetworkLegends networkLegends = myMonitor.getLegends();
    assertEquals("Receiving", networkLegends.getRxLegend().getName());
    assertEquals("2 B/s", networkLegends.getRxLegend().getValue());
    assertEquals("Sending", networkLegends.getTxLegend().getName());
    assertEquals("1 B/s", networkLegends.getTxLegend().getValue());

    List<Legend> legends = networkLegends.getLegends();
    assertEquals(2, legends.size());
    assertEquals("Sending", legends.get(0).getName());
    assertEquals("Receiving", legends.get(1).getName());
  }

  @Test
  public void updaterRegisteredCorrectly() {
    AspectObserver observer = new AspectObserver();

    final boolean[] usageUpdated = {false};
    myMonitor.getNetworkUsage().addDependency(observer).onChange(
      LineChartModel.Aspect.LINE_CHART, () -> usageUpdated[0] = true);

    final boolean[] legendUpdated = {false};
    myMonitor.getLegends().addDependency(observer).onChange(
      LegendComponentModel.Aspect.LEGEND, () -> legendUpdated[0] = true);

    final boolean[] trafficAxisUpdated = {false};
    myMonitor.getTrafficAxis().addDependency(observer).onChange(
      AxisComponentModel.Aspect.AXIS, () -> trafficAxisUpdated[0] = true);

    myTimer.tick(1);
    assertTrue(usageUpdated[0]);
    assertTrue(legendUpdated[0]);
    assertTrue(trafficAxisUpdated[0]);
  }

  @Test
  public void updaterUnregisteredCorrectlyOnExit() {
    myMonitor.exit();
    AspectObserver observer = new AspectObserver();

    final boolean[] usageUpdated = {false};
    myMonitor.getNetworkUsage().addDependency(observer).onChange(
      LineChartModel.Aspect.LINE_CHART, () -> usageUpdated[0] = true);

    final boolean[] legendUpdated = {false};
    myMonitor.getLegends().addDependency(observer).onChange(
      LegendComponentModel.Aspect.LEGEND, () -> legendUpdated[0] = true);

    final boolean[] trafficAxisUpdated = {false};
    myMonitor.getTrafficAxis().addDependency(observer).onChange(
      AxisComponentModel.Aspect.AXIS, () -> trafficAxisUpdated[0] = true);

    myTimer.tick(1);
    assertFalse(usageUpdated[0]);
    assertFalse(legendUpdated[0]);
    assertFalse(trafficAxisUpdated[0]);
  }

  @Test
  public void testExpand() {
    assertEquals(myProfilers.getStage().getClass(), NullMonitorStage.class);
    myMonitor.expand();
    assertThat(myProfilers.getStage(), instanceOf(NetworkProfilerStage.class));
  }
}