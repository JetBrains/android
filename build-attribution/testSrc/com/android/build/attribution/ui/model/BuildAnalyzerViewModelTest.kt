/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.model

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.ui.MockUiData
import com.google.common.truth.Truth.assertThat
import org.junit.Test


class BuildAnalyzerViewModelTest {

  private var callsCount = 0
  private val listenerMock: () -> Unit = {
    callsCount++
  }

  private val mockData = MockUiData()
  private val warningSuppressions = BuildAttributionWarningsFilter()

  private val model = BuildAnalyzerViewModel(mockData, warningSuppressions).apply {
    dataSetSelectionListener = listenerMock
  }

  @Test
  fun testInitialDataSetSelection() {
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.OVERVIEW)
  }

  @Test
  fun testInitialDataSetListOrderWithDownloadsEnabled() {
    val model = BuildAnalyzerViewModel(mockData, warningSuppressions)
    assertThat(model.availableDataSets).containsExactly(
      BuildAnalyzerViewModel.DataSet.OVERVIEW,
      BuildAnalyzerViewModel.DataSet.TASKS,
      BuildAnalyzerViewModel.DataSet.WARNINGS,
      BuildAnalyzerViewModel.DataSet.DOWNLOADS
    ).inOrder()
  }

  @Test
  fun testChangeDataSetSelectionNotifiesListener() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.TASKS
    assertThat(callsCount).isEqualTo(1)
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.TASKS)

    model.selectedData = BuildAnalyzerViewModel.DataSet.WARNINGS
    assertThat(callsCount).isEqualTo(2)
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.WARNINGS)
  }

  @Test
  fun testSettingSameDataSetDoesNotTriggerListener() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.OVERVIEW
    assertThat(callsCount).isEqualTo(0)
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.OVERVIEW)
  }

  @Test
  fun testJetifierWarningAutoSelectedOnCheckJetifierBuilds() {
    mockData.jetifierData = JetifierUsageAnalyzerResult(JetifierCanBeRemoved, lastCheckJetifierBuildTimestamp = 0, checkJetifierBuild = true)
    val model = BuildAnalyzerViewModel(mockData, warningSuppressions).apply {
      dataSetSelectionListener = listenerMock
    }
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.WARNINGS)
  }

  /**
   * This test is needed to make sure we show downloads page in dropdown if flag is true even if analyzer did not run
   * because of older gradle version. Initial intention was to not show the page in that case but as we changed it, let's better test.
   */
  @Test
  fun testDownloadsPageShownInComboBoxWhenNoDataBecauseOfGradle() {
    mockData.downloadsData = DownloadsAnalyzer.GradleDoesNotProvideEvents
    assertThat(model.availableDataSets).contains(BuildAnalyzerViewModel.DataSet.DOWNLOADS)
  }

  @Test
  fun testDownloadsPageNotShownInComboBoxWhenAnalyzerIsDisabled() {
    mockData.downloadsData = DownloadsAnalyzer.AnalyzerIsDisabled
    assertThat(model.availableDataSets).doesNotContain(BuildAnalyzerViewModel.DataSet.DOWNLOADS)
  }
}