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
package com.android.tools.profilers;

import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.cpu.CpuMonitorTooltip;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.energy.EnergyMonitorTooltip;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.memory.MemoryMonitorTooltip;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.NetworkMonitorTooltip;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.google.common.truth.Truth;
import com.intellij.ui.JBSplitter;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

public class StudioProfilersViewTest {

  private final FakeProfilerService myService = new FakeProfilerService();
  @Rule public FakeGrpcServer myGrpcChannel = new FakeGrpcServer("StudioProfilerTestChannel", myService);
  private StudioProfilers myProfilers;
  private FakeTimer myTimer;
  private StudioProfilersView myView;
  private FakeUi myUi;

  @Before
  public void setUp() throws Exception {
    myTimer = new FakeTimer();
    FakeIdeProfilerServices services = new FakeIdeProfilerServices();
    services.enableEnergyProfiler(true);
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), services, myTimer);
    // Make sure a process is selected
    myView = new StudioProfilersView(myProfilers, new FakeIdeProfilerComponents());
    myView.bind(FakeStage.class, FakeView::new);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    JPanel component = myView.getComponent();
    component.setSize(1024, 450);
    myUi = new FakeUi(component);
  }

  @Test
  public void testSameStageTransition() {
    FakeStage stage = new FakeStage(myProfilers);
    myProfilers.setStage(stage);
    StageView view = myView.getStageView();

    myProfilers.setStage(stage);
    assertThat(myView.getStageView()).isEqualTo(view);
  }

  @Test
  public void testMonitorExpansion() throws IOException {
    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);

    List<Point> points = new TreeWalker(myView.getComponent()).descendantStream()
      .filter(d -> d instanceof LineChart)
      .map(c -> myUi.getPosition(c))
      .collect(Collectors.toList());
    // Test that we have the expected number of monitors
    assertThat(points.size()).isEqualTo(4);

    //// Test the first monitor goes to cpu profiler
    myUi.mouse.click(points.get(0).x + 1, points.get(0).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(CpuProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the second monitor goes to memory profiler
    myUi.mouse.click(points.get(1).x + 1, points.get(1).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(MemoryProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the third monitor goes to network profiler
    myUi.mouse.click(points.get(2).x + 1, points.get(2).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(NetworkProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the fourth monitor goes to energy profiler
    myUi.mouse.click(points.get(3).x + 1, points.get(3).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(EnergyProfilerStage.class);
    myProfilers.setMonitoringStage();
  }

  @Test
  public void testMonitorTooltip() throws IOException {
    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);
    StudioMonitorStage stage = (StudioMonitorStage)myProfilers.getStage();

    List<Point> points = new TreeWalker(myView.getComponent()).descendantStream()
      .filter(d -> d instanceof LineChart)
      .map(c -> myUi.getPosition(c))
      .collect(Collectors.toList());
    // Test that we have the expected number of monitors
    assertThat(points.size()).isEqualTo(4);

    // cpu monitor tooltip
    myUi.mouse.moveTo(points.get(0).x + 1, points.get(0).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(CpuMonitorTooltip.class);
    ProfilerMonitor cpuMonitor = ((CpuMonitorTooltip)stage.getTooltip()).getMonitor();
    stage.getMonitors().forEach(
      monitor -> Truth.assertWithMessage("Only the CPU Monitor should be focused.")
        .that(monitor.isFocused()).isEqualTo(monitor == cpuMonitor));

    // memory monitor tooltip
    myUi.mouse.moveTo(points.get(1).x + 1, points.get(1).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(MemoryMonitorTooltip.class);
    ProfilerMonitor memoryMonitor = ((MemoryMonitorTooltip)stage.getTooltip()).getMonitor();
    stage.getMonitors().forEach(
      monitor -> Truth.assertWithMessage("Only the Memory Monitor should be focused.")
        .that(monitor.isFocused()).isEqualTo(monitor == memoryMonitor));

    // network monitor tooltip
    myUi.mouse.moveTo(points.get(2).x + 1, points.get(2).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(NetworkMonitorTooltip.class);
    ProfilerMonitor networMonitor = ((NetworkMonitorTooltip)stage.getTooltip()).getMonitor();
    stage.getMonitors().forEach(
      monitor -> Truth.assertWithMessage("Only the Network Monitor should be focused.")
        .that(monitor.isFocused()).isEqualTo(monitor == networMonitor));

    // energy monitor tooltip
    myUi.mouse.moveTo(points.get(3).x + 1, points.get(3).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(EnergyMonitorTooltip.class);
    ProfilerMonitor energyMonitor = ((EnergyMonitorTooltip)stage.getTooltip()).getMonitor();
    stage.getMonitors().forEach(
      monitor -> Truth.assertWithMessage("Only the Energy Monitor should be focused.")
        .that(monitor.isFocused()).isEqualTo(monitor == energyMonitor));

    // no tooltip
    myUi.mouse.moveTo(0, 0);
    assertThat(stage.getTooltip()).isNull();
    stage.getMonitors().forEach(monitor -> Truth.assertWithMessage("No monitor should be focused.").that(monitor.isFocused()).isFalse());
  }

  @Test
  public void testDeviceRendering() throws IOException {
    StudioProfilersView.DeviceComboBoxRenderer renderer = new StudioProfilersView.DeviceComboBoxRenderer();
    JList<Common.Device> list = new JList<>();
    // Null device
    Common.Device device = null;
    Component component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo(renderer.getEmptyText());

    // Standard case
    device = Common.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234)");

    // Suffix not serial
    device = Common.Device.newBuilder()
      .setModel("Model-9999")
      .setSerial("1234")
      .setState(Common.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model-9999 (1234)");

    // Suffix serial
    device = Common.Device.newBuilder()
      .setModel("Model-1234")
      .setSerial("1234")
      .setState(Common.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234)");

    // With manufacturer
    device = Common.Device.newBuilder()
      .setManufacturer("Manufacturer")
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Manufacturer Model (1234)");

    // Disconnected
    device = Common.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234) [DISCONNECTED]");

    // Offline
    device = Common.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.OFFLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234) [OFFLINE]");

    // Unspecifed
    device = Common.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.UNSPECIFIED)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234)");
  }

  @Test
  public void testProcessRendering() throws IOException {
    StudioProfilersView.ProcessComboBoxRenderer renderer = new StudioProfilersView.ProcessComboBoxRenderer();
    JList<Common.Process> list = new JList<>();
    // Null process
    Common.Process process = null;
    Component component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertThat(component.toString()).isEqualTo(renderer.getEmptyText());

    // Process
    process = Common.Process.newBuilder()
      .setName("MyProcessName")
      .setPid(1234)
      .setState(Common.Process.State.ALIVE)
      .build();
    component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertThat(component.toString()).isEqualTo("MyProcessName (1234)");

    // Dead process
    process = Common.Process.newBuilder()
      .setName("MyDeadProcessName")
      .setPid(4444)
      .setState(Common.Process.State.DEAD)
      .build();
    component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertThat(component.toString()).isEqualTo("MyDeadProcessName (4444) [DEAD]");
  }

  @Test
  public void testMonitorStage() throws Exception {
    transitionStage(new StudioMonitorStage(myProfilers));
  }

  @Test
  public void testNetworkStage() throws Exception {
    transitionStage(new NetworkProfilerStage(myProfilers));
  }

  @Test
  public void testMemoryStage() throws Exception {
    transitionStage(new MemoryProfilerStage(myProfilers));
  }

  @Test
  public void testCpuStage() throws Exception {
    transitionStage(new CpuProfilerStage(myProfilers));
  }

  @Test
  public void testEnergyStage() throws Exception {
    transitionStage(new EnergyProfilerStage(myProfilers));
  }

  @Test
  public void testNoStage() throws Exception {
    StudioProfilersView view = new StudioProfilersView(myProfilers, new FakeIdeProfilerComponents());
    JPanel component = view.getComponent();
    new ReferenceWalker(myProfilers).assertNotReachable(view, component);
  }

  @Test
  public void testSessionsViewHiddenBehindFlag() {
    FakeTimer timer = new FakeTimer();
    FakeIdeProfilerServices services = new FakeIdeProfilerServices();
    services.enableSessionsView(false);
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), services, timer);
    StudioProfilersView view = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    JComponent splitter = view.getComponent();
    assertThat(splitter).isInstanceOf(JBSplitter.class);
    assertThat(((JBSplitter)splitter).getFirstComponent()).isNull();

    // Test the true case as well.
    services.enableSessionsView(true);
    profilers = new StudioProfilers(myGrpcChannel.getClient(), services, timer);
    view = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    splitter = view.getComponent();
    assertThat(splitter).isInstanceOf(JBSplitter.class);
    assertThat(((JBSplitter)splitter).getFirstComponent()).isNotNull();
  }

  public void transitionStage(Stage stage) throws Exception {
    JPanel component = myView.getComponent();
    myProfilers.setStage(new FakeStage(myProfilers));
    new ReferenceWalker(myProfilers).assertNotReachable(myView, component);
    myProfilers.setStage(stage);
    // At this point it could be reachable with standard swing listeners.
    myProfilers.setStage(new FakeStage(myProfilers));
    // If we leaked a listener or a component in the tree then there will be a path
    // from the model all the way up to the main view or the main component. There could
    // be the case that some listeners that don't point to the view/component are still
    // leaked but it would be pretty rare that such a listener was needed in the first place.
    new ReferenceWalker(myProfilers).assertNotReachable(myView, component);
  }

  static class FakeView extends StageView<FakeStage> {

    public FakeView(@NotNull StudioProfilersView profilersView, @NotNull FakeStage stage) {
      super(profilersView, stage);
    }

    @Override
    public JComponent getToolbar() {
      return new JPanel();
    }
  }
}