/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.data.TaskData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildAttributionReportBuilderTest : AbstractBuildAttributionReportBuilderTest() {

  @Test
  fun testBuilderOnEmptyResults() {
    val report = BuildAttributionReportBuilder(MockResultsProvider()).build()

    assertThat(report.buildSummary.buildFinishedTimestamp).isEqualTo(0)
    assertThat(report.buildSummary.totalBuildDuration.timeMs).isEqualTo(0)
  }

  @Test
  fun testBuildSummary() {
    val analyzerResults = object : MockResultsProvider() {
      override fun getBuildFinishedTimestamp(): Long = 12345
      override fun getTotalBuildTimeMs(): Long = 1500
      override fun getTasksDeterminingBuildDuration(): List<TaskData> {
        return listOf(
          TaskData("taskA", ":app", pluginA, 0, 123, TaskData.TaskExecutionMode.FULL, emptyList()),
          TaskData("taskB", ":app", pluginA, 0, 456, TaskData.TaskExecutionMode.FULL, emptyList())
        )
      }
    }

    val report = BuildAttributionReportBuilder(analyzerResults).build()

    assertThat(report.buildSummary.buildFinishedTimestamp).isEqualTo(12345)
    assertThat(report.buildSummary.totalBuildDuration.timeMs).isEqualTo(1500)
    assertThat(report.buildSummary.criticalPathDuration).isEqualTo(TimeWithPercentage(123 + 456, 1500))
  }

}