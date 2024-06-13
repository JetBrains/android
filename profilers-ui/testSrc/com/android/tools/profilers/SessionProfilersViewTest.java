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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode;
import com.android.tools.idea.transport.faketransport.FakeGrpcServer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.cpu.CpuMonitorTooltip;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import com.android.tools.profilers.memory.MemoryMonitorTooltip;
import com.android.tools.profilers.sessions.SessionsView;
import com.google.common.truth.Truth;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.awt.Point;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunsInEdt
@RunWith(Parameterized.class)
public class SessionProfilersViewTest {

  private static final Common.Session SESSION_O = Common.Session.newBuilder().setSessionId(2).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS)
    .setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).setPid(1).build();
  private static final Common.SessionMetaData SESSION_O_METADATA = Common.SessionMetaData.newBuilder().setSessionId(2).setJvmtiEnabled(true)
    .setSessionName("App Device").setType(Common.SessionMetaData.SessionType.FULL).setStartTimestampEpochMs(1).build();

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myService;
  private final boolean myIsTestingProfileable;

  public SessionProfilersViewTest(boolean isTestingProfileable) {
    myIsTestingProfileable = isTestingProfileable;
    myService = isTestingProfileable
                ? new FakeTransportService(myTimer, true, AndroidVersion.VersionCodes.S, Common.Process.ExposureLevel.PROFILEABLE)
                : new FakeTransportService(myTimer);
    myGrpcChannel = FakeGrpcServer.createFakeGrpcServer("SessionProfilersViewTestChannel", myService);
  }

  @Rule public final FakeGrpcServer myGrpcChannel;
  @Rule public final EdtRule myEdtRule = new EdtRule();
  @Rule public final ApplicationRule myAppRule = new ApplicationRule();  // For initializing HelpTooltip.
  @Rule public final DisposableRule myDisposableRule = new DisposableRule();

  private StudioProfilers myProfilers;
  private FakeIdeProfilerServices myProfilerServices = new FakeIdeProfilerServices();
  private SessionProfilersView myView;
  private FakeUi myUi;

  @Before
  public void setUp() {
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myProfilerServices, myTimer);
    myProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    myView = new SessionProfilersView(myProfilers, new FakeIdeProfilerComponents(), myDisposableRule.getDisposable());
    myView.bind(FakeStage.class, FakeStageView::new);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    if (myIsTestingProfileable) {
      // We setup and profile a process, we assume that process has an agent attached by default.
      updateAgentStatus(FAKE_PROCESS.getPid(), DEFAULT_AGENT_ATTACHED_RESPONSE);
    }
    JComponent component = myView.getComponent();
    component.setSize(1024, 450);
    myUi = new FakeUi(component);
  }

  @Test
  public void testSameStageTransition() {
    FakeStage stage = new FakeStage(myProfilers, "Really?", false);
    myProfilers.setStage(stage);
    StageView view = myView.getStageView();

    myProfilers.setStage(stage);
    assertThat(myView.getStageView()).isEqualTo(view);
  }

  @Test
  public void testViewHasNoExceptionsWhenProfilersStop() {
    FakeStage stage = new FakeStage(myProfilers, "Really?", false);
    myProfilers.setStage(stage);
    StageView view = myView.getStageView();

    myProfilers.setStage(stage);
    myProfilers.stop();
    // Make sure no exceptions
  }

  @Test
  public void testMonitorExpansion() {
    myProfilerServices.enableTaskBasedUx(false);

    assumeFalse(myIsTestingProfileable);

    myService.addSession(SESSION_O, SESSION_O_METADATA);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.getSessionsManager().setSession(SESSION_O);
    myUi.layout();

    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);

    List<Point> points = new TreeWalker(myView.getComponent()).descendantStream()
      .filter(d -> d instanceof LineChart)
      .map(c -> myUi.getPosition(c))
      .collect(Collectors.toList());

    // Test that we have the expected number of monitors
    assertThat(points.size()).isEqualTo(2);

    // Test the first monitor goes to cpu profiler
    myUi.mouse.click(points.get(0).x + 1, points.get(0).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(CpuProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the second monitor goes to memory profiler
    myUi.mouse.click(points.get(1).x + 1, points.get(1).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(MainMemoryProfilerStage.class);
    myProfilers.setMonitoringStage();
  }

  @Test
  public void testMonitorTooltip() {
    myProfilerServices.enableTaskBasedUx(false);

    assumeFalse(myIsTestingProfileable);

    myService.addSession(SESSION_O, SESSION_O_METADATA);
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
    assertThat(points.size()).isEqualTo(2);

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
    SessionProfilersView profilersView = new SessionProfilersView(profilers, new FakeIdeProfilerComponents(), myDisposableRule.getDisposable());
    assertThat(profilersView.getSessionsView().getCollapsed()).isTrue();

    // Fake a resize and re-create the StudioProfilerView, the session UI should maintain the previous dimension
    profilersView.getSessionsView().getExpandButton().doClick();
    ThreeComponentsSplitter splitter = (ThreeComponentsSplitter)profilersView.getComponent().getComponent(0);
    assertThat(splitter.getFirstSize()).isEqualTo(SessionsView.getComponentMinimizeSize(true).width);
    splitter.setSize(1024, 450);
    myUi.mouse.drag(splitter.getFirstSize(), 0, 10, 0);

    profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myProfilerServices, myTimer);
    profilersView = new SessionProfilersView(profilers, new FakeIdeProfilerComponents(), myDisposableRule.getDisposable());
    assertThat(profilersView.getSessionsView().getCollapsed()).isFalse();
    assertThat(((ThreeComponentsSplitter)profilersView.getComponent().getComponent(0)).getFirstSize()).isEqualTo(splitter.getFirstSize());
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

  @Parameterized.Parameters
  public static List<Boolean> isTestingProfileable() {
    return Arrays.asList(false, true);
  }
}