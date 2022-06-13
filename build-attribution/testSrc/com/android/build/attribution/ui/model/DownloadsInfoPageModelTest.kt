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

import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.google.common.truth.Truth
import org.junit.Test

class DownloadsInfoPageModelTest {

  @Test
  fun testEmptyTextForGradleVersion() {
    val model = DownloadsInfoPageModel(DownloadsAnalyzer.GradleDoesNotProvideEvents)
    Truth.assertThat(model.repositoriesTableEmptyText).isEqualTo(
      "Minimal Gradle version providing downloads data is 7.3."
    )
  }

  @Test
  fun testEmptyTextForEmptyResult() {
    val model = DownloadsInfoPageModel(DownloadsAnalyzer.ActiveResult(emptyList()))
    Truth.assertThat(model.repositoriesTableEmptyText).isEqualTo(
      "There was no attempt to download files during this build."
    )
  }

  /** Test the formatted content of the table. */
  @Test
  fun testTableValuesForNonEmptyResult() {
    val downloadsData = DownloadsAnalyzer.ActiveResult(repositoryResults = listOf(
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
        failedRequestsBytesDownloaded = 0
      )
    ))
    val model = DownloadsInfoPageModel(downloadsData)

    val tableModel = model.repositoriesTableModel
    val tableDump = buildString {
      for (rowId in 0 until tableModel.rowCount) {
        append("|")
        for (columnId in 0 until tableModel.columnCount) {
          append(tableModel.getValueAt(rowId, columnId))
          append("|")
        }
        appendLine()
      }
    }
    Truth.assertThat(tableDump.trimEnd()).isEqualTo("""
      |Google|5|300 kB|1.0s|0|0.0s|
      |Maven Central|3|10 kB|0.5s|2|<0.1s|
    """.trimIndent())
  }
}