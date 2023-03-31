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
package com.android.tools.idea.insights.ui.actions

import com.android.tools.adtui.actions.createTestActionEvent
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeComponentPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsight
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.insights.analysis.Cause
import com.google.common.truth.Truth
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.ListWithFilter
import com.jetbrains.rd.util.first
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private val FRAME1 = ISSUE1.sampleEvent.stacktraceGroup.exceptions.first().stacktrace.frames.first()
private val FRAME2 = ISSUE2.sampleEvent.stacktraceGroup.exceptions.first().stacktrace.frames.first()

@RunWith(value = Parameterized::class)
class AppInsightsGutterIconActionTest(private val insights: Map<String, List<AppInsight>>) {
  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)
  private val popupRule = JBPopupRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(controllerRule).around(popupRule)!!

  @Test
  fun `gutter popup shows correct information`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val displayPanel = JPanel()
      val gutterIconAction = AppInsightsGutterIconAction(insights) { _, _ -> }
      val mouseEvent = MouseEvent(displayPanel, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
      val actionEvent = createTestActionEvent(gutterIconAction, mouseEvent)
      gutterIconAction.actionPerformed(actionEvent)

      val fakeUi =
        FakeUi((popupRule.fakePopupFactory.getPopup<Unit>(0) as FakeComponentPopup).contentPanel)
      val list = fakeUi.findComponent<ListWithFilter<*>>()!!.list as JBList
      with(list.model) {
        Truth.assertThat(size)
          .isEqualTo(
            insights.flatMap { it.value }.size + insights.size + maxOf(insights.size - 1, 0)
          )
        var checkedIndex = 0
        insights.forEach { (group, insights) ->
          if (checkedIndex != 0) {
            Truth.assertThat(getElementAt(checkedIndex)).isEqualTo(SeparatorInstruction)
            checkedIndex++
          }
          Truth.assertThat(getElementAt(checkedIndex)).isEqualTo(HeaderInstruction(group))
          checkedIndex++
          insights.forEach { insight ->
            Truth.assertThat(getElementAt(checkedIndex))
              .isEqualTo(InsightInstruction(insight, group))
            checkedIndex++
          }
        }
      }

      val coloredComponents =
        fakeUi.findAllComponents<ResizedSimpleColoredComponent> {
          it !is JListSimpleColoredComponent<*>
        }
      val selectAnIssuePanel = coloredComponents[0]
      Truth.assertThat(selectAnIssuePanel.toString()).isEqualTo("Select an issue to see details")

      if (insights.size == 1) {
        val eventsPanel = coloredComponents[1]
        Truth.assertThat(eventsPanel.toString())
          .isEqualTo("${insights.first().value.sumOf { it.issue.issueDetails.eventsCount }}")
        val usersPanel = coloredComponents[2]
        Truth.assertThat(usersPanel.toString())
          .isEqualTo(
            "${insights.first().value.sumOf { it.issue.issueDetails.impactedDevicesCount }}"
          )
      }
    }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: shows correct info for {0}")
    fun data() =
      listOf(
        mapOf("Firebase" to listOf(AppInsight(1, ISSUE1, FRAME1, Cause.Frame(FRAME1)) {})),
        mapOf("Firebase" to listOf(AppInsight(1, ISSUE1, FRAME1, Cause.Frame(FRAME1)) {})),
        mapOf(
          "Firebase" to
            listOf(
              AppInsight(1, ISSUE1, FRAME1, Cause.Frame(FRAME1)) {},
              AppInsight(1, ISSUE2, FRAME2, Cause.Frame(FRAME2)) {}
            )
        ),
        mapOf(
          "Firebase" to
            listOf(
              AppInsight(1, ISSUE1, FRAME1, Cause.Frame(FRAME1)) {},
              AppInsight(1, ISSUE2, FRAME2, Cause.Frame(FRAME2)) {}
            ),
          "Vitals" to
            listOf(
              AppInsight(1, ISSUE1, FRAME1, Cause.Frame(FRAME1)) {},
              AppInsight(1, ISSUE2, FRAME2, Cause.Frame(FRAME2)) {}
            )
        )
      )
  }
}
