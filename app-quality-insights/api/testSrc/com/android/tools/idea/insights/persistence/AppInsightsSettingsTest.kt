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
package com.android.tools.idea.insights.persistence

import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.CONNECTION2
import com.android.tools.idea.insights.DEFAULT_FETCHED_DEVICES
import com.android.tools.idea.insights.DEFAULT_FETCHED_OSES
import com.android.tools.idea.insights.DEFAULT_FETCHED_PERMISSIONS
import com.android.tools.idea.insights.DEFAULT_FETCHED_VERSIONS
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.VisibilityType
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.client.IssueResponse
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val device = WithCount(0, Device("Google", "Pixel 4a"))
private val version = WithCount(0, Version("2.0", "2.0", "2.0"))
private val operatingSystem = WithCount(0, OperatingSystemInfo("Android Api 23", "Api 23"))

class AppInsightsSettingsTest {
  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(controllerRule)

  private val settings: InsightsFilterSettings
    get() =
      projectRule.project
        .service<AppInsightsSettings>()
        .tabSettings[controllerRule.controller.key.displayName]!!

  @Test
  fun `settings is only applied to the first non empty ConnectionsChanged event`() =
    runBlocking<Unit> {
      // Verify setting is not applied until the connections list is not empty.
      controllerRule.updateConnections(emptyList())
      assertThat(controllerRule.consumeNext().connections).isEqualTo(Selection(null, emptyList()))

      // Mock settings
      projectRule.project
        .service<AppInsightsSettings>()
        .tabSettings[controllerRule.controller.key.displayName] =
        InsightsFilterSettings(
          connection = CONNECTION1.toSetting(),
          timeIntervalDays = TimeIntervalFilter.SEVEN_DAYS.name,
          visibilityType = VisibilityType.ALL.name,
          signal = SignalType.SIGNAL_EARLY.name,
          failureTypes = listOf(FailureType.FATAL.name),
          versions = listOf(version.value.toSetting()),
          devices = listOf(device.value.toSetting()),
          operatingSystems = listOf(operatingSystem.value.toSetting()),
        )

      // Check settings are correctly applied to the state
      controllerRule.updateConnections(listOf(CONNECTION2, CONNECTION1))
      with(controllerRule.consumeNext()) {
        assertThat(connections).isEqualTo(Selection(CONNECTION1, listOf(CONNECTION2, CONNECTION1)))
        assertThat(filters.timeInterval.selected).isEqualTo(TimeIntervalFilter.SEVEN_DAYS)
        assertThat(filters.visibilityType.selected).isEqualTo(VisibilityType.ALL)
        assertThat(filters.failureTypeToggles.selected).isEqualTo(setOf(FailureType.FATAL))
        assertThat(filters.versions.selected).isEqualTo(setOf(version))
        assertThat(filters.devices.selected).isEqualTo(setOf(device))
        assertThat(filters.operatingSystems.selected).isEqualTo(setOf(operatingSystem))
      }
      completeFetch()

      // Verify that once settings are applied, they are not applied again.
      controllerRule.updateConnections(emptyList())
      assertThat(controllerRule.consumeNext().connections).isEqualTo(Selection(null, emptyList()))
      controllerRule.updateConnections(listOf(CONNECTION2, CONNECTION1))
      with(controllerRule.consumeNext()) {
        assertThat(connections).isEqualTo(Selection(CONNECTION2, listOf(CONNECTION2, CONNECTION1)))
        assertThat(filters).isEqualTo(TEST_FILTERS)
      }
      completeFetch()
    }

  @Test
  fun `transitions update the settings`() =
    runBlocking<Unit> {
      val issueResponse =
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS, version),
          listOf(DEFAULT_FETCHED_DEVICES, device),
          listOf(DEFAULT_FETCHED_OSES, operatingSystem),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      controllerRule.consumeInitialState(LoadingState.Ready(issueResponse))
      controllerRule.selectVersions(setOf(version.value))
      controllerRule.consumeNext()
      completeFetch(issueResponse)
      controllerRule.selectDevices(setOf(device.value))
      controllerRule.consumeNext()
      completeFetch(issueResponse)
      controllerRule.selectOsVersion(setOf(operatingSystem.value))
      controllerRule.consumeNext()
      completeFetch(issueResponse)
      controllerRule.selectTimeInterval(TimeIntervalFilter.NINETY_DAYS)
      controllerRule.consumeNext()
      completeFetch(issueResponse)
      controllerRule.selectVisibilityType(VisibilityType.USER_PERCEIVED)
      controllerRule.consumeNext()
      completeFetch(issueResponse)
      controllerRule.selectSignal(SignalType.SIGNAL_REGRESSED)
      controllerRule.consumeNext()
      completeFetch(issueResponse)
      controllerRule.toggleFatality(FailureType.FATAL)
      controllerRule.consumeNext()
      val finalState = completeFetch(issueResponse)

      with(settings) {
        assertThat(connection).isEqualTo(finalState.connections.selected!!.toSetting())
        assertThat(timeIntervalDays).isEqualTo(TimeIntervalFilter.NINETY_DAYS.name)
        assertThat(visibilityType).isEqualTo(VisibilityType.USER_PERCEIVED.name)
        assertThat(signal).isEqualTo(SignalType.SIGNAL_REGRESSED.name)
        assertThat(failureTypes).containsExactly(FailureType.NON_FATAL.name, FailureType.ANR.name)
        assertThat(versions).containsExactly(version.value.toSetting())
        assertThat(devices).containsExactly(device.value.toSetting())
        assertThat(operatingSystems).containsExactly(operatingSystem.value.toSetting())
      }
    }

  @Test
  fun `settings are merged into filter state`() =
    runBlocking<Unit> {
      projectRule.project
        .service<AppInsightsSettings>()
        .tabSettings[controllerRule.controller.key.displayName] =
        InsightsFilterSettings(
          connection = CONNECTION1.toSetting(),
          timeIntervalDays = TimeIntervalFilter.SEVEN_DAYS.name,
          visibilityType = VisibilityType.ALL.name,
          signal = SignalType.SIGNAL_EARLY.name,
          failureTypes = listOf(FailureType.FATAL.name),
          versions = listOf(version.value.toSetting()),
          devices = listOf(device.value.toSetting()),
          operatingSystems = listOf(operatingSystem.value.toSetting()),
        )

      controllerRule.updateConnections(listOf(CONNECTION1))
      with(controllerRule.consumeNext()) {
        assertThat(connections.selected).isEqualTo(CONNECTION1)
        assertThat(filters.timeInterval.selected).isEqualTo(TimeIntervalFilter.SEVEN_DAYS)
        assertThat(filters.visibilityType.selected).isEqualTo(VisibilityType.ALL)
        assertThat(filters.signal.selected).isEqualTo(SignalType.SIGNAL_EARLY)
        assertThat(filters.failureTypeToggles.selected).containsExactly(FailureType.FATAL)
        assertThat(filters.devices.selected.map { it.value }).containsExactly(device.value)
        assertThat(filters.versions.selected.map { it.value }).containsExactly(version.value)
        assertThat(filters.operatingSystems.selected.map { it.value })
          .containsExactly(operatingSystem.value)
      }
    }

  @Test
  fun `settings should not apply if connection does not exist`() = runBlocking {
    // Mock settings for a connection that does not exist.
    projectRule.project
      .service<AppInsightsSettings>()
      .tabSettings[controllerRule.controller.key.displayName] =
      InsightsFilterSettings(
        connection = CONNECTION1.toSetting(),
        timeIntervalDays = TimeIntervalFilter.SEVEN_DAYS.name,
        visibilityType = VisibilityType.ALL.name,
        signal = SignalType.SIGNAL_EARLY.name,
        failureTypes = listOf(FailureType.FATAL.name),
        versions = listOf(version.value.toSetting()),
        devices = listOf(device.value.toSetting()),
        operatingSystems = listOf(operatingSystem.value.toSetting()),
      )

    // Update connections
    controllerRule.updateConnections(listOf(CONNECTION2))
    val model = controllerRule.consumeNext()

    // Check settings are not applied
    assertThat(model.connections).isEqualTo(Selection(CONNECTION2, listOf(CONNECTION2)))
    assertThat(model.filters).isEqualTo(TEST_FILTERS)
  }

  private suspend fun completeFetch(
    issueResponse: IssueResponse =
      IssueResponse(
        emptyList(),
        listOf(DEFAULT_FETCHED_VERSIONS),
        listOf(DEFAULT_FETCHED_DEVICES),
        listOf(DEFAULT_FETCHED_OSES),
        DEFAULT_FETCHED_PERMISSIONS,
      )
  ): AppInsightsState {
    controllerRule.client.completeIssuesCallWith(LoadingState.Ready(issueResponse))
    return controllerRule.consumeNext()
  }
}
