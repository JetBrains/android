/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights.ui.actions

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.selectionOf
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
import org.junit.rules.RuleChain

private val ICON = StudioIcons.AppQualityInsights.EARLY_SIGNAL

class AppInsightsDropDownActionTest {

  private val projectRule = ProjectRule()
  private val jbPopupRule = JBPopupRule()

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(jbPopupRule)!!

  enum class TestValues {
    DEFAULT,
    ONE,
    TWO
  }

  @Test
  fun `popup has the right value selected`(): Unit =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableStateFlow(selectionOf(TestValues.ONE))
      val dropdown = AppInsightsDropDownAction("testName", null, null, flow, null) {}

      val fakeUi = initUi(dropdown)

      val testActionEvent = TestActionEvent()
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
        AppInsightsDropDownAction(
          "testName",
          null,
          null,
          flow,
          { value -> if (value == TestValues.TWO) ICON else null }
        ) {}

      val fakeUi = initUi(dropdown)

      val testActionEvent = TestActionEvent()
      dropdown.update(testActionEvent)
      assertThat(testActionEvent.presentation.text).isEqualTo(TestValues.ONE.toString())
      assertThat(testActionEvent.presentation.icon).isNull()

      flow.emit(selectionOf(TestValues.TWO))
      fakeUi.updateToolbars()

      dropdown.update(testActionEvent)
      assertThat(testActionEvent.presentation.text).isEqualTo(TestValues.TWO.toString())
      assertThat(testActionEvent.presentation.icon).isNull()

      dropdown.actionPerformed(testActionEvent)
      assertThat(dropdown.childrenCount).isEqualTo(3)
      for (children in dropdown.getChildren(TestActionEvent())) {
        val presentation = children.templatePresentation
        if (presentation.text == TestValues.TWO.toString())
          assertThat(presentation.icon).isEqualTo(ICON)
        else assertThat(presentation.icon).isNull()
      }
    }

  private fun <T> initUi(dropdown: AppInsightsDropDownAction<T>): FakeUi {
    val panel = JPanel(BorderLayout())
    val fakeUi = FakeUi(panel)
    val actionGroups = DefaultActionGroup().apply { add(dropdown) }
    val toolbar =
      ActionManager.getInstance().createActionToolbar("AppInsights", actionGroups, true).apply {
        targetComponent = panel
      }
    panel.add(toolbar.component, BorderLayout.CENTER)
    toolbar.updateActionsImmediately()
    fakeUi.updateToolbars()
    return fakeUi
  }
}
