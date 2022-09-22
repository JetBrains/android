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
package com.android.build.attribution.proto

import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.google.common.truth.Truth
import org.junit.Test

class DownloadsAnalyzerResultMessageConverterTest {
  @Test
  fun testDownloadsAnalyzerResult() {
    val repoResult = DownloadsAnalyzer.RepositoryResult(
      DownloadsAnalyzer.OtherRepository("repository"),
      listOf(
        DownloadsAnalyzer.DownloadResult(
          123,
          DownloadsAnalyzer.OtherRepository("repository"),
          "url",
          DownloadsAnalyzer.DownloadStatus.SUCCESS,
          1234,
          5678,
          "failure"
        )
      )
    )
    val downloadResult = DownloadsAnalyzer.ActiveResult(listOf(repoResult))
    val resultMessage = DownloadsAnalyzerResultMessageConverter.transform(downloadResult)
    val resultConverted = DownloadsAnalyzerResultMessageConverter.construct(resultMessage)
    Truth.assertThat(resultConverted).isEqualTo(downloadResult)
  }
}