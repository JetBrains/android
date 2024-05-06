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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.sdklib.AndroidVersion;
import com.android.test.testutils.TestUtils;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode;
import com.android.tools.idea.transport.faketransport.FakeGrpcServer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.cpu.CpuCaptureMetadata;
import com.android.tools.profilers.cpu.CpuCaptureStage;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.memory.AllocationStage;
import com.android.tools.profilers.memory.CaptureObjectLoader;
import com.android.tools.profilers.memory.FakeCaptureObjectLoader;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StageNavigationToolbarTest {

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myService;
  private final FakeIdeProfilerServices myProfilerServices = new FakeIdeProfilerServices();
  private final boolean myIsTestingProfileable;
  @Rule public final FakeGrpcServer myGrpcChannel;

  public StageNavigationToolbarTest(boolean isTestingProfileable) {
    myIsTestingProfileable = isTestingProfileable;
    myService = isTestingProfileable
                ? new FakeTransportService(myTimer, true, AndroidVersion.VersionCodes.S, Common.Process.ExposureLevel.PROFILEABLE)
                : new FakeTransportService(myTimer);
    myGrpcChannel = FakeGrpcServer.createFakeGrpcServer("StageNavigationToolbarTestChannel", myService);
  }

  private StudioProfilers myProfilers;

  private StageNavigationToolbar myStageNavigationToolbar;


  @Before
  public void setUp() {
    myProfilerServices.enableEnergyProfiler(true);
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myProfilerServices, myTimer);
    myProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    myStageNavigationToolbar = new StageNavigationToolbar(myProfilers);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    if (myIsTestingProfileable) {
      // We set up and profile a process, we assume that process has an agent attached by default.
      updateAgentStatus(FAKE_PROCESS.getPid());
    }
  }

  /**
   * This method is the same as the setUp method ran before every test, except that it disables/hides
   * the Power Profiler. The FakeIdeProfilerService's default value for the Power Profiler flag enables it,
   * and thus we can trivially test the exclusion of the Energy Profiler option (when Power Profiler is
   * enabled, Energy Profiler is disabled, and vice-versa). But to test if disabling the Power Profiler brings
   * the Energy Profiler option back, we must repeat these set up steps with the Power Profiler disabled.
   */
  private void setUpWithPowerProfilerDisabled() {
    myProfilerServices.enableEnergyProfiler(true);
    myProfilerServices.setSystemTracePowerProfilerDisplayMode(PowerProfilerDisplayMode.HIDE);
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myProfilerServices, myTimer);
    myProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    myStageNavigationToolbar = new StageNavigationToolbar(myProfilers);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    if (myIsTestingProfileable) {
      // We set up and profile a process, we assume that process has an agent attached by default.
      updateAgentStatus(FAKE_PROCESS.getPid());
    }
  }

  @Test
  public void captureCpuStageGoesBackToCpuStageThenBackToMonitorStage() {
    myProfilers.setStage(CpuCaptureStage.create(myProfilers,
                                                ProfilersTestData.DEFAULT_CONFIG,
                                                TestUtils.resolveWorkspacePath(CpuProfilerUITestUtils.VALID_TRACE_PATH).toFile(),
                                                ProfilersTestData.SESSION_DATA.getSessionId()));
    myStageNavigationToolbar.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isInstanceOf(CpuProfilerStage.class);
    assertThat(((CpuProfilerStage)(myProfilers.getStage())).getEntryPoint()).isEqualTo(
      CpuCaptureMetadata.CpuProfilerEntryPoint.CHILD_STAGE_BACK_BTN_OR_FAILURE);
    myStageNavigationToolbar.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  @Test
  public void liveAllocationStageGoesBackToMainMemoryProfilerStage() {
    CaptureObjectLoader fakeLoader = new FakeCaptureObjectLoader();
    AllocationStage stage = AllocationStage.makeLiveStage(myProfilers, fakeLoader);
    myProfilers.setStage(stage);
    stage.stopTracking();
    myStageNavigationToolbar.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isInstanceOf(MainMemoryProfilerStage.class);
  }

  @Test
  public void staticAllocationStageGoesBackToMainMemoryProfilerStage() {
    AllocationStage stage = AllocationStage.makeStaticStage(myProfilers, 1.0, 5.0);
    myProfilers.setStage(stage);
    myStageNavigationToolbar.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isInstanceOf(MainMemoryProfilerStage.class);
  }

  @Test
  public void profilerStaysInStageWhenUserConfirmsStay() {
    setFakeStage();
    myProfilerServices.setShouldProceedYesNoDialog(false);
    myStageNavigationToolbar.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isInstanceOf(FakeStage.class);
  }

  @Test
  public void profilerExitsWhenUserConfirmsExit() {
    setFakeStage();
    myProfilerServices.setShouldProceedYesNoDialog(true);
    myStageNavigationToolbar.getBackButton().doClick();
    assertThat(myProfilers.getStage()).isNotInstanceOf(FakeStage.class);
  }

  @Test
  public void menuShowsSupportedStagesForDebuggable() {
    assumeFalse(myIsTestingProfileable);
    // The Power Profiler is enabled by the FakeIdeProfilerServices by default, and thus the Energy Profiler should not be present.
    menuShowsSupportedStages(CpuProfilerStage.class, MainMemoryProfilerStage.class);
    // When the Power Profiler is disabled, the Energy Profiler should be present.
    setUpWithPowerProfilerDisabled();
    menuShowsSupportedStages(CpuProfilerStage.class, MainMemoryProfilerStage.class, EnergyProfilerStage.class);
  }

  @Test
  public void menuShowsSupportedStagesForProfileable() {
    assumeTrue(myIsTestingProfileable);
    menuShowsSupportedStages(CpuProfilerStage.class, MainMemoryProfilerStage.class);
  }

  private void updateAgentStatus(int pid) {
    long sessionStreamId = myProfilers.getSession().getStreamId();
    myService.addEventToStream(sessionStreamId, Common.Event.newBuilder()
      .setPid(pid)
      .setKind(Common.Event.Kind.AGENT)
      .setAgentData(ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE)
      .build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
  }

  private void menuShowsSupportedStages(Class<?>... expected) {
    TreeWalker t = new TreeWalker(myStageNavigationToolbar);
    Predicate<ComboBoxModel<?>> itemsChecker = model -> model.getSize() == expected.length &&
                                                        IntStream.range(0, model.getSize()).allMatch(
                                                          i -> model.getElementAt(i) instanceof Class<?> &&
                                                               expected[i].isAssignableFrom((Class<?>)model.getElementAt(i)));
    assertThat(
      t.descendantStream().anyMatch(view -> view instanceof JComboBox<?> && itemsChecker.test(((JComboBox<?>)view).getModel()))).isTrue();
  }

  private void setFakeStage() {
    FakeStage stage = new FakeStage(myProfilers, "Really?", false);
    myProfilers.setStage(stage);
  }

  @Parameterized.Parameters
  public static List<Boolean> isTestingProfileable() {
    return Arrays.asList(false, true);
  }
}
