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
package com.android.tools.idea.vitals.ui

import ai.grazie.utils.mpp.runBlocking
import com.android.testutils.time.FakeClock
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.DEFAULT_FETCHED_DEVICES
import com.android.tools.idea.insights.DEFAULT_FETCHED_OSES
import com.android.tools.idea.insights.DEFAULT_FETCHED_PERMISSIONS
import com.android.tools.idea.insights.DEFAULT_FETCHED_VERSIONS
import com.android.tools.idea.insights.DetailedIssueStats
import com.android.tools.idea.insights.FAKE_6_DAYS_AGO
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE1_DETAILS
import com.android.tools.idea.insights.IssueStats
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.analytics.TestAppInsightsTracker
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.ui.AppInsightsIssuesTableView
import com.android.tools.idea.insights.ui.DistributionPanel
import com.android.tools.idea.insights.ui.DistributionsContainerPanel
import com.android.tools.idea.insights.ui.actions.AppInsightsDisplayRefreshTimestampAction
import com.android.tools.idea.insights.ui.actions.AppInsightsDropDownAction
import com.android.tools.idea.insights.ui.actions.TreeDropDownAction
import com.android.tools.idea.insights.ui.dateFormatter
import com.android.tools.idea.insights.waitForCondition
import com.android.tools.idea.vitals.TEST_CONNECTION_1
import com.android.tools.idea.vitals.TEST_CONNECTION_2
import com.android.tools.idea.vitals.TEST_CONNECTION_3
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.SimpleColoredComponent
import icons.StudioIcons
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JProgressBar
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class VitalsTabTest {

  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(controllerRule)

  private val clock: FakeClock
    get() = controllerRule.clock

  private fun FakeUi.findToolbar(): ActionToolbar = this.findComponent()!!

  private fun FakeUi.getConnectionsDropdownComponent() =
    findToolbar().actions[0] as VitalsConnectionSelectorAction

  private fun FakeUi.getTimestampFromLastRefresh(): AppInsightsDisplayRefreshTimestampAction =
    findToolbar().actions.filterIsInstance<AppInsightsDisplayRefreshTimestampAction>().first()

  private fun DistributionPanel.assertContent(issue: IssueStats<Double>, category: String) {
    var index = 0
    issue.groups.forEach { group ->
      assertThat((components[index++] as? JLabel)?.text).isEqualTo(group.groupName)
      assertThat((components[index++] as? JLabel)?.text)
        .isEqualTo("${group.percentage.roundToInt()}%")
      assertThat((components[index++] as? JProgressBar)?.value)
        .isEqualTo(group.percentage.roundToInt())
      assertThat((components[index++] as? JLabel)?.icon).isEqualTo(StudioIcons.Common.INFO)
    }
    assertThat((components[index++] as? JLabel)?.text)
      .isEqualTo("Most affected $category: ${issue.topValue}")
    assertThat(index).isEqualTo(componentCount)
  }

  private fun createTab() =
    VitalsTab(controllerRule.controller, projectRule.project, clock, TestAppInsightsTracker).also {
      Disposer.register(controllerRule.disposable, it)
    }

  @Test
  fun `tab shows correct information on startup`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val tab = createTab()

      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS
          )
        ),
        detailsState = LoadingState.Ready(ISSUE1_DETAILS),
        connectionsState = listOf(TEST_CONNECTION_1, TEST_CONNECTION_2, TEST_CONNECTION_3)
      )

      val fakeUi = FakeUi(tab)

      // Wait for the stacktrace to render. It's a good indicator for when the UI is ready to be
      // checked.
      waitForCondition(5000) {
        val consoleView = fakeUi.findComponent<ConsoleViewImpl>()!!
        consoleView.text.trim() ==
          """
        retrofit2.HttpException: HTTP 401 
            dev.firebase.appdistribution.api_service.ResponseWrapper${"$"}Companion.build(ResponseWrapper.kt:23)
            dev.firebase.appdistribution.api_service.ResponseWrapper${"$"}Companion.fetchOrError(ResponseWrapper.kt:31)
      """
            .trimIndent()
      }
      // Also wait for the details panel to have some content. It should come quickly after the
      // above check.
      waitForCondition {
        // Make sure each DistributionPanel has a JProgressBar
        fakeUi.findAllComponents<DistributionPanel>().all {
          it.findDescendant<JProgressBar>() != null
        }
      }

      // Check the toolbar.
      val actionEvent = TestActionEvent.createTestEvent()
      fakeUi.getConnectionsDropdownComponent().update(actionEvent)
      assertThat(actionEvent.presentation.text)
        .isEqualTo("${TEST_CONNECTION_1.displayName} [${TEST_CONNECTION_1.appId}]")

      val dropDownPresentations =
        listOf(
          "Last 30 days",
          "All visibility",
          "All versions",
          "All devices",
          "All operating systems"
        )
      fakeUi
        .findToolbar()
        .actions
        .filter { it is AppInsightsDropDownAction<*> || it is TreeDropDownAction<*, *> }
        .forEachIndexed { index, action ->
          action.update(actionEvent)
          assertThat(actionEvent.presentation.text).isEqualTo(dropDownPresentations[index])
        }

      assertThat(fakeUi.getTimestampFromLastRefresh().displayText).startsWith("Last refreshed:")

      // Table
      val table = fakeUi.findComponent<AppInsightsIssuesTableView.IssuesTableView>()!!
      assertThat(table.rowCount).isEqualTo(1)
      assertThat(table.getRow(0)).isEqualTo(ISSUE1)

      // Stack trace view
      // Header row
      assertThat(fakeUi.findComponent<SimpleColoredComponent> { it.toString() == "crash.Crash" })
        .isNotNull()

      // events count, user count, api range, device model
      val firstRow =
        fakeUi
          .findComponent<JLabel> {
            it.icon == StudioIcons.AppQualityInsights.ISSUE && it.text == "50"
          }!!
          .parent
      val firstRowComponents = firstRow.components.filter { it !is Box.Filler }
      with(firstRowComponents[0] as JLabel) {
        assertThat(text).isEqualTo("50")
        assertThat(icon).isEqualTo(StudioIcons.AppQualityInsights.ISSUE)
      }
      with(firstRowComponents[1] as JLabel) {
        assertThat(text).isEqualTo("5")
        assertThat(icon).isEqualTo(StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE)
      }
      with(firstRowComponents[2] as JLabel) {
        assertThat(text).isEqualTo("8 → 13")
        assertThat(icon).isEqualTo(StudioIcons.LayoutEditor.Toolbar.ANDROID_API)
      }

      // Device, OS Version, affected versions
      fakeUi.findComponent<JLabel> { it.text == "Versions affected: 1.2.3 → 2.0.0" }

      // Time, link to Vitals
      assertThat(
          fakeUi
            .findComponent<JLabel> { it.icon == StudioIcons.LayoutEditor.Palette.ANALOG_CLOCK }!!
            .text
        )
        .isEqualTo(dateFormatter.format(FAKE_6_DAYS_AGO))

      assertThat(fakeUi.findComponent<HyperlinkLabel>()!!.text).isEqualTo("View on Android Vitals")

      // Stack trace
      val consoleView = fakeUi.findComponent<ConsoleViewImpl>()!!
      assertThat(consoleView.text.trim())
        .isEqualTo(
          """
        retrofit2.HttpException: HTTP 401 
            dev.firebase.appdistribution.api_service.ResponseWrapper${"$"}Companion.build(ResponseWrapper.kt:23)
            dev.firebase.appdistribution.api_service.ResponseWrapper${"$"}Companion.fetchOrError(ResponseWrapper.kt:31)
      """
            .trimIndent()
        )

      // Details panel
      val distributionPanels = fakeUi.findAllComponents<DistributionPanel>()
      assertThat(distributionPanels).hasSize(2)

      val deviceDistribution = distributionPanels[0]
      val osDistribution = distributionPanels[1]

      deviceDistribution.assertContent(ISSUE1_DETAILS.deviceStats, "device")
      osDistribution.assertContent(ISSUE1_DETAILS.osStats, "Android version")
    }

  @Test
  fun `empty stats should show empty text in place of distribution panel`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val tab = createTab()
      val fakeUi = FakeUi(tab)
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS
          )
        ),
        detailsState =
          LoadingState.Ready(
            DetailedIssueStats(IssueStats(null, emptyList()), IssueStats(null, emptyList()))
          )
      )

      with(fakeUi.findComponent<DistributionsContainerPanel>()!!.emptyText) {
        waitForCondition { text == "Detailed stats unavailable." }
        assertThat(isStatusVisible).isTrue()
      }
    }

  @Test
  fun `empty device stats but non-empty os stats result in empty device section`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val tab = createTab()
      val fakeUi = FakeUi(tab)
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS
          )
        ),
        detailsState =
          LoadingState.Ready(
            DetailedIssueStats(IssueStats(null, emptyList()), ISSUE1_DETAILS.osStats)
          )
      )

      waitForCondition { fakeUi.findComponent<JLabel> { it.text == "No data available" } != null }
      fakeUi
        .findAllComponents<DistributionPanel>()[1]
        .assertContent(ISSUE1_DETAILS.osStats, "Android version")
    }

  @Test
  fun `empty os stats but non-empty device stats result in empty os section`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val tab = createTab()
      val fakeUi = FakeUi(tab)
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS
          )
        ),
        detailsState =
          LoadingState.Ready(
            DetailedIssueStats(ISSUE1_DETAILS.deviceStats, IssueStats(null, emptyList()))
          )
      )

      waitForCondition { fakeUi.findComponent<JLabel> { it.text == "No data available" } != null }
      fakeUi
        .findAllComponents<DistributionPanel>()[0]
        .assertContent(ISSUE1_DETAILS.deviceStats, "device")
    }
}
