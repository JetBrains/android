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
package com.android.tools.profilers

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

private val ICON = StudioIcons.Profiler.Sessions.ALLOCATIONS

class ProfilerDropDownActionTest {

  @get:Rule
  val projectRule = ProjectRule()

  enum class TestValues {
    DEFAULT,
    ONE,
    TWO,
  }

  @Test
  fun `popup has the right value selected`(): Unit =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableStateFlow(selectionOf(TestValues.ONE))
      val dropdown = ProfilerDropDownAction("testName", null, null, flow, null, {})

      val fakeUi = initUi(dropdown)

      val testActionEvent = TestActionEvent.createTestEvent()
      dropdown.update(testActionEvent)
      assertThat(testActionEvent.presentation.text).isEqualTo(TestValues.ONE.toString())
      assertThat(testActionEvent.presentation.icon).isNull()

      flow.emit(selectionOf(TestValues.TWO))
      fakeUi.updateToolbars()

      dropdown.update(testActionEvent)
      assertThat(testActionEvent.presentation.text).isEqualTo(TestValues.TWO.toString())
      assertThat(testActionEvent.presentation.icon).isNull()
    }

  @Test
  fun `icons for entries are correctly set when available`(): Unit =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableStateFlow(selectionOf(TestValues.ONE))
      val dropdown =
        ProfilerDropDownAction(
          "testName",
          null,
          null,
          flow,
          { value -> if (value == TestValues.TWO) ICON else null },
          {},
        )

      initUi(dropdown)

      val testActionEvent = TestActionEvent.createTestEvent()
      // Performing the action populates the dropdown's children by calling updateActions internally.
      dropdown.actionPerformed(testActionEvent)
      assertThat(dropdown.childrenCount).isEqualTo(3)
      for (children in dropdown.getChildren(TestActionEvent.createTestEvent())) {
        val presentation = children.templatePresentation
        if (presentation.text == TestValues.TWO.toString()) {
          assertThat(presentation.icon).isEqualTo(ICON)
        }
        else {
          assertThat(presentation.icon).isNull()
        }
      }
    }

  private fun <T> initUi(dropdown: ProfilerDropDownAction<T>): FakeUi {
    val panel = JPanel(BorderLayout())
    val fakeUi = FakeUi(panel)
    val actionGroups = DefaultActionGroup().apply { add(dropdown) }
    val toolbar =
      ActionManager.getInstance().createActionToolbar("ProfilerTest", actionGroups, true).apply {
        targetComponent = panel
      }
    panel.add(toolbar.component, BorderLayout.CENTER)
    fakeUi.updateToolbars()
    return fakeUi
  }
}