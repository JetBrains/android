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
import com.android.build.attribution.ui.MockUiData
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test


class BuildAnalyzerViewModelTest {

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

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
  fun testShouldWarnAboutNoGCSetting() {
    fun testCase(
      javaVersionUsed: Int?,
      isGarbageCollectorSettingSet: Boolean?,
      expectedResult: Boolean
    ) {
      mockData.buildSummary  = mockData.mockBuildOverviewData(javaVersionUsed, isGarbageCollectorSettingSet)
      expect.that(model.shouldWarnAboutNoGCSetting).isEqualTo(expectedResult)
    }

    testCase(javaVersionUsed = null, isGarbageCollectorSettingSet = null, expectedResult = false)
    testCase(javaVersionUsed = 8, isGarbageCollectorSettingSet = false, expectedResult = false)
    testCase(javaVersionUsed = 9, isGarbageCollectorSettingSet = false, expectedResult = true)
    testCase(javaVersionUsed = 10, isGarbageCollectorSettingSet = false, expectedResult = true)
    testCase(javaVersionUsed = 11, isGarbageCollectorSettingSet = false, expectedResult = true)
    testCase(javaVersionUsed = 12, isGarbageCollectorSettingSet = false, expectedResult = true)
    testCase(javaVersionUsed = 13, isGarbageCollectorSettingSet = false, expectedResult = true)
    testCase(javaVersionUsed = 14, isGarbageCollectorSettingSet = false, expectedResult = true)
    testCase(javaVersionUsed = 15, isGarbageCollectorSettingSet = false, expectedResult = true)
    testCase(javaVersionUsed = 8, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 9, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 10, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 11, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 12, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 13, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 14, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 15, isGarbageCollectorSettingSet = true, expectedResult = false)
  }

  @Test
  fun testShouldWarnAboutNoGCSettingWhenSuppressed() {
    warningSuppressions.suppressNoGCSettingWarning = true
    fun testCase(
      javaVersionUsed: Int?,
      isGarbageCollectorSettingSet: Boolean?,
      expectedResult: Boolean
    ) {
      mockData.buildSummary  = mockData.mockBuildOverviewData(javaVersionUsed, isGarbageCollectorSettingSet)
      expect.that(model.shouldWarnAboutNoGCSetting).isEqualTo(expectedResult)
    }

    testCase(javaVersionUsed = 8, isGarbageCollectorSettingSet = false, expectedResult = false)
    testCase(javaVersionUsed = 9, isGarbageCollectorSettingSet = false, expectedResult = false)
    testCase(javaVersionUsed = 10, isGarbageCollectorSettingSet = false, expectedResult = false)
    testCase(javaVersionUsed = 11, isGarbageCollectorSettingSet = false, expectedResult = false)
    testCase(javaVersionUsed = 12, isGarbageCollectorSettingSet = false, expectedResult = false)
    testCase(javaVersionUsed = 13, isGarbageCollectorSettingSet = false, expectedResult = false)
    testCase(javaVersionUsed = 14, isGarbageCollectorSettingSet = false, expectedResult = false)
    testCase(javaVersionUsed = 15, isGarbageCollectorSettingSet = false, expectedResult = false)
    testCase(javaVersionUsed = 8, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 9, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 10, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 11, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 12, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 13, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 14, isGarbageCollectorSettingSet = true, expectedResult = false)
    testCase(javaVersionUsed = 15, isGarbageCollectorSettingSet = true, expectedResult = false)
  }
}