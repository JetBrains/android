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
package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils
import com.android.tools.profilers.cpu.capturedetails.CpuTreeNodeTest.TopDownTest.Companion.newNode
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JTree

class TopDownDetailsViewTest {
  private val timer = FakeTimer()

  @JvmField
  @Rule
  val grpcChannel = FakeGrpcChannel("TopDownDetailsViewTest", FakeTransportService(timer))

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private lateinit var profilersView: StudioProfilersView
  private val capture = CpuProfilerUITestUtils.validCapture()

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices(), timer)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
  }

  @Test
  fun showsNoDataForThreadMessageWhenNodeIsEmpty() {
    val topDown = CaptureDetails.Type.TOP_DOWN.build(ClockType.GLOBAL, Range(), emptyList(), capture, Utils::runOnUi)
      as CaptureDetails.TopDown
    val topDownView = TreeDetailsView.TopDownDetailsView(profilersView, topDown)

    val noDataInstructions = TreeWalker(topDownView.component).descendants().filterIsInstance<InstructionsPanel>().first {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction
      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_THREAD_MESSAGE
    }
    assertThat(noDataInstructions.isVisible).isTrue()
  }

  @Test
  fun showsContentWhenNodeIsNotNull() {
    val topDown = CaptureDetails.Type.TOP_DOWN.build(ClockType.GLOBAL, Range(),
                                                     listOf(capture.getCaptureNode(capture.mainThreadId)!!),
                                                     capture, Utils::runOnUi) as CaptureDetails.TopDown
    val topDownView = TreeDetailsView.TopDownDetailsView(profilersView, topDown)

    val noDataInstructionsList = TreeWalker(topDownView.component).descendants().filterIsInstance<InstructionsPanel>().filter {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction
      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_THREAD_MESSAGE
    }
    assertThat(noDataInstructionsList).isEmpty()

    val tree = TreeWalker(topDownView.component).descendants().filterIsInstance<JTree>().first()
    assertThat(tree.isVisible).isTrue()
  }

  @Test
  fun showsNoDataForRangeMessage() {
    // Select a range where we don't have trace data
    val range = Range(Double.MAX_VALUE - 10, Double.MAX_VALUE - 5)
    val topDown = CaptureDetails.Type.TOP_DOWN.build(ClockType.GLOBAL, range,
                                                     listOf(capture.getCaptureNode(capture.mainThreadId)!!),
                                                     capture, Utils::runOnUi) as CaptureDetails.TopDown
    val topDownView = TreeDetailsView.TopDownDetailsView(profilersView, topDown)

    val noDataInstructions = TreeWalker(topDownView.component).descendants().filterIsInstance<InstructionsPanel>().first {
      val textInstruction = it.getRenderInstructionsForComponent(0)[0] as TextInstruction
      textInstruction.text == CaptureDetailsView.NO_DATA_FOR_RANGE_MESSAGE
    }
    assertThat(noDataInstructions.isVisible).isTrue()
  }

  @Test
  fun rootIsHiddenOnInvalidNodeId() {
    val range = Range(Double.MAX_VALUE - 10, Double.MAX_VALUE - 5)
    val topDown = CaptureDetails.Type.TOP_DOWN.build(ClockType.GLOBAL, range,
                                                     listOf(newNode("", 0, 10)), capture, Utils::runOnUi) as CaptureDetails.TopDown
    val topDownView = TreeDetailsView.TopDownDetailsView(profilersView, topDown)
    val tree = TreeWalker(topDownView.component).descendants().filterIsInstance<JTree>().first()
    assertThat(tree.isRootVisible).isFalse()
  }

  @Test
  fun maintainsExpandedStateWhenRangeChanges() {
    val range = Range(capture.range)
    val topDown = CaptureDetails.Type.TOP_DOWN.build(ClockType.GLOBAL, range,
                                                     listOf(capture.getCaptureNode(capture.mainThreadId)!!),
                                                     capture, Utils::runOnUi) as CaptureDetails.TopDown
    val topDownView = TreeDetailsView.TopDownDetailsView(profilersView, topDown)
    val tree = TreeWalker(topDownView.component).descendants().filterIsInstance<JTree>().first()
    assertThat(tree.isVisible).isTrue()
    val treePath = tree.getExpandedDescendants(tree.getPathForRow(0)).iterator().next()
    assertThat(tree.isExpanded(treePath)).isTrue()
    range.shift(TimeUnit.MILLISECONDS.toMicros(100).toDouble())
    assertThat(tree.isExpanded(treePath)).isTrue()
  }
}