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

import com.android.testutils.waitForCondition
import com.android.tools.adtui.common.AdtUiUtils.getActionMask
import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.layoutinspector.FakeSessionStats
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.jetbrains.annotations.NonNls
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class StateInspectionPanelTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val fileOpenRule = FileOpenCaptureRule(projectRule)

  @get:Rule val chain = RuleChain(projectRule, fileOpenRule, EdtRule())

  private val model = TestStateInspectionModel()
  private val stats = FakeSessionStats()
  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)
  private lateinit var disposable: Disposable

  @Before
  fun before() {
    disposable = projectRule.testRootDisposable
  }

  @Test
  fun testNoEditorCreatedInitially() {
    val panel = StateInspectionPanel(model, projectRule.project, stats, testScope, disposable)
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(panel.componentCount).isEqualTo(0)
    assertThat(panel.getUserData(STATE_READ_EDITOR_KEY)).isNull()
    Disposer.dispose(disposable)
  }

  @Test
  fun testEditorDestroyedWhenHidden() {
    val panel = StateInspectionPanel(model, projectRule.project, stats, testScope, disposable)
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
    val panel = StateInspectionPanel(model, projectRule.project, stats, testScope, disposable)
    model.show.value = true
    testDispatcher.scheduler.advanceUntilIdle()
    val label = panel.getDescendant<JLabel> { it.name == RECOMPOSITION_TEXT_LABEL_NAME }
    assertThat(label.text).isEqualTo("")

    model.recompositionText.value = "Testing"
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(label.text).isEqualTo("Testing")
  }

  @Test
  fun testEmptyStateText() {
    val panel = StateInspectionPanel(model, projectRule.project, stats, testScope, disposable)
    model.show.value = true
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(panel.findDescendant<EmptyStatePanel>()).isNull()

    model.emptyStateText.value = "Hello\nWorld"
    testDispatcher.scheduler.advanceUntilIdle()
    val emptyState = panel.getDescendant<EmptyStatePanel>()
    assertThat(emptyState.reasonText).isEqualTo("Hello World")

    model.emptyStateText.value = ""
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(panel.findDescendant<EmptyStatePanel>()).isNull()
  }

  @Test
  fun testStateReadText() {
    val panel = StateInspectionPanel(model, projectRule.project, stats, testScope, disposable)
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
    val panel = StateInspectionPanel(model, projectRule.project, stats, testScope, disposable)
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

  @Test
  fun testStateInspectionData() {
    val panel = StateInspectionPanel(model, projectRule.project, stats, testScope, disposable)
    model.show.value = true
    testDispatcher.scheduler.advanceUntilIdle()
    val editor = panel.getUserData(STATE_READ_EDITOR_KEY)!!
    assertThat(editor.getUserData(LAYOUT_INSPECTOR_COMPOSABLE_INSPECTED_KEY)).isNull()

    val data = ComposableDefinition("composable", "MyFile.kt")
    model.composableInspected.value = data
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(editor.getUserData(LAYOUT_INSPECTOR_COMPOSABLE_INSPECTED_KEY)).isEqualTo(data)
  }

  private fun testButton(buttonAction: TestAction) {
    val panel = StateInspectionPanel(model, projectRule.project, stats, testScope, disposable)
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

  @Test
  fun testFocusTraversal() {
    val container = JPanel(BorderLayout())
    val button = JButton()
    button.isFocusable = true
    container.add(button, BorderLayout.NORTH)
    val panel = StateInspectionPanel(model, projectRule.project, stats, testScope, disposable)
    model.show.value = true
    model.prevAction.enabled = true
    model.nextAction.enabled = true
    model.minimizeAction.enabled = true
    testDispatcher.scheduler.advanceUntilIdle()
    container.add(panel, BorderLayout.CENTER)
    val prev = panel.getDescendant<ActionButton> { it.action == model.prevAction }
    val next = panel.getDescendant<ActionButton> { it.action == model.nextAction }
    val minimize = panel.getDescendant<ActionButton> { it.action == model.minimizeAction }
    val editor = panel.getDescendant<JTextComponent>()

    container.size = Dimension(800, 600)
    val ui = FakeUi(container, createFakeWindow = true)
    val focusManager = FakeKeyboardFocusManager(disposable)
    button.requestFocus()

    // Transfer focus forward
    assertThat(focusManager.focusOwner).isSameAs(button)
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB)
    assertThat(focusManager.focusOwner).isSameAs(prev)
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB)
    assertThat(focusManager.focusOwner).isSameAs(next)
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB)
    assertThat(focusManager.focusOwner).isSameAs(minimize)
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB)
    assertThat(focusManager.focusOwner).isSameAs(editor)
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB)
    assertThat(focusManager.focusOwner).isSameAs(button)

    // Transfer focus backward
    ui.keyboard.backTab()
    assertThat(focusManager.focusOwner).isSameAs(editor)
    ui.keyboard.backTab()
    assertThat(focusManager.focusOwner).isSameAs(minimize)
    ui.keyboard.backTab()
    assertThat(focusManager.focusOwner).isSameAs(next)
    ui.keyboard.backTab()
    assertThat(focusManager.focusOwner).isSameAs(prev)
    ui.keyboard.backTab()
    assertThat(focusManager.focusOwner).isSameAs(button)
  }

  @Test
  fun testActiveContent() {
    // Necessary to properly update toolbar button states.
    HeadlessDataManager.fallbackToProductionDataManager(disposable)

    installFakeExtensionPoints(projectRule.testRootDisposable)
    projectRule.fixture.addFileToProject("src/com/example/recompositiontest/MainActivity.kt", "")
    val project = projectRule.project
    val detectorFactory = SynchronousHyperLinkDetectorFactory()
    val panel = StateInspectionPanel(model, project, stats, testScope, disposable, detectorFactory)
    model.show.value = true
    model.prevAction.enabled = true
    model.nextAction.enabled = true
    model.stackTraceText.value =
      """
      State read value: [b, c] <invalidated> (Explain with AI)
          at com.example.recompositiontest.MainActivityKt.Item(MainActivity.kt:60)

    """
        .trimIndent()
    testDispatcher.scheduler.advanceUntilIdle()
    val prev = panel.getDescendant<ActionButton> { it.action == model.prevAction }
    val next = panel.getDescendant<ActionButton> { it.action == model.nextAction }
    val editor = panel.getUserData(STATE_READ_EDITOR_KEY)!!
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(
      object : DataProvider {
        override fun getData(dataId: @NonNls String): Any? {
          if (CommonDataKeys.EDITOR.`is`(dataId)) {
            return editor
          }
          if (CommonDataKeys.PROJECT.`is`(dataId)) {
            return projectRule.project
          }
          return null
        }
      },
      disposable,
    )

    // Pressing space button on prev action causes the action to be performed:
    panel.size = Dimension(800, 600)
    val ui = FakeUi(panel, createFakeWindow = true)
    val focusManager = FakeKeyboardFocusManager(disposable)
    prev.requestFocus()
    assertThat(focusManager.focusOwner).isSameAs(prev)
    ui.keyboard.pressAndRelease(KeyEvent.VK_SPACE)
    ui.keyboard.pressAndRelease(KeyEvent.VK_SPACE)
    ui.keyboard.pressAndRelease(KeyEvent.VK_SPACE)
    assertThat(model.prevAction.performedCount).isEqualTo(3)

    // Auto transfer focus away from disabled prev button:
    model.prevAction.enabled = false
    model.updates.value += 1
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(focusManager.focusOwner).isSameAs(next)

    // Activate a link in the editor:
    waitForCondition(10.seconds) { editor.markupModel.allHighlighters.size == 3 }
    validateMarkupModel(editor.markupModel) {
      region(1, "<invalidated>")
      region(1, "(Explain with AI)")
      region(2, "MainActivity.kt:60")
    }
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB)
    assertThat(focusManager.focusOwner).isSameAs(editor.contentComponent)

    val text = editor.document.text
    val offset = text.indexOf("MainActivity.kt:60")
    editor.caretModel.moveToOffset(offset + 2)
    val queue = IdeEventQueue.getInstance()

    // The action shortcuts are handled by the IdeEventQueue not the standard keyboard listeners:
    queue.pressAndRelease(editor.contentComponent, KeyEvent.VK_B, getActionMask())
    fileOpenRule.checkEditorOpened("MainActivity.kt", true)
  }

  class TestStateInspectionModel : StateInspectionModel {
    override val show = MutableStateFlow(false)
    override val prevAction = TestAction()
    override val recompositionText = MutableStateFlow("")
    override val emptyStateText = MutableStateFlow("")
    override val nextAction = TestAction()
    override val minimizeAction = TestAction()
    override val stateReadsText = MutableStateFlow("")
    override val stackTraceText = MutableStateFlow("")
    override val updates = MutableStateFlow(0)
    override val composableInspected = MutableStateFlow<ComposableDefinition?>(null)
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

  private fun FakeKeyboard.backTab() {
    press(KeyEvent.VK_SHIFT)
    pressAndRelease(KeyEvent.VK_TAB)
    release(KeyEvent.VK_SHIFT)
  }

  private fun IdeEventQueue.pressAndRelease(component: Component, keyCode: Int, modifiers: Int) {
    val press =
      KeyEvent(
        component,
        KeyEvent.KEY_PRESSED,
        System.nanoTime(),
        modifiers,
        keyCode,
        keyCode.toChar(),
      )
    val release =
      KeyEvent(
        component,
        KeyEvent.KEY_RELEASED,
        System.nanoTime(),
        modifiers,
        keyCode,
        keyCode.toChar(),
      )
    dispatchEvent(press)
    dispatchEvent(release)
  }
}
