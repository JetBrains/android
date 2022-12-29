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
package com.google.services.firebase.insights.ui

import com.android.tools.adtui.actions.createTestActionEvent
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeComponentPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.services.firebase.insights.AppInsightsModuleControllerRule
import com.google.services.firebase.insights.CrashlyticsInsight
import com.google.services.firebase.insights.ISSUE1
import com.google.services.firebase.insights.ISSUE2
import com.google.services.firebase.insights.analysis.Cause
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.ListWithFilter
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
class AppInsightsGutterIconActionTest(private val insights: List<CrashlyticsInsight>) {
  private val projectRule = AndroidProjectRule.inMemory()
  private val controllerRule = AppInsightsModuleControllerRule(projectRule)
  private val popupRule = JBPopupRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(controllerRule).around(popupRule)!!

  @Test
  fun `gutter popup shows correct information`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val displayPanel = JPanel()
      val gutterIconAction = AppInsightsGutterIconAction(projectRule.project, insights)
      val mouseEvent = MouseEvent(displayPanel, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
      val actionEvent = createTestActionEvent(gutterIconAction, mouseEvent)
      gutterIconAction.actionPerformed(actionEvent)

      val fakeUi =
        FakeUi((popupRule.fakePopupFactory.getPopup<Unit>(0) as FakeComponentPopup).contentPanel)
      val list = fakeUi.findComponent<ListWithFilter<*>>()!!.list as JBList
      with(list.model) {
        assertThat(size).isEqualTo(insights.size)
        insights.forEachIndexed { index, crashlyticsInsight ->
          assertThat(getElementAt(index)).isEqualTo(crashlyticsInsight)
        }
      }

      val coloredComponents =
        fakeUi.findAllComponents<ResizedSimpleColoredComponent> {
          it !is JListSimpleColoredComponent<*>
        }
      val selectAnIssuePanel = coloredComponents[0]
      assertThat(selectAnIssuePanel.toString()).isEqualTo("Select an issue to see details")

      val eventsPanel = coloredComponents[1]
      assertThat(eventsPanel.toString())
        .isEqualTo("${insights.sumOf { it.issue.issueDetails.eventsCount }}")
      val usersPanel = coloredComponents[2]
      assertThat(usersPanel.toString())
        .isEqualTo("${insights.sumOf { it.issue.issueDetails.impactedDevicesCount }}")
    }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: shows correct info for {0}")
    fun data() =
      listOf(
        listOf(CrashlyticsInsight(1, ISSUE1, FRAME1, Cause.Frame(FRAME1)) {}),
        listOf(
          CrashlyticsInsight(1, ISSUE1, FRAME1, Cause.Frame(FRAME1)) {},
          CrashlyticsInsight(1, ISSUE2, FRAME2, Cause.Frame(FRAME2)) {}
        ),
      )
  }
}
