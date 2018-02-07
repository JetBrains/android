/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.SelectionComponent;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.swing.FakeKeyboard;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.NetworkProfiler.NetworkProfilerData;
import static com.android.tools.profiler.proto.NetworkProfiler.SpeedData;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class NetworkProfilerStageViewTest {

  private static final List<NetworkProfilerData> NETWORK_PROFILER_DATA_LIST = ImmutableList.<NetworkProfilerData>builder()
    .add(createSpeedData(0, 0, 0))
    .add(createSpeedData(10, 1, 1))
    .add(createSpeedData(20, 0, 0))
    .add(createSpeedData(30, 1, 1))
    .add(createSpeedData(40, 1, 1))
    .add(createSpeedData(50, 1, 1))
    .add(createConnectionData(0, 0))
    .add(createConnectionData(10, 0))
    .add(createConnectionData(20, 0))
    .add(createConnectionData(30, 0))
    .add(createConnectionData(40, 1)).build();
  private static final Range VIEW_RANGE = new Range(0, 60);

  private FakeUi myFakeUi;
  private StudioProfilersView myView;

  private final FakeProfilerService myProfilerService = new FakeProfilerService(true);
  private final FakeNetworkService myNetworkService =
    FakeNetworkService.newBuilder().setNetworkDataList(NETWORK_PROFILER_DATA_LIST).build();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("NetworkProfilerStageViewTestChannel", myProfilerService, myNetworkService,
                        new FakeEventService(), new FakeMemoryService(), new FakeCpuService());

  private FakeTimer myTimer;

  @Before
  public void setUp() {
    myTimer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), myTimer);
    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));

    // StudioProfilersView initialization needs to happen after the tick, as during setDevice/setProcess the StudioMonitorStage is
    // constructed. If the StudioMonitorStageView is constructed as well, grpc exceptions will be thrown due to lack of various services
    // in the channel, and the tick loop would not complete properly to set the process and agent status.
    profilers.setStage(new NetworkProfilerStage(profilers));
    // Initialize the view after the stage, otherwise it will create the views for the monitoring stage.
    myView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    JPanel viewComponent = myView.getComponent();
    viewComponent.setSize(new Dimension(600, 200));
    myFakeUi = new FakeUi(viewComponent);
    profilers.getTimeline().getViewRange().set(0, 60);
  }

  @Test
  public void draggingSelectionOpensConnectionsViewAndPressingEscapeClosesIt() throws Exception {
    NetworkProfilerStageView stageView = (NetworkProfilerStageView)myView.getStageView();

    TreeWalker stageWalker = new TreeWalker(stageView.getComponent());
    LineChart lineChart = (LineChart)stageWalker.descendantStream().filter(LineChart.class::isInstance).findFirst().get();
    SelectionComponent selectionComponent =
      (SelectionComponent)stageWalker.descendantStream().filter(SelectionComponent.class::isInstance).findFirst().get();

    ConnectionsView connectionsView = stageView.getConnectionsView();
    TreeWalker connectionsViewWalker = new TreeWalker(connectionsView.getComponent());
    assertThat(connectionsViewWalker.ancestorStream().allMatch(Component::isVisible)).isFalse();

    Point start = myFakeUi.getPosition(lineChart);
    assertThat(stageView.getStage().getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    assertThat(connectionsViewWalker.ancestorStream().allMatch(Component::isVisible)).isFalse();

    myFakeUi.mouse.press(start.x, start.y);
    assertThat(stageView.getStage().getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    assertThat(connectionsViewWalker.ancestorStream().allMatch(Component::isVisible)).isFalse();

    myFakeUi.mouse.dragDelta(10, 0);
    assertThat(stageView.getStage().getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    assertThat(connectionsViewWalker.ancestorStream().allMatch(Component::isVisible)).isFalse();

    myFakeUi.mouse.release();
    assertThat(stageView.getStage().getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    assertThat(connectionsViewWalker.ancestorStream().allMatch(Component::isVisible)).isTrue();

    myFakeUi.keyboard.setFocus(selectionComponent);
    myFakeUi.keyboard.press(FakeKeyboard.Key.ESC);
    assertThat(connectionsViewWalker.ancestorStream().allMatch(Component::isVisible)).isFalse();
  }

  @Test
  public void dragSelectionToggleInfoPanelVisibility() {
    NetworkProfilerStageView stageView = (NetworkProfilerStageView)myView.getStageView();
    TreeWalker treeWalker = new TreeWalker(stageView.getComponent());
    JComponent infoPanel = (JComponent)treeWalker.descendantStream().filter(c -> "Info".equals(c.getName())).findFirst().get();
    assertFalse(infoPanel.isVisible());
    LineChart lineChart = (LineChart)treeWalker.descendantStream().filter(LineChart.class::isInstance).findFirst().get();

    int microSecondToX = (int)(lineChart.getSize().getWidth() / (VIEW_RANGE.getMax() - VIEW_RANGE.getMin()));
    Point start = myFakeUi.getPosition(lineChart);
    myFakeUi.mouse.drag(start.x, start.y, 9 * microSecondToX, 0);
    assertThat(infoPanel.isVisible()).isFalse();
    myFakeUi.mouse.drag(start.x + 10 * microSecondToX, start.y, 5 * microSecondToX, 0);
    assertThat(infoPanel.isVisible()).isTrue();
    myFakeUi.mouse.drag(start.x + 20 * microSecondToX, start.y, 5 * microSecondToX, 0);
    assertThat(infoPanel.isVisible()).isFalse();
    myFakeUi.mouse.drag(start.x + 35 * microSecondToX, start.y, 2 * microSecondToX, 0);
    assertThat(infoPanel.isVisible()).isTrue();
    myFakeUi.mouse.drag(start.x, start.y, 40 * microSecondToX, 0);
    assertThat(infoPanel.isVisible()).isTrue();
  }

  private static NetworkProfilerData createSpeedData(long time, long sent, long received) {
    return NetworkProfilerData.newBuilder()
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos(time))
      .setSpeedData(SpeedData.newBuilder().setReceived(received).setSent(sent).build()).build();
  }

  private static NetworkProfilerData createConnectionData(long time, int connectionNumber) {
    return NetworkProfilerData.newBuilder()
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos(time))
      .setConnectionData(NetworkProfiler.ConnectionData.newBuilder().setConnectionNumber(connectionNumber).build()).build();
  }
}
