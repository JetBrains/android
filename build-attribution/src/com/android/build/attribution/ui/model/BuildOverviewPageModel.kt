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
package com.android.build.attribution.ui.model

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.DownloadsSummaryUIData

class BuildOverviewPageModel(
  val reportUiData: BuildAttributionReportUiData,
  private val warningSuppressions: BuildAttributionWarningsFilter
) {
  val shouldWarnAboutGC: Boolean
    get() = reportUiData.buildSummary.garbageCollectionTime.percentage > 10.0

  val shouldWarnAboutNoGCSetting: Boolean
    get() = !warningSuppressions.suppressNoGCSettingWarning
            && reportUiData.buildSummary.isGarbageCollectorSettingSet == false
            && reportUiData.buildSummary.javaVersionUsed?.let { it >= 9 } ?: false

  val downloadsSummaryUiData: DownloadsSummaryUIData?
    get() = (reportUiData.downloadsData as? DownloadsAnalyzer.ActiveResult)?.let { DownloadsSummaryUIData(it) }
}