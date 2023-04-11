/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_UNSPECIFIED_RESPONSE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.idea.transport.faketransport.FakeGrpcServer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import icons.StudioIcons;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
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
public class StageWithToolbarViewTest {

  private static final int NEW_DEVICE_ID = 1;
  private static final int NEW_PROCESS_ID = 2;
  private static final String FAKE_PROCESS_2 = "FakeProcess2";

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myService;
  private final boolean myIsTestingProfileable;

  public StageWithToolbarViewTest(boolean isTestingProfileable) {
    myIsTestingProfileable = isTestingProfileable;
    myService = isTestingProfileable
                ? new FakeTransportService(myTimer, true, AndroidVersion.VersionCodes.S, Common.Process.ExposureLevel.PROFILEABLE)
                : new FakeTransportService(myTimer);
    myGrpcChannel = FakeGrpcServer.createFakeGrpcServer("StudioProfilerTestChannel", myService);
  }

  @Rule public final FakeGrpcServer myGrpcChannel;
  @Rule public final EdtRule myEdtRule = new EdtRule();

  private final FakeIdeProfilerServices myProfilerServices = new FakeIdeProfilerServices();
  private StudioProfilers myProfilers;
  private FakeStudioProfilersView myFakeStudioProfilersView;
  private StageWithToolbarView myStageWithToolbarView;
  private final JPanel myStageComponent = new JPanel();

  @Before
  public void setUp() {
    myProfilerServices.enableEnergyProfiler(true);
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myProfilerServices, myTimer);
    myProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);

    IdeProfilerComponents fakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    myFakeStudioProfilersView = new FakeStudioProfilersView(myProfilers, fakeIdeProfilerComponents);
    myStageWithToolbarView =
      new StageWithToolbarView(myProfilers, myStageComponent, fakeIdeProfilerComponents, this::buildStageView, new JPanel());

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    if (myIsTestingProfileable) {
      // We setup and profile a process, we assume that process has an agent attached by default.
      updateAgentStatus(FAKE_PROCESS.getPid(), DEFAULT_AGENT_ATTACHED_RESPONSE);
    }
  }

  private StageView buildStageView(Stage stage) {
    if (stage instanceof NullMonitorStage) {
      return new NullMonitorStageView(myFakeStudioProfilersView, (NullMonitorStage)stage);
    }
    if (stage instanceof StudioMonitorStage) {
      return new StudioMonitorStageView(myFakeStudioProfilersView, (StudioMonitorStage)stage);
    }
    if (stage instanceof FakeStage) {
      return new FakeStageView(myFakeStudioProfilersView, (FakeStage)stage);
    }
    throw new IllegalStateException("Unsupported stage found: " + stage.getStageType());
  }

  @Test
  public void testGoLiveButtonStates() {
    // Check that go live is initially enabled and toggled
    JToggleButton liveButton = myStageWithToolbarView.getGoLiveButton();
    ArrayList<ContextMenuItem> contextMenuItems = ProfilerContextMenu.createIfAbsent(myStageComponent).getContextMenuItems();
    ContextMenuItem attachItem = null;
    ContextMenuItem detachItem = null;
    for (ContextMenuItem item : contextMenuItems) {
      if (item.getText().equals(StageWithToolbarView.ATTACH_LIVE)) {
        attachItem = item;
      }
      else if (item.getText().equals(StageWithToolbarView.DETACH_LIVE)) {
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
    assertThat(liveButton.getToolTipText()).startsWith(StageWithToolbarView.ATTACH_LIVE);

    // Attaching to live should select the button again.
    attachItem.run();
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    assertThat(liveButton.isEnabled()).isTrue();
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isTrue();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.PAUSE_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StageWithToolbarView.DETACH_LIVE);

    // Stopping the session should disable and unselect the button
    endSession();
    Common.Session deadSession = myProfilers.getSessionsManager().getSelectedSession();
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isFalse();
    assertThat(liveButton.isEnabled()).isFalse();
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isFalse();

    startSessionWithNewDeviceAndProcess();
    if (myIsTestingProfileable) {
      updateAgentStatus(NEW_PROCESS_ID, DEFAULT_AGENT_ATTACHED_RESPONSE);
    }
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
    JToggleButton liveButton = myStageWithToolbarView.getGoLiveButton();
    assertThat(liveButton.isEnabled()).isTrue();
    myProfilers.getTimeline().setStreaming(false);
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StageWithToolbarView.ATTACH_LIVE);

    myProfilers.getTimeline().setStreaming(true);
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.PAUSE_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StageWithToolbarView.DETACH_LIVE);
  }

  @Test
  public void testTimelineButtonEnableStates() {
    CommonButton zoomInButton = myStageWithToolbarView.getZoomInButton();
    CommonButton zoomOutButton = myStageWithToolbarView.getZoomOutButton();
    CommonButton resetButton = myStageWithToolbarView.getResetZoomButton();
    CommonButton frameSelectionButton = myStageWithToolbarView.getZoomToSelectionButton();
    JToggleButton liveButton = myStageWithToolbarView.getGoLiveButton();

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
    endSession();
    assertThat(zoomInButton.isEnabled()).isTrue();
    assertThat(zoomOutButton.isEnabled()).isTrue();
    assertThat(resetButton.isEnabled()).isTrue();
    assertThat(frameSelectionButton.isEnabled()).isTrue();
    assertThat(liveButton.isEnabled()).isFalse();

    // Starting a session that is waiting for an agent to initialize should have all controls disabled.
    startSessionWithNewDeviceAndProcess();
    updateAgentStatus(NEW_PROCESS_ID, DEFAULT_AGENT_UNSPECIFIED_RESPONSE);
    assertThat(zoomInButton.isEnabled()).isFalse();
    assertThat(zoomOutButton.isEnabled()).isFalse();
    assertThat(resetButton.isEnabled()).isFalse();
    assertThat(frameSelectionButton.isEnabled()).isFalse();
    assertThat(liveButton.isEnabled()).isFalse();

    // Controls should be enabled after agent is attached.
    updateAgentStatus(NEW_PROCESS_ID, DEFAULT_AGENT_ATTACHED_RESPONSE);
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
    assertThat(myStageWithToolbarView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myStageWithToolbarView.getStageLoadingComponent().isVisible()).isFalse();

    // Sets a preferred process, the UI should wait and show the loading panel.
    endSession();
    updatePreferredProcess(FAKE_DEVICE_NAME);
    assertThat(myProfilers.getAutoProfilingEnabled()).isTrue();
    assertThat(myStageWithToolbarView.getStageViewComponent().isVisible()).isFalse();
    assertThat(myStageWithToolbarView.getStageLoadingComponent().isVisible()).isTrue();

    Common.Process process = Common.Process.newBuilder()
      .setPid(NEW_PROCESS_ID)
      .setDeviceId(FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .setExposureLevel(myIsTestingProfileable ? Common.Process.ExposureLevel.PROFILEABLE : Common.Process.ExposureLevel.DEBUGGABLE)
      .build();
    startSession(FAKE_DEVICE, process);
    if (myIsTestingProfileable) {
      updateAgentStatus(NEW_PROCESS_ID, DEFAULT_AGENT_ATTACHED_RESPONSE);
    }

    // Preferred process is found, session begins and the loading stops.
    assertThat(myStageWithToolbarView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myStageWithToolbarView.getStageLoadingComponent().isVisible()).isFalse();
  }

  @Test
  public void testLoadingPanelWhileWaitingForAgentAttach() {
    assumeFalse(myIsTestingProfileable); // hardcoded `FAKE_DEVICE` is different than one used for the profileable test

    assertThat(myStageWithToolbarView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myStageWithToolbarView.getStageLoadingComponent().isVisible()).isFalse();

    Common.Process process = Common.Process.newBuilder()
      .setPid(NEW_PROCESS_ID)
      .setDeviceId(FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
      .build();
    startSession(FAKE_DEVICE, process);
    updateAgentStatus(NEW_PROCESS_ID, DEFAULT_AGENT_UNSPECIFIED_RESPONSE);

    // Agent is detached, the UI should wait and show the loading panel.
    assertThat(myStageWithToolbarView.getStageViewComponent().isVisible()).isFalse();
    assertThat(myStageWithToolbarView.getStageLoadingComponent().isVisible()).isTrue();

    updateAgentStatus(NEW_PROCESS_ID, DEFAULT_AGENT_ATTACHED_RESPONSE);

    // Attach status is detected, loading should stop.
    assertThat(myStageWithToolbarView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myStageWithToolbarView.getStageLoadingComponent().isVisible()).isFalse();
  }

  @Test
  public void testNullStageIfDeviceIsUnsupported() {
    final String UNSUPPORTED_DEVICE_NAME = "UnsupportedDevice";
    final String UNSUPPORTED_REASON = "This device is unsupported";
    assertThat(myStageWithToolbarView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myStageWithToolbarView.getStageLoadingComponent().isVisible()).isFalse();

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
      .setPid(NEW_PROCESS_ID)
      .setDeviceId(device.getDeviceId())
      .setState(Common.Process.State.ALIVE)
      .setName(FAKE_PROCESS_2)
      .setExposureLevel(myIsTestingProfileable ? Common.Process.ExposureLevel.PROFILEABLE : Common.Process.ExposureLevel.DEBUGGABLE)
      .build();
    myService.updateDevice(FAKE_DEVICE, dead_device);

    // Set the preferred device to the unsupported one. Loading screen will be displayed.
    endSession();
    updatePreferredProcess(UNSUPPORTED_DEVICE_NAME);
    assertThat(myStageWithToolbarView.getStageViewComponent().isVisible()).isFalse();
    assertThat(myStageWithToolbarView.getStageLoadingComponent().isVisible()).isTrue();

    // Preferred device is found. Loading stops and null stage should be displayed with the unsupported reason.
    myService.addDevice(device);
    myService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setProcess(device, process);
    assertThat(myStageWithToolbarView.getStageViewComponent().isVisible()).isTrue();
    assertThat(myStageWithToolbarView.getStageLoadingComponent().isVisible()).isFalse();
  }

  @Test
  public void nonTimelineStageHidesTimelineNavigationToolbar() {
    myProfilers.setStage(new FakeStage(myProfilers, null, false));
    assertThat(myStageWithToolbarView.getTimelineNavigationToolbar().isVisible()).isFalse();
  }

  @Test
  public void timelineStageShowsTimelineNavigationToolbar() {
    // The default stage is NullMonitorStage.
    assertThat(myStageWithToolbarView.getTimelineNavigationToolbar().isVisible()).isTrue();
  }

  private void startSessionWithNewDeviceAndProcess() {
    Common.Device onlineDevice = Common.Device.newBuilder()
      .setDeviceId(NEW_DEVICE_ID)
      .setFeatureLevel(AndroidVersion.VersionCodes.O)
      .setState(Common.Device.State.ONLINE)
      .build();
    Common.Process onlineProcess = Common.Process.newBuilder()
      .setPid(NEW_PROCESS_ID)
      .setDeviceId(NEW_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setExposureLevel(myIsTestingProfileable ? Common.Process.ExposureLevel.PROFILEABLE : Common.Process.ExposureLevel.DEBUGGABLE)
      .build();
    startSession(onlineDevice, onlineProcess);
  }

  private void startSession(Common.Device device, Common.Process process) {
    myService.addDevice(device);
    updateProcess(device, process);
  }

  private void updateProcess(Common.Device device, Common.Process process) {
    myService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    myProfilers.setProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
  }

  private void updateAgentStatus(int pid, Common.AgentData agentData) {
    long sessionStreamId = myProfilers.getSession().getStreamId();
    myService.addEventToStream(sessionStreamId, Common.Event.newBuilder()
      .setPid(pid)
      .setKind(Common.Event.Kind.AGENT)
      .setAgentData(agentData)
      .build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
  }

  private void endSession() {
    myProfilers.getSessionsManager().endCurrentSession();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
  }

  private void updatePreferredProcess(String preferredDeviceName) {
    myProfilers.setPreferredProcess(preferredDeviceName, FAKE_PROCESS_2, null);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
  }

  private static class FakeStudioProfilersView extends StudioProfilersView {

    protected FakeStudioProfilersView(@NotNull StudioProfilers profilers, @NotNull IdeProfilerComponents profilerComponents) {
      super(profilers, profilerComponents);
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return new JPanel();
    }

    @Override
    public void installCommonMenuItems(@NotNull JComponent component) { }

    @NotNull
    @Override
    public StageWithToolbarView getStageWithToolbarView() {
      return null;
    }

    @NotNull
    @Override
    public JPanel getStageComponent() {
      return null;
    }

    @Nullable
    @Override
    public StageView getStageView() {
      return null;
    }

    @Override
    public void dispose() { }
  }

  @Parameterized.Parameters
  public static List<Boolean> isTestingProfileable() {
    return Arrays.asList(false, true);
  }
}
