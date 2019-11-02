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

import com.android.build.attribution.analyzers.BuildEventsAnalyzersResultsProvider
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.data.TimeWithPercentage


/**
 * A Builder class for a data structure holding the data gathered by Gradle build analyzers.
 * The data structure of the report is described in UiDataModel.kt
 */
class BuildAttributionReportBuilder(
  val analyzersProxy: BuildEventsAnalyzersResultsProvider,
  val buildFinishedTimestamp: Long
) {

  fun build(): BuildAttributionReportUiData {
    val buildSummary = createBuildSummary()
    return object : BuildAttributionReportUiData {
      override val buildSummary: BuildSummary = buildSummary
    }
  }

  private fun createBuildSummary() = object : BuildSummary {
    override val buildFinishedTimestamp = this@BuildAttributionReportBuilder.buildFinishedTimestamp
    override val totalBuildDuration = TimeWithPercentage(analyzersProxy.getTotalBuildTime(), analyzersProxy.getTotalBuildTime())
    override val criticalPathDuration = TimeWithPercentage(analyzersProxy.getCriticalPathDuration(), analyzersProxy.getTotalBuildTime())
  }
}