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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.sdklib.AndroidVersion;
import com.android.testutils.TestUtils;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.idea.transport.faketransport.FakeGrpcServer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.cpu.CpuCaptureStage;
import com.android.tools.profilers.cpu.CpuMonitorTooltip;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils;
import com.android.tools.profilers.energy.EnergyMonitorTooltip;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.memory.FakeCaptureObjectLoader;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import com.android.tools.profilers.memory.MemoryCaptureStage;
import com.android.tools.profilers.memory.MemoryMonitorTooltip;
import com.android.tools.profilers.sessions.SessionsView;
import com.google.common.truth.Truth;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import icons.StudioIcons;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunsInEdt
@RunWith(Parameterized.class)
public class StudioProfilersViewTest {

  private static final Common.Session SESSION_O = Common.Session.newBuilder().setSessionId(2).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS)
    .setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).setPid(1).build();
  private static final Common.SessionMetaData SESSION_O_METADATA = Common.SessionMetaData.newBuilder().setSessionId(2).setJvmtiEnabled(true)
    .setSessionName("App Device").setType(Common.SessionMetaData.SessionType.FULL).setStartTimestampEpochMs(1).build();

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myService;
  private final boolean myIsTestingProfileable;

  public StudioProfilersViewTest(boolean isTestingProfileable) {
    myIsTestingProfileable = isTestingProfileable;
    myService = isTestingProfileable
                ? new FakeTransportService(myTimer, true, AndroidVersion.VersionCodes.S, Common.Process.ExposureLevel.PROFILEABLE)
                : new FakeTransportService(myTimer);
    myGrpcChannel = FakeGrpcServer.createFakeGrpcServer("StudioProfilerTestChannel", getService(), myProfilerService);
  }

  private final FakeProfilerService myProfilerService = new FakeProfilerService(myTimer);

  @Rule public final FakeGrpcServer myGrpcChannel;
  @Rule public final EdtRule myEdtRule = new EdtRule();
  @Rule public final ApplicationRule myAppRule = new ApplicationRule();  // For initializing HelpTooltip.

  private StudioProfilers myProfilers;
  private FakeIdeProfilerServices myProfilerServices = new FakeIdeProfilerServices();
  private StudioProfilersView myView;
  private FakeUi myUi;

  private FakeTransportService getService() {
    return myService;
  }

  @Before
  public void setUp() {
    myProfilerServices.enableEnergyProfiler(true);
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myProfilerServices, myTimer);
    myProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    // We setup and profile a process, we assume that process has an agent attached by default.
    myService.setAgentStatus(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build());
    // Make sure a process is selected
    myView = new StudioProfilersView(myProfilers, new FakeIdeProfilerComponents());
    myView.bind(FakeStage.class, FakeView::new);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    JLayeredPane component = myView.getComponent();
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
  public void testMonitorExpansion() {
    assumeFalse(myIsTestingProfileable);
    // Set session to enable Energy Monitor.
    myProfilerService.addSession(SESSION_O, SESSION_O_METADATA);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.getSessionsManager().setSession(SESSION_O);
    myUi.layout();

    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);

    List<Point> points = new TreeWalker(myView.getComponent()).descendantStream()
      .filter(d -> d instanceof LineChart)
      .map(c -> myUi.getPosition(c))
      .collect(Collectors.toList());
    // Test that we have the expected number of monitors
    assertThat(points.size()).isEqualTo(3);

    // Test the first monitor goes to cpu profiler
    myUi.mouse.click(points.get(0).x + 1, points.get(0).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(CpuProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the second monitor goes to memory profiler
    myUi.mouse.click(points.get(1).x + 1, points.get(1).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(MainMemoryProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the fourth monitor goes to energy profiler
    myUi.mouse.click(points.get(2).x + 1, points.get(2).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(EnergyProfilerStage.class);
    myProfilers.setMonitoringStage();
  }

  @Test
  public void testMonitorTooltip() {
    assumeFalse(myIsTestingProfileable);
    // Set Session to enable Energy monitor tooltip.
    myProfilerService.addSession(SESSION_O, SESSION_O_METADATA);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.getSessionsManager().setSession(SESSION_O);
    myUi.layout();

    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);
    StudioMonitorStage stage = (StudioMonitorStage)myProfilers.getStage();

    List<Point> points = new TreeWalker(myView.getComponent()).descendantStream()
      .filter(d -> d instanceof LineChart)
      .map(c -> myUi.getPosition(c))
      .collect(Collectors.toList());
    // Test that we have the expected number of monitors
    assertThat(points.size()).isEqualTo(3);

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

    // energy monitor tooltip
    myUi.mouse.moveTo(points.get(2).x + 1, points.get(2).y + 1);
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
  public void testRememberSessionUiStates() {
    // Check that sessions is initially expanded
    assertThat(myView.getSessionsView().getCollapsed()).isFalse();

    // Fake a collapse action and re-create the StudioProfilerView, the session UI should now remain collapsed.
    myView.getSessionsView().getCollapseButton().doClick();
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myProfilerServices, myTimer);
    StudioProfilersView profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    assertThat(profilersView.getSessionsView().getCollapsed()).isTrue();

    // Fake a resize and re-create the StudioProfilerView, the session UI should maintain the previous dimension
    profilersView.getSessionsView().getExpandButton().doClick();
    ThreeComponentsSplitter splitter = (ThreeComponentsSplitter)profilersView.getComponent().getComponent(0);
    assertThat(splitter.getFirstSize()).isEqualTo(SessionsView.getComponentMinimizeSize(true).width);
    splitter.setSize(1024, 450);
    FakeUi ui = new FakeUi(splitter);
    myUi.mouse.drag(splitter.getFirstSize(), 0, 10, 0);

    profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myProfilerServices, myTimer);
    profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    assertThat(profilersView.getSessionsView().getCollapsed()).isFalse();
    assertThat(((ThreeComponentsSplitter)profilersView.getComponent().getComponent(0)).getFirstSize()).isEqualTo(splitter.getFirstSize());
  }

  @Test
  public void testGoLiveButtonStates() {
    // Check that go live is initially enabled and toggled
    JToggleButton liveButton = myView.getGoLiveButton();
    ArrayList<ContextMenuItem> contextMenuItems = ProfilerContextMenu.createIfAbsent(myView.getStageComponent()).getContextMenuItems();
    ContextMenuItem attachItem = null;
    ContextMenuItem detachItem = null;
    for (ContextMenuItem item : contextMenuItems) {
      if (item.getText().equals(StudioProfilersView.ATTACH_LIVE)) {
        attachItem = item;
      }
      else if (item.getText().equals(StudioProfilersView.DETACH_LIVE)) {
        detachItem = item;
      }
    }
    assertThat(attachItem).isNotNull();
    assertThat(detachItem).isNotNull();

    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    assertThat(liveButton.isEnabled()).isTrue();
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isTrue();

    // Detaching from live should unselect the button.
    detachItem.run();
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    assertThat(liveButton.isEnabled()).isTrue();
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(attachItem.isEnabled()).isTrue();
    assertThat(detachItem.isEnabled()).isFalse();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StudioProfilersView.ATTACH_LIVE);

    // Attaching to live should select the button again.
    attachItem.run();
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    assertThat(liveButton.isEnabled()).isTrue();
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isTrue();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.PAUSE_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StudioProfilersView.DETACH_LIVE);

    // Stopping the session should disable and unselect the button
    myProfilers.getSessionsManager().endCurrentSession();
    Common.Session deadSession = myProfilers.getSessionsManager().getSelectedSession();
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isFalse();
    assertThat(liveButton.isEnabled()).isFalse();
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isFalse();

    Common.Device onlineDevice = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build();
    Common.Process onlineProcess = Common.Process.newBuilder().setPid(2).setState(Common.Process.State.ALIVE).build();
    myProfilers.getSessionsManager().beginSession(onlineDevice, onlineProcess);
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    // Live button should be selected when switching to a live session.
    assertThat(liveButton.isEnabled()).isTrue();
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isTrue();

    // Switching to a dead session should disable and unselect the button.
    myProfilers.getSessionsManager().setSession(deadSession);
    assertThat(liveButton.isEnabled()).isFalse();
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isFalse();
  }

  @Test
  public void testGoLiveButtonWhenToggleStreaming() {
    JToggleButton liveButton = myView.getGoLiveButton();
    assertThat(liveButton.isEnabled()).isTrue();
    myProfilers.getTimeline().setStreaming(false);
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StudioProfilersView.ATTACH_LIVE);

    myProfilers.getTimeline().setStreaming(true);
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.PAUSE_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StudioProfilersView.DETACH_LIVE);
  }

  @Test
  public void testTimelineButtonEnableStates() {
    CommonButton zoomInButton = myView.getZoomInButton();
    CommonButton zoomOutButton = myView.getZoomOutButton();
    CommonButton resetButton = myView.getResetZoomButton();
    CommonButton frameSelectionButton = myView.getZoomToSelectionButton();
    JToggleButton liveButton = myView.getGoLiveButton();

    // A live session without agent should have all controls enabled
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    assertThat(zoomInButton.isEnabled()).isTrue();
    assertThat(zoomOutButton.isEnabled()).isTrue();
    assertThat(resetButton.isEnabled()).isTrue();
    assertThat(frameSelectionButton.isEnabled()).isFalse(); // Frame selection button is dependent on selection being available.
    assertThat(liveButton.isEnabled()).isTrue();

    // Updating the selection should enable the frame selection control.
    myProfilers.getTimeline().getSelectionRange().set(myProfilers.getTimeline().getDataRange());
    assertThat(zoomInButton.isEnabled()).isTrue();
    assertThat(zoomOutButton.isEnabled()).isTrue();
    assertThat(resetButton.isEnabled()).isTrue();
    assertThat(frameSelectionButton.isEnabled()).isTrue();
    assertThat(liveButton.isEnabled()).isTrue();

    // Stopping the session should disable the live control
    myProfilers.getSessionsManager().endCurrentSession();
    assertThat(zoomInButton.isEnabled()).isTrue();
    assertThat(zoomOutButton.isEnabled()).isTrue();
    assertThat(resetButton.isEnabled()).isTrue();
    assertThat(frameSelectionButton.isEnabled()).isTrue();
    assertThat(liveButton.isEnabled()).isFalse();

    // Starting a session that is waiting for an agent to initialize should have all controls disabled.
    Common.Device onlineDevice = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build();
    Common.Process onlineProcess = Common.Process.newBuilder().setPid(2).setState(Common.Process.State.ALIVE).build();
    myService.setAgentStatus(Common.AgentData.getDefaultInstance());
    myProfilers.getSessionsManager().beginSession(onlineDevice, onlineProcess);
    assertThat(zoomInButton.isEnabled()).isFalse();
    assertThat(zoomOutButton.isEnabled()).isFalse();
    assertThat(resetButton.isEnabled()).isFalse();
    assertThat(frameSelectionButton.isEnabled()).isFalse();
    assertThat(liveButton.isEnabled()).isFalse();

    // Controls should be enabled after agent is attached.
    myService.setAgentStatus(
      Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(zoomInButton.isEnabled()).isTrue();
    assertThat(zoomOutButton.isEnabled()).isTrue();
    assertThat(resetButton.isEnabled()).isTrue();
    assertThat(frameSelectionButton.isEnabled()).isFalse();
    assertThat(liveButton.isEnabled()).isTrue();

    // Setting to an empty session should have all controls disabled.
    myProfilers.getSessionsManager().setSession(Common.Session.getDefaultInstance());
    assertThat(zoomInButton.isEnabled()).isFalse();
    assertThat(zoomOutButton.isEnabled()).isFalse();
    assertThat(resetButton.isEnabled()).isFalse();
    assertThat(frameSelectionButton.isEnabled()).isFalse();
    assertThat(liveButton.isEnabled()).isFalse();
  }

  @Test
  public void testLoadingPanelWhileWaitingForPreferredProcess() {
    final String FAKE_PROCESS_2 = "FakeProcess2";
    assertThat(myView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myView.getStageLoadingComponent().isVisible()).isFalse();

    // Sets a preferred process is set, the UI should wait and show the loading panel.
    myProfilers.getSessionsManager().endCurrentSession();
    myProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_2, null);
    assertThat(myProfilers.getAutoProfilingEnabled()).isTrue();
    assertThat(myView.getStageViewComponent().isVisible()).isFalse();
    assertThat(myView.getStageLoadingComponent().isVisible()).isTrue();

    Common.Process process = Common.Process.newBuilder()
      .setPid(2)
      .setDeviceId(FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .build();
    myService.addProcess(FAKE_DEVICE, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Preferred process is found, session begins and the loading stops.
    assertThat(myView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myView.getStageLoadingComponent().isVisible()).isFalse();
  }

  @Test
  public void testLoadingPanelWhileWaitingForAgentAttach() {
    assumeFalse(myIsTestingProfileable); // hardcoded `FAKE_DEVICE` is different than one used for the profileable test
    final String FAKE_PROCESS_2 = "FakeProcess2";
    assertThat(myView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myView.getStageLoadingComponent().isVisible()).isFalse();

    myService.setAgentStatus(
      Common.AgentData.getDefaultInstance());
    Common.Process process = Common.Process.newBuilder()
      .setPid(2)
      .setDeviceId(FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .build();
    myService.addProcess(FAKE_DEVICE, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(FAKE_DEVICE, process);

    // Agent is detached, the UI should wait and show the loading panel.
    assertThat(myView.getStageViewComponent().isVisible()).isFalse();
    assertThat(myView.getStageLoadingComponent().isVisible()).isTrue();

    myService.setAgentStatus(
      Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Attach status is detected, loading should stop.
    assertThat(myView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myView.getStageLoadingComponent().isVisible()).isFalse();
  }

  @Test
  public void testNullStageIfDeviceIsUnsupported() {
    final String FAKE_PROCESS_2 = "FakeProcess2";
    final String UNSUPPORTED_DEVICE_NAME = "UnsupportedDevice";
    final String UNSUPPORTED_REASON = "This device is unsupported";
    assertThat(myView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myView.getStageLoadingComponent().isVisible()).isFalse();

    // Disconnect the current device and connect to an unsupported device.
    Common.Device dead_device = FAKE_DEVICE.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    Common.Device device = Common.Device.newBuilder()
      .setDeviceId(999)
      .setSerial(UNSUPPORTED_DEVICE_NAME)
      .setApiLevel(AndroidVersion.VersionCodes.KITKAT)
      .setFeatureLevel(AndroidVersion.VersionCodes.KITKAT)
      .setModel(UNSUPPORTED_DEVICE_NAME)
      .setState(Common.Device.State.ONLINE)
      .setUnsupportedReason(UNSUPPORTED_REASON)
      .build();
    Common.Process process = Common.Process.newBuilder()
      .setPid(2)
      .setDeviceId(device.getDeviceId())
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .build();
    myService.updateDevice(FAKE_DEVICE, dead_device);

    // Set the preferred device to the unsupported one. Loading screen will be displayed.
    myProfilers.getSessionsManager().endCurrentSession();
    myProfilers.setPreferredProcess(UNSUPPORTED_DEVICE_NAME, FAKE_PROCESS_2, null);
    assertThat(myView.getStageViewComponent().isVisible()).isFalse();
    assertThat(myView.getStageLoadingComponent().isVisible()).isTrue();

    // Preferred device is found. Loading stops and null stage should be displayed with the unsupported reason.
    myService.addDevice(device);
    myService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    assertThat(myView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myView.getStageLoadingComponent().isVisible()).isFalse();
  }

  @Test
  public void nonTimelineStageHidesRightToolbar_timelineStageShowsRightToolbar() {
    myProfilers.setStage(new MemoryCaptureStage(myProfilers, new FakeCaptureObjectLoader(), null, null));
    assertThat(myView.getRightToolbar().isVisible()).isFalse();

    myProfilers.setStage(new MainMemoryProfilerStage(myProfilers));
    assertThat(myView.getRightToolbar().isVisible()).isTrue();
  }

  @Test
  public void captureCpuStageGoesBackToCpuStageThenBackToMonitorStage() {
    myProfilers.setStage(CpuCaptureStage.create(myProfilers,
                                                ProfilersTestData.DEFAULT_CONFIG,
                                                TestUtils.resolveWorkspacePath(CpuProfilerUITestUtils.VALID_TRACE_PATH).toFile(),
                                                ProfilersTestData.SESSION_DATA.getSessionId()));
    myView.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isInstanceOf(CpuProfilerStage.class);
    myView.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  @Test
  public void profilerStaysInStageWhenUserConfirmsStay() {
    setFakeStage();
    myProfilerServices.setShouldProceedYesNoDialog(false);
    myView.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isInstanceOf(FakeStage.class);
  }

  @Test
  public void profilerExitsWhenUserConfirmsExit() {
    setFakeStage();
    myProfilerServices.setShouldProceedYesNoDialog(true);
    myView.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isNotInstanceOf(FakeStage.class);
  }

  @Test
  public void menuShowsSupportedStagesForDebuggable() {
    assumeFalse(myIsTestingProfileable);
    menuShowsSupportedStages(CpuProfilerStage.class, MainMemoryProfilerStage.class, EnergyProfilerStage.class);
  }

  @Test
  public void menuShowsSupportedStagesForProfileable() {
    assumeTrue(myIsTestingProfileable);
    menuShowsSupportedStages(CpuProfilerStage.class, MainMemoryProfilerStage.class);
  }

  private void menuShowsSupportedStages(Class<?>... expected) {
    TreeWalker t = new TreeWalker(myView.getCommonToolbar());
    Predicate<ComboBoxModel<?>> itemsChecker = model ->
      model.getSize() == expected.length &&
      IntStream.range(0, model.getSize())
        .allMatch(i -> model.getElementAt(i) instanceof Class<?> &&
                       expected[i].isAssignableFrom((Class<?>)model.getElementAt(i)));
    assertThat(t.descendantStream()
                 .anyMatch(view -> view instanceof JComboBox<?> && itemsChecker.test(((JComboBox<?>)view).getModel())))
      .isTrue();
  }

  private void setFakeStage() {
    FakeStage stage = new FakeStage(myProfilers);
    myView.bind(stage.getClass(), FakeStageView::new);
    myProfilers.setStage(stage);
    assertThat(myProfilers.getStage()).isInstanceOf(FakeStage.class);
  }

  private static class FakeStage extends Stage<Timeline> {
    FakeStage(@NotNull StudioProfilers profilers) {
      super(profilers);
    }

    @NotNull
    @Override
    public Timeline getTimeline() {
      return getStudioProfilers().getTimeline();
    }

    @Override
    public void enter() { }

    @Override
    public void exit() { }

    @Override
    public AndroidProfilerEvent.Stage getStageType() {
      return AndroidProfilerEvent.Stage.UNKNOWN_STAGE;
    }

    @Nullable
    @Override
    public String getConfirmExitMessage() {
      return "Really?";
    }
  }

  private static class FakeStageView extends StageView<FakeStage> {
    public FakeStageView(@NotNull StudioProfilersView view, @NotNull FakeStage stage) {
      super(view, stage);
    }

    @Override
    public JComponent getToolbar() {
      return new JLabel("Hello world");
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

  @Parameterized.Parameters
  public static List<Boolean> isTestingProfileable() {
    return Arrays.asList(false, true);
  }
}