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
import com.google.common.truth.Truth
import org.junit.Test

class DownloadsSummaryUIDataTest {

  private val downloadsData = DownloadsAnalyzer.ActiveResult(repositoryResults = listOf(
    DownloadsAnalyzer.RepositoryResult(
      repository = DownloadsAnalyzer.KnownRepository.GOOGLE,
      successRequestsCount = 5,
      successRequestsTimeMs = 1000,
      successRequestsBytesDownloaded = 300000,
      missedRequestsCount = 0,
      missedRequestsTimeMs = 0,
      failedRequestsCount = 0,
      failedRequestsTimeMs = 0,
      failedRequestsBytesDownloaded = 0
    ),
    DownloadsAnalyzer.RepositoryResult(
      repository = DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL,
      successRequestsCount = 1,
      successRequestsTimeMs = 500,
      successRequestsBytesDownloaded = 10000,
      missedRequestsCount = 1,
      missedRequestsTimeMs = 10,
      failedRequestsCount = 1,
      failedRequestsTimeMs = 5,
      failedRequestsBytesDownloaded = 100
    )
  ))

  @Test
  fun testSumOfRequests() {
    Truth.assertThat(DownloadsSummaryUIData (downloadsData).sumOfRequests).isEqualTo(5 + 1 + 1 + 1)
  }

  @Test
  fun testSumOfDataBytes() {
    Truth.assertThat(DownloadsSummaryUIData (downloadsData).sumOfDataBytes).isEqualTo(300000 + 10000 + 100)
  }

  @Test
  fun testSumOfTimeMs() {
    Truth.assertThat(DownloadsSummaryUIData (downloadsData).sumOfTimeMs).isEqualTo(1000 + 500 + 10 + 5)
  }
}