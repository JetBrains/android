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
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuMonitor;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.memory.MemoryMonitor;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.NetworkMonitor;
import com.android.tools.profilers.network.NetworkProfilerStage;
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
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), myTimer);
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
    // Test that we have three monitors
    assertThat(points.size()).isEqualTo(3);

    // Test the first monitor goes to cpu profiling
    myUi.mouse.click(points.get(0).x + 1, points.get(0).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(CpuProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the second monitor goes to memory profiling
    myUi.mouse.click(points.get(1).x + 1, points.get(1).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(MemoryProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the third monitor goes to network profiling
    myUi.mouse.click(points.get(2).x + 1, points.get(2).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(NetworkProfilerStage.class);
  }

  @Test
  public void testMonitorTooltip() throws IOException {
    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);
    StudioMonitorStage stage = (StudioMonitorStage)myProfilers.getStage();

    List<Point> points = new TreeWalker(myView.getComponent()).descendantStream()
      .filter(d -> d instanceof LineChart)
      .map(c -> myUi.getPosition(c))
      .collect(Collectors.toList());
    // Test that we have three monitors
    assertThat(points.size()).isEqualTo(3);

    // cpu monitoring
    myUi.mouse.moveTo(points.get(0).x + 1, points.get(0).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(CpuMonitor.class);
    assertThat(stage.getTooltip().isFocused()).isTrue();
    stage.getMonitors().forEach(monitor -> assertThat(monitor.isFocused()).isEqualTo(monitor == stage.getTooltip()));

    // memory monitoring
    myUi.mouse.moveTo(points.get(1).x + 1, points.get(1).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(MemoryMonitor.class);
    assertThat(stage.getTooltip().isFocused()).isTrue();
    stage.getMonitors().forEach(monitor -> assertThat(monitor.isFocused()).isEqualTo(monitor == stage.getTooltip()));

    // network monitoring
    myUi.mouse.moveTo(points.get(2).x + 1, points.get(2).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(NetworkMonitor.class);
    assertThat(stage.getTooltip().isFocused()).isTrue();
    stage.getMonitors().forEach(monitor -> assertThat(monitor.isFocused()).isEqualTo(monitor == stage.getTooltip()));

    // no tooltip
    myUi.mouse.moveTo(0, 0);
    assertThat(stage.getTooltip()).isNull();
    stage.getMonitors().forEach(monitor -> assertThat(monitor.isFocused()).isFalse());
  }

  @Test
  public void testDeviceRendering() throws IOException {
    StudioProfilersView.DeviceComboBoxRenderer renderer = new StudioProfilersView.DeviceComboBoxRenderer();
    JList<Profiler.Device> list = new JList<>();
    // Null device
    Profiler.Device device = null;
    Component component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo(renderer.getEmptyText());

    // Standard case
    device = Profiler.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Profiler.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234)");

    // Suffix not serial
    device = Profiler.Device.newBuilder()
      .setModel("Model-9999")
      .setSerial("1234")
      .setState(Profiler.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model-9999 (1234)");

    // Suffix serial
    device = Profiler.Device.newBuilder()
      .setModel("Model-1234")
      .setSerial("1234")
      .setState(Profiler.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234)");

    // With manufacturer
    device = Profiler.Device.newBuilder()
      .setManufacturer("Manufacturer")
      .setModel("Model")
      .setSerial("1234")
      .setState(Profiler.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Manufacturer Model (1234)");

    // Disconnected
    device = Profiler.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Profiler.Device.State.DISCONNECTED)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234) [DISCONNECTED]");

    // Offline
    device = Profiler.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Profiler.Device.State.OFFLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234) [OFFLINE]");

    // Unspecifed
    device = Profiler.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Profiler.Device.State.UNSPECIFIED)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234)");
  }

  @Test
  public void testProcessRendering() throws IOException {
    StudioProfilersView.ProcessComboBoxRenderer renderer = new StudioProfilersView.ProcessComboBoxRenderer();
    JList<Profiler.Process> list = new JList<>();
    // Null process
    Profiler.Process process = null;
    Component component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertThat(component.toString()).isEqualTo(renderer.getEmptyText());

    // Process
    process = Profiler.Process.newBuilder()
      .setName("MyProcessName")
      .setPid(1234)
      .setState(Profiler.Process.State.ALIVE)
      .build();
    component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertThat(component.toString()).isEqualTo("MyProcessName (1234)");

    // Dead process
    process = Profiler.Process.newBuilder()
      .setName("MyDeadProcessName")
      .setPid(4444)
      .setState(Profiler.Process.State.DEAD)
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
  public void testNoStage() throws Exception {
    StudioProfilersView view = new StudioProfilersView(myProfilers, new FakeIdeProfilerComponents());
    JPanel component = view.getComponent();
    new ReferenceWalker(myProfilers).assertNotReachable(view, component);
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

  static class FakeStage extends Stage {
    public FakeStage(@NotNull StudioProfilers profilers) {
      super(profilers);
    }

    @Override
    public void enter() {

    }

    @Override
    public void exit() {

    }
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