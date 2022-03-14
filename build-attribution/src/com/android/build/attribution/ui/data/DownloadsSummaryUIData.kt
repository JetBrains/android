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
package com.android.build.attribution.ui.data

import com.android.build.attribution.analyzers.DownloadsAnalyzer

class DownloadsSummaryUIData(
  downloadsData: DownloadsAnalyzer.Result
) {
  val shouldShowOnUi: Boolean = downloadsData.analyzerActive
  val isEmpty: Boolean = downloadsData.repositoryResults.isEmpty()
  val sumOfRequests: Int = downloadsData.repositoryResults.sumOf { it.successRequestsCount + it.failedRequestsCount + it.missedRequestsCount }
  val sumOfDataBytes: Long = downloadsData.repositoryResults.sumOf { it.successRequestsBytesDownloaded + it.failedRequestsBytesDownloaded }
  val sumOfTimeMs: Long = downloadsData.repositoryResults.sumOf { it.successRequestsTimeMs + it.failedRequestsTimeMs + it.missedRequestsTimeMs }
}