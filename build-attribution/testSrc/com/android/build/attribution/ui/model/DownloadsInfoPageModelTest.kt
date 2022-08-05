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
import com.android.build.attribution.ui.mockDownloadsData
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
    val downloadsData = mockDownloadsData()
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

  @Test
  fun testRequestsListContentOnSelectionChange() {
    val downloadsData = mockDownloadsData()
    val model = DownloadsInfoPageModel(downloadsData)

    Truth.assertThat(model.requestsListModel.rowCount).isEqualTo(0)

    // Select all
    model.selectedRepositoriesUpdated(downloadsData.repositoryResults)

    Truth.assertThat(model.requestsListModel.rowCount).isEqualTo(8)
  }
}