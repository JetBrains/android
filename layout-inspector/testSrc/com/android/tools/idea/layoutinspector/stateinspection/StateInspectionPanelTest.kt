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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class StateInspectionPanelTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val chain = RuleChain(projectRule, EdtRule())

  private val model = TestStateInspectionModel()
  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)
  private lateinit var disposable: Disposable

  @Before
  fun before() {
    disposable = projectRule.testRootDisposable
  }

  @Test
  fun testNoEditorCreatedInitially() {
    val panel = StateInspectionPanel(model, projectRule.project, testScope, disposable)
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(panel.componentCount).isEqualTo(0)
    assertThat(panel.getUserData(STATE_READ_EDITOR_KEY)).isNull()
    Disposer.dispose(disposable)
  }

  @Test
  fun testEditorDestroyedWhenHidden() {
    val panel = StateInspectionPanel(model, projectRule.project, testScope, disposable)
    assertThat(panel.componentCount).isEqualTo(0)
    assertThat(panel.getUserData(STATE_READ_EDITOR_KEY)).isNull()
    testDispatcher.scheduler.advanceUntilIdle()

    model.show.value = true
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(panel.componentCount).isEqualTo(1)
    assertThat(panel.getUserData(STATE_READ_EDITOR_KEY)).isNotNull()

    model.show.value = false
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(panel.componentCount).isEqualTo(0)
    assertThat(panel.getUserData(STATE_READ_EDITOR_KEY)).isNull()
  }

  @Test
  fun testRecompositionText() {
    val panel = StateInspectionPanel(model, projectRule.project, testScope, disposable)
    model.show.value = true
    testDispatcher.scheduler.advanceUntilIdle()
    val label = panel.getDescendant<JLabel> { it.name == RECOMPOSITION_TEXT_LABEL_NAME }
    assertThat(label.text).isEqualTo("")

    model.recompositionText.value = "Testing"
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(label.text).isEqualTo("Testing")
  }

  @Test
  fun testStateReadText() {
    val panel = StateInspectionPanel(model, projectRule.project, testScope, disposable)
    model.show.value = true
    testDispatcher.scheduler.advanceUntilIdle()
    val label = panel.getDescendant<JLabel> { it.name == STATE_READ_TEXT_LABEL_NAME }
    assertThat(label.text).isEqualTo("")

    model.stateReadsText.value = "Testing"
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(label.text).isEqualTo("Testing")
  }

  @Test
  fun testStackTraceText() {
    val panel = StateInspectionPanel(model, projectRule.project, testScope, disposable)
    model.show.value = true
    testDispatcher.scheduler.advanceUntilIdle()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val editor = panel.getUserData(STATE_READ_EDITOR_KEY)!!
    assertThat(editor.document.text).isEqualTo("")

    model.stackTraceText.value = "Testing"
    testDispatcher.scheduler.advanceUntilIdle()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(editor.document.text).isEqualTo("Testing")
  }

  @Test
  fun testPrevAction() {
    testButton(model.prevAction)
  }

  @Test
  fun testNextAction() {
    testButton(model.nextAction)
  }

  @Test
  fun testMinimizeAction() {
    testButton(model.minimizeAction)
  }

  private fun testButton(buttonAction: TestAction) {
    val panel = StateInspectionPanel(model, projectRule.project, testScope, disposable)
    model.show.value = true
    testDispatcher.scheduler.advanceUntilIdle()
    val button = panel.getDescendant<ActionButton> { it.action == buttonAction }
    assertThat(button.isEnabled).isFalse()

    buttonAction.enabled = true
    model.updates.value++
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(button.isEnabled).isTrue()

    panel.size = Dimension(600, 800)
    val ui = FakeUi(panel, createFakeWindow = true)
    val point = SwingUtilities.convertPoint(button, 8, 8, panel)
    ui.mouse.focus = button
    ui.mouse.click(point.x, point.y)
    assertThat(buttonAction.performedCount).isEqualTo(1)
    ui.mouse.click(point.x, point.y)
    assertThat(buttonAction.performedCount).isEqualTo(2)

    buttonAction.enabled = false
    model.updates.value++
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(button.isEnabled).isFalse()
  }

  class TestStateInspectionModel : StateInspectionModel {
    override val show = MutableStateFlow(false)
    override val prevAction = TestAction()
    override val recompositionText = MutableStateFlow("")
    override val nextAction = TestAction()
    override val minimizeAction = TestAction()
    override val stateReadsText = MutableStateFlow("")
    override val stackTraceText = MutableStateFlow("")
    override val updates = MutableStateFlow(0)
  }

  class TestAction : AnAction() {
    var enabled = false
    var performedCount = 0
      private set

    override fun actionPerformed(event: AnActionEvent) {
      performedCount++
    }

    override fun update(event: AnActionEvent) {
      event.presentation.isEnabled = enabled
    }
  }
}
