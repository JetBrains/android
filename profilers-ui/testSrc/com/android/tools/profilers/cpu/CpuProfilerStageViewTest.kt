/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.RecordingOptionsView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.event.FakeEventService
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.JBSplitter
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.Point
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities

// Path to trace file. Used in test to build AtraceParser.
private const val TOOLTIP_TRACE_DATA_FILE = "tools/adt/idea/profilers-ui/testData/cputraces/atrace.ctrace"

@RunsInEdt
@RunWith(Parameterized::class)
class CpuProfilerStageViewTest(private val isTestingProfileable: Boolean) {
  private val myTimer = FakeTimer()
  private val myComponents = FakeIdeProfilerComponents()
  private val myIdeServices = FakeIdeProfilerServices()
  private val myTransportService = if (isTestingProfileable) {
    FakeTransportService(myTimer, true, AndroidVersion.VersionCodes.S, Common.Process.ExposureLevel.PROFILEABLE)
  }
  else FakeTransportService(myTimer)

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("CpuCaptureViewTestChannel", myTransportService, FakeEventService())

  @get:Rule
  val myEdtRule = EdtRule()

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private lateinit var myStage: CpuProfilerStage

  private lateinit var myProfilersView: StudioProfilersView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeServices, myTimer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)

    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage
    myStage.enter()
    myProfilersView = StudioProfilersView(profilers, myComponents, disposableRule.disposable)
  }

  @Test
  fun testTooltipComponentIsFirstChild() {
    val cpuProfilerStageView = CpuProfilerStageView(myProfilersView, myStage)
    val treeWalker = TreeWalker(cpuProfilerStageView.component)
    val tooltipComponent = treeWalker.descendants().filterIsInstance(RangeTooltipComponent::class.java)[0]
    assertThat(tooltipComponent.parent.components[0]).isEqualTo(tooltipComponent)
  }

  @Test
  fun recordButtonDisabledInDeadSessions() {
    assumeFalse(isTestingProfileable)
    // Create a valid capture and end the current session afterwards.
    myStage.profilerConfigModel.profilingConfiguration = FakeIdeProfilerServices.ATRACE_CONFIG
    CpuProfilerTestUtils.captureSuccessfully(
      myStage,
      myTransportService,
      CpuProfilerTestUtils.traceFileToByteString(resolveWorkspacePath(TOOLTIP_TRACE_DATA_FILE).toFile()))
    myStage.studioProfilers.sessionsManager.endCurrentSession()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // Re-create the stage so that the capture is not cached
    myStage = CpuProfilerStage(myStage.studioProfilers)
    myStage.studioProfilers.stage = myStage
    myStage.studioProfilers.sessionsManager.endCurrentSession()
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    val recordButton = TreeWalker(stageView.component).descendants().filterIsInstance<JButton>().first {
      it.text == RecordingOptionsView.START
    }
    // When creating the stage view, the record button should be disabled as the current session is dead.
    assertThat(recordButton.isEnabled).isFalse()
  }

  @Test
  fun stoppingAndStartingDisableRecordButton() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    val recordButton = TreeWalker(stageView.component).descendants().filterIsInstance<JButton>().first {
      it.text == RecordingOptionsView.START
    }

    myStage.captureState = CpuProfilerStage.CaptureState.CAPTURING
    // Setting the state to STARTING should disable the recording button
    assertThat(recordButton.text).isEqualTo(RecordingOptionsView.STOP)

  }

  @Test
  fun recordButtonShouldntHaveTooltip() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    val recordButton = TreeWalker(stageView.component).descendants().filterIsInstance<JButton>().first {
      it.text == RecordingOptionsView.START
    }
    assertThat(recordButton.toolTipText).isNull()

    myStage.captureState = CpuProfilerStage.CaptureState.CAPTURING
    val stopButton = TreeWalker(stageView.component).descendants().filterIsInstance<JButton>().first {
      it.text == RecordingOptionsView.STOP
    }
    assertThat(stopButton.toolTipText).isNull()
  }

  /**
   * Checks that the menu items common to all profilers are installed in the CPU profiler context menu.
   */
  @Test
  fun testCommonProfilersMenuItems() {
    // Clear any context menu items added to the service to make sure we'll have only the items created in CpuProfilerStageView
    myComponents.clearContextMenuItems()
    // Create a CpuProfilerStageView. We don't need its value, so we don't store it in a variable.
    CpuProfilerStageView(myProfilersView, myStage)

    val expectedCommonMenus = listOf(StudioProfilersView.ATTACH_LIVE,
                                     StudioProfilersView.DETACH_LIVE,
                                     ContextMenuItem.SEPARATOR.text,
                                     StudioProfilersView.ZOOM_IN,
                                     StudioProfilersView.ZOOM_OUT)
    val items = myComponents.allContextMenuItems
    // CPU specific menus should be added.
    assertThat(items.size).isGreaterThan(expectedCommonMenus.size)

    val itemsSuffix = items.subList(items.size - expectedCommonMenus.size, items.size)
    assertThat(itemsSuffix.map { it.text }).containsExactlyElementsIn(expectedCommonMenus).inOrder()
  }

  @Test
  fun instructionsPanelIsTheFirstComponentOfUsageView() {
    val usageView = getUsageView(CpuProfilerStageView(myProfilersView, myStage))
    assertThat(usageView.getComponent(0)).isInstanceOf(InstructionsPanel::class.java)
  }

  @Test
  fun showsTooltipSeekComponentWhenMouseIsOverUsageView() {
    assumeFalse(isTestingProfileable)
    val stageView = CpuProfilerStageView(myProfilersView, myStage)

    val instructions = TreeWalker(stageView.component).descendants().filterIsInstance<InstructionsPanel>().first()
    instructions.isVisible = false // Hide instructions as they otherwise block the components we want to test

    stageView.component.setBounds(0, 0, 500, 500)
    val ui = FakeUi(stageView.component)

    val usageView = getUsageView(stageView)
    val usageViewPos = ui.getPosition(usageView)
    assertThat(usageViewPos.y).isGreaterThan(0)

    assertThat(stageView.shouldShowTooltipSeekComponent()).isFalse()
    // Move into |CpuUsageView|
    ui.mouse.moveTo(usageViewPos.x + usageView.width / 2, usageViewPos.y + usageView.height / 2)
    assertThat(stageView.shouldShowTooltipSeekComponent()).isTrue()
  }

  @Test
  fun tooltipIsUsageTooltipWhenMouseIsOverUsageView() {
    assumeFalse(isTestingProfileable)
    val stageView = CpuProfilerStageView(myProfilersView, myStage)

    stageView.component.setBounds(0, 0, 500, 500)
    val ui = FakeUi(stageView.component)

    val usageView = getUsageView(stageView)
    val usageViewOrigin = SwingUtilities.convertPoint(usageView, Point(0, 0), stageView.component)
    assertThat(usageViewOrigin.y).isGreaterThan(0)

    assertThat(myStage.tooltip).isNull()
    // Move into |CpuUsageView|
    ui.mouse.moveTo(usageViewOrigin.x, usageViewOrigin.y)
    assertThat(myStage.tooltip).isInstanceOf(CpuProfilerStageCpuUsageTooltip::class.java)
  }

  @Test
  fun showsCpuCaptureViewAlwaysOnNewRecordingWorkflow() {
    // We're not reusing |myStage| because we want to test the stage with the enabled feature flag.
    // Once the new recording workflow is stable and the flag is removed, we should remove the entire block.
    run {
      myStage = CpuProfilerStage(myStage.studioProfilers)
      myStage.enter()
    }
    val stageView = CpuProfilerStageView(myProfilersView, myStage)

    val splitter = TreeWalker(stageView.component).descendants().filterIsInstance<JBSplitter>().first()
    assertThat(splitter.secondComponent).isNotNull()
  }

  @Test
  fun `disabled event banner not shown in profileable process`() {
    assumeTrue(isTestingProfileable)
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    assertThat(TreeWalker(stageView.component).descendantStream()
                 .noneMatch { it is JLabel && it.text != null && it.text.contains("Additional profiling support is unavailable") })
      .isTrue()
  }

  private fun getUsageView(stageView: CpuProfilerStageView) = TreeWalker(stageView.component)
    .descendants()
    .filterIsInstance<CpuUsageView>()
    .first()

  companion object {
    @Parameterized.Parameters
    @JvmStatic
    fun isTestingProfileable() = listOf(false, true) // whether process is profileable/debuggable, regardless of feature flag
  }
}