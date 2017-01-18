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

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.*;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.NetworkProfiler.*;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

public class NetworkRadioViewTest {
  private static final ImmutableList<NetworkProfilerData> FAKE_DATA =
    new ImmutableList.Builder<NetworkProfilerData>()
    .add(FakeNetworkService.newRadioData(1, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.ACTIVE))
    .add(FakeNetworkService.newRadioData(5, ConnectivityData.NetworkType.WIFI, ConnectivityData.RadioState.UNSPECIFIED))
    .add(FakeNetworkService.newRadioData(18, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.ACTIVE)).build();

  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("NetworkMonitorTest", FakeNetworkService.newBuilder().setNetworkDataList(FAKE_DATA).build());

  private NetworkRadioView myView;
  private NetworkProfilerStageView myStageView;
  private FakeTimer myTimer;
  @Before
  public void setUp() {
    myTimer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), myTimer);
    StudioProfilersView profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    NetworkProfilerStage stage = new NetworkProfilerStage(profilers);
    myStageView = new NetworkProfilerStageView(profilersView, stage);
    myView = new NetworkRadioView(myStageView);
  }

  @Test
  public void componentStructure() {
    assertTrue(myView.getComponent() instanceof JPanel);
    JPanel component = (JPanel)myView.getComponent();
    assertEquals(2, component.getComponentCount());

    assertTrue(component.getComponent(0) instanceof JPanel);
    assertTrue(component.getComponent(1) instanceof StateChart);
    JPanel labelAndLegend = (JPanel)component.getComponent(0);
    assertEquals(2, labelAndLegend.getComponentCount());
    assertTrue(labelAndLegend.getComponent(0) instanceof JLabel);
    assertTrue(labelAndLegend.getComponent(1) instanceof LegendComponent);
  }

  @Test
  public void stateChartCreatedAsIntended() {
    myStageView.getTimeline().getViewRange().set(0, TimeUnit.SECONDS.toMicros(10));
    myTimer.tick(1);

    List<RangedSeries<NetworkRadioDataSeries.RadioState>> series = myStageView.getStage().getRadioState().getSeries();
    assertEquals(1, series.size());

    List<SeriesData<NetworkRadioDataSeries.RadioState>> expected = Arrays.asList(
      new SeriesData<>(TimeUnit.SECONDS.toMicros(1), NetworkRadioDataSeries.RadioState.HIGH),
      new SeriesData<>(TimeUnit.SECONDS.toMicros(5), NetworkRadioDataSeries.RadioState.WIFI));

    checkSeriesEquals(expected, series.get(0).getSeries());

    myStageView.getTimeline().getViewRange().set(TimeUnit.SECONDS.toMicros(15), TimeUnit.SECONDS.toMicros(20));
    myTimer.tick(1);

    expected = Collections.singletonList(
      new SeriesData<>(TimeUnit.SECONDS.toMicros(18), NetworkRadioDataSeries.RadioState.HIGH));

    series = myStageView.getStage().getRadioState().getSeries();
    assertEquals(1, series.size());
    checkSeriesEquals(expected, series.get(0).getSeries());
  }

  @Test
  public void testLabel() {
    assertEquals("Radio", getLabel().getText());
  }

  private JLabel getLabel() {
    JPanel comp = (JPanel)myView.getComponent();
    JPanel topPane = (JPanel)comp.getComponent(0);
    assertThat(topPane.getComponent(0), instanceOf(JLabel.class));
    return (JLabel)topPane.getComponent(0);
  }

  private static void checkSeriesEquals(@NotNull List<SeriesData<NetworkRadioDataSeries.RadioState>> expected,
                                 @NotNull List<SeriesData<NetworkRadioDataSeries.RadioState>> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); ++i) {
      assertEquals(expected.get(i).x, actual.get(i).x);
      assertEquals(expected.get(i).value, actual.get(i).value);
    }
  }
}