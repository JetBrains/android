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
package com.android.build.attribution.statistics

import com.android.build.attribution.BuildAnalysisResults
import com.android.build.attribution.BuildAnalyzerSettings
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.storeBuildAnalyzerResultData
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StatisticsCollectorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private lateinit var previousSettingsState: BuildAnalyzerSettings.State

  @Before
  fun changeLimitSizeHistory() {
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
    previousSettingsState = BuildAnalyzerSettings.getInstance(projectRule.project).settingsState
    BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored = 10
  }

  @After
  fun cleanup() {
    StudioFlags.BUILD_ANALYZER_HISTORY.clearOverride()
    BuildAnalyzerSettings.getInstance(projectRule.project).settingsState = previousSettingsState
  }

  @Test
  fun checkOrderAndResultsIds() {
    val statisticsCollector = StatisticsCollector(projectRule.project)
    val singleCollector = SimpleSingleStatisticsCollector()
    val limitSizeHistory = BuildAnalyzerSettings.getInstance(projectRule.project).settingsState.maxNumberOfBuildsStored
    repeat(limitSizeHistory) {
      storeBuildAnalyzerResultData(projectRule.project).get()
    }
    statisticsCollector.collectStatistics(singleCollector)
    Truth.assertThat(singleCollector.resultsIds).isEqualTo(
      BuildAnalyzerStorageManager.getInstance(projectRule.project).getListOfHistoricBuildDescriptors().toList().sortedBy { it.buildFinishedTimestamp }.map { it.buildSessionID })
  }
}

class SimpleSingleStatisticsCollector : SingleStatisticsCollector() {
  val resultsIds = mutableListOf<String>()
  override fun accept(t: BuildAnalysisResults) {
    resultsIds.add(t.getBuildSessionID())
  }
}