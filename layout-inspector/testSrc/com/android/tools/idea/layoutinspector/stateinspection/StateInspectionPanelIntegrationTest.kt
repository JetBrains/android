/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.stateinspection

import com.android.adblib.DeviceSelector
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.COMPOSE3
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.appinspection.FakeInspectorStateReads
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.getUserData
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import icons.StudioIcons
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val MODERN_PROCESS =
  MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)
private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData/stateinspection"

/**
 * Integration test that involves: [StateInspectionPanel], [StateInspectionModel],
 * [com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient],
 * and
 * [com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecompositionStateReadCache].
 */
@RunsInEdt
class StateInspectionPanelIntegrationTest {
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val inspectionRule = AppInspectionInspectorRule(projectRule)
  private val inspectorRule =
    LayoutInspectorRule(listOf(inspectionRule.createInspectorClientProvider()), projectRule) {
      it.name == MODERN_PROCESS.name
    }

  @get:Rule val rule = RuleChain(projectRule, inspectionRule, inspectorRule, EdtRule())

  @Before
  fun before() {
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectionRule.adbSession.deviceServices.configureShellCommand(
      DeviceSelector.fromSerialNumber(MODERN_DEVICE.serial),
      "settings get global debug_view_attributes",
      stdout = "1",
    )
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()
  }

  @Test
  fun testPanelWithStateReadsForAll() {
    inspectorRule.inspector.treeSettings.observeStateReadsForAll = true
    val state = FakeInspectorStateReads(inspectionRule.composeInspector)
    state.createFakeStateReadsForAll()

    val panel = createPanel(COMPOSE1)
    val ui = FakeUi(panel, createFakeWindow = true)
    val prev = panel.buttonWithIcon(StudioIcons.LayoutEditor.Motion.PREVIOUS_TICK)
    val next = panel.buttonWithIcon(StudioIcons.LayoutEditor.Motion.NEXT_TICK)
    val minimize = panel.buttonWithIcon(AllIcons.General.HideToolWindow)
    val recompositionText = panel.getDescendant<JLabel> { it.name == RECOMPOSITION_TEXT_LABEL_NAME }

    waitForCondition(10.seconds) { recompositionText.text == "Recomposition 2" }
    panel.checkContent("state_reads_1_2.txt")
    assertThat(prev.isEnabled).isTrue()
    assertThat(next.isEnabled).isFalse()

    ui.click(prev)
    waitForCondition(10.seconds) { recompositionText.text == "Recomposition 1" }
    panel.checkContent("state_reads_1_1.txt")
    assertThat(prev.isEnabled).isFalse()
    assertThat(next.isEnabled).isTrue()

    ui.click(next)
    waitForCondition(10.seconds) { recompositionText.text == "Recomposition 2" }
    panel.checkContent("state_reads_1_2.txt")
    assertThat(prev.isEnabled).isTrue()
    assertThat(next.isEnabled).isFalse()
    assertThat(SwingUtilities.isDescendingFrom(recompositionText, panel)).isTrue()

    assertThat(minimize.isEnabled).isTrue()
    ui.click(minimize)
    waitForCondition(10.seconds) { !panel.isVisible }
    assertThat(SwingUtilities.isDescendingFrom(recompositionText, panel)).isFalse()
  }

  @Test
  fun testPanelWithStateReadsOnDemand() {
    inspectorRule.inspector.treeSettings.observeStateReadsForAll = false
    val state = FakeInspectorStateReads(inspectionRule.composeInspector)
    state.createFakeStateReadsForOnDemand()

    val panel = createPanel(COMPOSE1)
    val compose1 = inspectorRule.inspectorModel[COMPOSE1]!!
    val ui = FakeUi(panel, createFakeWindow = true)
    val prev = panel.buttonWithIcon(StudioIcons.LayoutEditor.Motion.PREVIOUS_TICK)
    val next = panel.buttonWithIcon(StudioIcons.LayoutEditor.Motion.NEXT_TICK)
    val minimize = panel.buttonWithIcon(AllIcons.General.HideToolWindow)
    val recompositionText = panel.getDescendant<JLabel> { it.name == RECOMPOSITION_TEXT_LABEL_NAME }
    val stateReadText = panel.getDescendant<JLabel> { it.name == STATE_READ_TEXT_LABEL_NAME }

    waitForCondition(10.seconds) { recompositionText.text == "Waiting for interactions" }
    assertThat(stateReadText.text).isEqualTo("")
    panel.checkContent("")
    assertThat(prev.isEnabled).isFalse()
    assertThat(next.isEnabled).isFalse()

    compose1.recompositions.count = 4
    state.sendEventForRecomposition3()
    waitForCondition(10.seconds) { recompositionText.text == "Recomposition 3" }
    assertThat(stateReadText.text).isEqualTo("State Reads: 1")
    panel.checkContent("state_reads_1_1.txt")
    assertThat(prev.isEnabled).isFalse()
    assertThat(next.isEnabled).isTrue()

    ui.click(next)
    waitForCondition(10.seconds) { recompositionText.text == "Recomposition 4" }
    assertThat(stateReadText.text).isEqualTo("State Reads: 2")
    panel.checkContent("state_reads_1_2.txt")
    assertThat(prev.isEnabled).isTrue()
    assertThat(next.isEnabled).isFalse()

    ui.click(prev)
    waitForCondition(10.seconds) { recompositionText.text == "Recomposition 3" }
    assertThat(stateReadText.text).isEqualTo("State Reads: 1")
    panel.checkContent("state_reads_1_1.txt")
    assertThat(prev.isEnabled).isFalse()
    assertThat(next.isEnabled).isTrue()
    assertThat(SwingUtilities.isDescendingFrom(recompositionText, panel)).isTrue()

    assertThat(minimize.isEnabled).isTrue()
    ui.click(minimize)
    waitForCondition(10.seconds) { !panel.isVisible }
    assertThat(SwingUtilities.isDescendingFrom(recompositionText, panel)).isFalse()
  }

  private fun StateInspectionPanel.checkContent(dataFile: String) {
    var expectedText = ""
    if (dataFile.isNotEmpty()) {
      val file = "${TEST_DATA_PATH}/$dataFile"
      expectedText = TestUtils.resolveWorkspacePathUnchecked(file).readText()
    }
    val editor = getUserData(STATE_READ_EDITOR_KEY)!!
    waitForCondition(10.seconds) { editor.document.text == expectedText }
  }

  private fun StateInspectionPanel.buttonWithIcon(icon: Icon): ActionButton =
    getDescendant<ActionButton> { it.action.templatePresentation.icon == icon }

  private fun FakeUi.click(button: ActionButton) {
    val point = SwingUtilities.convertPoint(button, 8, 8, this.root)
    mouse.click(point.x, point.y)
  }

  private fun createPanel(node: Long): StateInspectionPanel {
    val model = inspectorRule.inspectorModel
    val window =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        compose(COMPOSE1, "Column", composeCount = 2, composeFilename = "MainActivity.kt") {
          compose(COMPOSE2, "Button", composeCount = 2) {
            compose(COMPOSE3, "Text", composeCount = 0)
          }
        }
      }
    model.update(window, listOf(ROOT), 0)
    val panel = createStateInspectionPanel(inspectorRule.inspector, projectRule.testRootDisposable)
    panel.size = Dimension(800, 600)
    model.stateReadsNode = model[node]
    return panel
  }
}
