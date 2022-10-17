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
package com.android.build.attribution.ui.view

import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.ui.mockDownloadsData
import com.android.build.attribution.ui.model.DownloadsInfoPageModel
import com.android.build.attribution.ui.warningIcon
import com.android.tools.adtui.TreeWalker
import com.google.common.truth.Truth
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.OnePixelSplitter
import org.junit.Test
import org.mockito.Mockito

class DownloadsInfoPageViewTest {

  @Test
  fun testViewCreatedWithNonEmptyData() {
    val downloadsData = mockDownloadsData()
    val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)
    val pageModel = DownloadsInfoPageModel(downloadsData)
    val downloadsPage = DownloadsInfoPageView(pageModel, mockHandlers)

    val splitter = TreeWalker(downloadsPage.component).descendants().filterIsInstance<OnePixelSplitter>().single()
    Truth.assertThat(splitter.firstComponent).isNotNull()
    Truth.assertThat(splitter.secondComponent).isNotNull()
  }

  @Test
  fun testViewCreatedWithEmptyData() {
    val downloadsData = DownloadsAnalyzer.ActiveResult(emptyList())
    val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)
    val pageModel = DownloadsInfoPageModel(downloadsData)
    val downloadsPage = DownloadsInfoPageView(pageModel, mockHandlers)

    val splitter = TreeWalker(downloadsPage.component).descendants().filterIsInstance<OnePixelSplitter>().single()
    Truth.assertThat(splitter.firstComponent).isNotNull()
    Truth.assertThat(splitter.secondComponent).isNull()
  }

  @Test
  fun testStatusColumnPresentation() {
    val downloads = listOf(
      downloadResult(DownloadsAnalyzer.DownloadStatus.SUCCESS, null),
      downloadResult(DownloadsAnalyzer.DownloadStatus.MISSED, null),
      downloadResult(DownloadsAnalyzer.DownloadStatus.FAILURE, "error\nmessage")
    )
    val resultList = downloads.groupBy { it.repository }.map { (repo, events) ->
      DownloadsAnalyzer.RepositoryResult(repository = repo, downloads = events)
    }
    val downloadsData = DownloadsAnalyzer.ActiveResult(resultList)
    val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)
    val pageModel = DownloadsInfoPageModel(downloadsData)
    val downloadsPage = DownloadsInfoPageView(pageModel, mockHandlers)
    // Select all repositories to populate right table with all requests
    pageModel.selectedRepositoriesUpdated(downloadsData.repositoryResults)
    val requestsTable = downloadsPage.requestsList

    // Convert first column cells into text presentation and compare
    Truth.assertThat((0 until requestsTable.rowCount).joinToString(separator = "\n---\n") { row ->
      val renderer = requestsTable.getCellRenderer(row, 0)
      val component = requestsTable.prepareRenderer(renderer, row, 0) as ColoredTableCellRenderer
      val cellIcon = when (component.icon) {
        warningIcon() -> "icon:[W]"
        null -> "no icon"
        else -> "unexpected icon"
      }
      val cellText = component.getCharSequence(false)
      val cellTooltip = component.toolTipText?.let { "tooltip:[$it]" } ?: "no tooltip"
      "[$cellText], $cellIcon, $cellTooltip"
    })
      .isEqualTo("""
        [Ok], no icon, no tooltip
        ---
        [Not Found], icon:[W], no tooltip
        ---
        [Error], icon:[W], tooltip:[error<br/>message]
        """.trimIndent())
  }

  private fun downloadResult(status: DownloadsAnalyzer.DownloadStatus, failureMessage: String?): DownloadsAnalyzer.DownloadResult = DownloadsAnalyzer.DownloadResult(
    timestamp = 0,
    repository = DownloadsAnalyzer.KnownRepository.GOOGLE,
    url = "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/7.3.0-alpha05/gradle-7.3.0-alpha05.pom",
    status = status,
    duration = 100,
    bytes = 1000,
    failureMessage = failureMessage
  )
}