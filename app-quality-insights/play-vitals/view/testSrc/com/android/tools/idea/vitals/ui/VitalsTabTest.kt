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

import com.android.testutils.delayUntilCondition
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
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.EventPage
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE1_DETAILS
import com.android.tools.idea.insights.IssueStats
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.analytics.TestAppInsightsTracker
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.ui.AppInsightsIssuesTableView
import com.android.tools.idea.insights.ui.DetailsPanelHeader
import com.android.tools.idea.insights.ui.DistributionPanel
import com.android.tools.idea.insights.ui.DistributionsContainerPanel
import com.android.tools.idea.insights.ui.actions.AppInsightsDisplayRefreshTimestampAction
import com.android.tools.idea.insights.ui.actions.AppInsightsDropDownAction
import com.android.tools.idea.insights.ui.actions.TreeDropDownAction
import com.android.tools.idea.insights.ui.dateFormatter
import com.android.tools.idea.insights.ui.shortenEventId
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
import com.intellij.ui.components.JBTabbedPane
import icons.StudioIcons
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
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
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        detailsState = LoadingState.Ready(ISSUE1_DETAILS),
        eventsState = LoadingState.Ready(EventPage(listOf(ISSUE1.sampleEvent), "")),
        connectionsState = listOf(TEST_CONNECTION_1, TEST_CONNECTION_2, TEST_CONNECTION_3),
      )

      val fakeUi = FakeUi(tab)

      // Wait for the stacktrace to render. It's a good indicator for when the UI is ready to be
      // checked.
      delayUntilCondition(200) {
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
      delayUntilCondition(200) {
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
          "All operating systems",
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

      // Details Panel content
      // Header
      val detailsPanelHeader = fakeUi.findComponent<DetailsPanelHeader>()!!
      // Set header to a large size so the title label don't get truncated.
      detailsPanelHeader.size = Dimension(500, 500)
      delayUntilCondition(200) {
        detailsPanelHeader.titleLabel.text == "<html>crash.<B>Crash</B></html>"
      }
      assertThat(detailsPanelHeader.eventsCountLabel.text).isEqualTo("50,000,000")
      assertThat(detailsPanelHeader.usersCountLabel.text).isEqualTo("3,000")

      // Details body
      val rows =
        fakeUi
          .findComponent<JPanel> { it.name == "detail_rows" }!!
          .components
          .filterIsInstance<JPanel>()
      assertThat(rows.size).isEqualTo(4)

      // Versions affected, signals, open/close button
      with(FakeUi(rows[0])) {
        assertThat(findComponent<JLabel>()!!.text).isEqualTo("Versions affected: 1.2.3 → 2.0.0")
      }

      // Event id, Link to vitals console
      with(FakeUi(rows[1])) {
        assertThat(findComponent<JLabel>()!!.text)
          .isEqualTo("Event ${ISSUE1.issueDetails.sampleEvent.shortenEventId()}")
        assertThat(findComponent<HyperlinkLabel>()!!.text).isEqualTo("View on Android Vitals")
      }

      // Device, OS Version, Timestamp, VCS Commit
      with(FakeUi(rows[2])) {
        assertThat(findAllComponents<JLabel>().filter { isShowing(it) }.map { it.text })
          .containsExactly(
            "Google Pixel 4a",
            "Android 3.1 (API 12)",
            dateFormatter.format(ISSUE1.sampleEvent.eventData.eventTime),
          )
        assertThat(findAllComponents<HyperlinkLabel>().filter { it.isVisible }.map { it.text })
          .containsExactly("74081e5f")
      }

      // Tabbed pane
      val tabbedPane = fakeUi.findComponent<JBTabbedPane>()!!
      assertThat(tabbedPane.tabCount).isEqualTo(1)
      assertThat(tabbedPane.getTitleAt(0)).isEqualTo("Stack trace")
      val panel = tabbedPane.getComponentAtIdx(0) as JPanel
      assertThat(panel.components.first()).isInstanceOf(ConsoleViewImpl::class.java)

      // Stack trace
      val consoleView = panel.components.first() as ConsoleViewImpl
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
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        detailsState =
          LoadingState.Ready(
            DetailedIssueStats(IssueStats(null, emptyList()), IssueStats(null, emptyList()))
          ),
      )

      with(fakeUi.findComponent<DistributionsContainerPanel>()!!.emptyText) {
        delayUntilCondition(200) { text == "Detailed stats unavailable." }
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
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        detailsState =
          LoadingState.Ready(
            DetailedIssueStats(IssueStats(null, emptyList()), ISSUE1_DETAILS.osStats)
          ),
      )

      delayUntilCondition(200) {
        fakeUi.findComponent<JLabel> { it.text == "No data available" } != null
      }
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
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        detailsState =
          LoadingState.Ready(
            DetailedIssueStats(ISSUE1_DETAILS.deviceStats, IssueStats(null, emptyList()))
          ),
      )

      delayUntilCondition(200) {
        fakeUi.findComponent<JLabel> { it.text == "No data available" } != null
      }
      fakeUi
        .findAllComponents<DistributionPanel>()[0]
        .assertContent(ISSUE1_DETAILS.deviceStats, "device")
    }

  @Test
  fun `missing sample event does not cause crash when shown`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val tab = createTab()
      val fakeUi = FakeUi(tab)
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1.copy(sampleEvent = Event())),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        ),
        detailsState =
          LoadingState.Ready(
            DetailedIssueStats(ISSUE1_DETAILS.deviceStats, IssueStats(null, emptyList()))
          ),
      )

      delayUntilCondition(200) {
        fakeUi.findComponent<JLabel> {
          it.icon == StudioIcons.LayoutEditor.Toolbar.ANDROID_API && it.text == "unknown"
        } != null
      }
    }

  private fun JBTabbedPane.getComponentAtIdx(idx: Int) =
    (getComponentAt(idx) as JComponent).components.last()
}
