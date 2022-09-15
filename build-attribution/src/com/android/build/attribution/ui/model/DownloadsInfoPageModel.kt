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
import com.android.build.attribution.analyzers.minGradleVersionProvidingDownloadEvents
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.warningIcon
import com.intellij.openapi.util.text.Formats
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.util.Comparator
import javax.swing.JTable
import javax.swing.JToolTip
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer

class DownloadsInfoPageModel(
  private val downloadsData: DownloadsAnalyzer.Result
) {

  val repositoriesTableModel: ListTableModel<DownloadsAnalyzer.RepositoryResult> = RepositoriesTableModel(downloadsData)

  val repositoriesTableEmptyText: String get() = when(downloadsData) {
    is DownloadsAnalyzer.GradleDoesNotProvideEvents -> "Minimal Gradle version providing downloads data is $minGradleVersionProvidingDownloadEvents."
    is DownloadsAnalyzer.ActiveResult -> "There was no attempt to download files during this build."
    is DownloadsAnalyzer.AnalyzerIsDisabled -> error("UI Should not be available for this state.")
  }

  val requestsListModel: ListTableModel<DownloadsAnalyzer.DownloadResult> = RequestsListTableModel().apply {
    isSortable = true
  }

  fun selectedRepositoriesUpdated(repositories: List<DownloadsAnalyzer.RepositoryResult>) {
    requestsListModel.items = repositories.flatMap { it.downloads }
  }
}

private class RepositoriesTableModel(result: DownloadsAnalyzer.Result) : ListTableModel<DownloadsAnalyzer.RepositoryResult>() {
  init {
    fun column(title: String, tooltip: String? = null, valueOf: (DownloadsAnalyzer.RepositoryResult) -> String) =
      object : ColumnInfo<DownloadsAnalyzer.RepositoryResult, String>(title) {
        override fun valueOf(found: DownloadsAnalyzer.RepositoryResult): String = valueOf(found)
        override fun getPreferredStringValue() = title
        override fun getTooltipText(): String? {
          return tooltip
        }
      }
    columnInfos = arrayOf(
      column("Repository") { when(val repo = it.repository) {
        is DownloadsAnalyzer.KnownRepository -> repo.presentableName
        is DownloadsAnalyzer.OtherRepository -> repo.host
      }},
      column("Requests", "Total number of requests.") { it.totalNumberOfRequests().toString() },
      column("Data", "Total amount of data downloaded.") { Formats.formatFileSize(it.totalAmountOfData()) },
      column("Time", "Total amount of time taken to execute requests.") { durationString(it.totalAmountOfTime()) },
      column("Failed Requests", "Number of failed requests.") { it.numberOfFailed().toString() },
      column("Failed Requests Time", "Total amount of time taken to execute failed requests.") { durationString(it.timeOfFailed()) },
    )
    items = (result as? DownloadsAnalyzer.ActiveResult)?.repositoryResults ?: emptyList()
  }

  private fun DownloadsAnalyzer.RepositoryResult.totalNumberOfRequests() = successRequestsCount + failedRequestsCount + missedRequestsCount
  private fun DownloadsAnalyzer.RepositoryResult.totalAmountOfData() = successRequestsBytesDownloaded + failedRequestsBytesDownloaded
  private fun DownloadsAnalyzer.RepositoryResult.totalAmountOfTime() = successRequestsTimeMs + failedRequestsTimeMs + missedRequestsTimeMs
  private fun DownloadsAnalyzer.RepositoryResult.numberOfFailed() = failedRequestsCount + missedRequestsCount
  private fun DownloadsAnalyzer.RepositoryResult.timeOfFailed() = failedRequestsTimeMs + missedRequestsTimeMs
}

/**
 * Populates table with the list of requests from selected repositories.
 */
class RequestsListTableModel : ListTableModel<DownloadsAnalyzer.DownloadResult>() {

  init {
    columnInfos = arrayOf(
      object : ColumnInfo<DownloadsAnalyzer.DownloadResult, DownloadsAnalyzer.DownloadResult>("Status") {
        val cellRenderer = MyWarningIconCellRenderer()
        override fun valueOf(item: DownloadsAnalyzer.DownloadResult): DownloadsAnalyzer.DownloadResult = item
        override fun getRenderer(item: DownloadsAnalyzer.DownloadResult): TableCellRenderer = cellRenderer
        override fun getPreferredStringValue() = "Not Found"
        override fun getMaxStringValue(): String = preferredStringValue
        override fun getAdditionalWidth(): Int = warningIcon().iconWidth
        override fun getComparator(): Comparator<DownloadsAnalyzer.DownloadResult> = Comparator.comparing { it.status }
      },
      object : ColumnInfo<DownloadsAnalyzer.DownloadResult, String>("File") {
        val cellRenderer = MyCellRenderer(SimpleTextAttributes.REGULAR_ATTRIBUTES)
        override fun valueOf(item: DownloadsAnalyzer.DownloadResult): String = item.url
        override fun getRenderer(item: DownloadsAnalyzer.DownloadResult): TableCellRenderer = cellRenderer
        override fun getComparator(): Comparator<DownloadsAnalyzer.DownloadResult> = Comparator.comparing { it.url }
      },
      object : ColumnInfo<DownloadsAnalyzer.DownloadResult, String>("Time") {
        val cellRenderer = MyCellRenderer(SimpleTextAttributes.GRAYED_ATTRIBUTES)
        override fun valueOf(item: DownloadsAnalyzer.DownloadResult): String = durationString(item.duration)
        override fun getRenderer(item: DownloadsAnalyzer.DownloadResult): TableCellRenderer = cellRenderer
        override fun getPreferredStringValue() = "###.#s"
        override fun getMaxStringValue(): String = preferredStringValue
        override fun getComparator(): Comparator<DownloadsAnalyzer.DownloadResult> = Comparator.comparing { it.duration }
      },
      object : ColumnInfo<DownloadsAnalyzer.DownloadResult, String>("Size") {
        val cellRenderer = MyCellRenderer(SimpleTextAttributes.GRAYED_ATTRIBUTES)
        override fun valueOf(item: DownloadsAnalyzer.DownloadResult): String = Formats.formatFileSize(item.bytes)
        override fun getRenderer(item: DownloadsAnalyzer.DownloadResult): TableCellRenderer = cellRenderer
        override fun getPreferredStringValue() = "123.45MB"
        override fun getMaxStringValue(): String = preferredStringValue
        override fun getComparator(): Comparator<DownloadsAnalyzer.DownloadResult> = Comparator.comparing { it.bytes }
      }
    )
  }
}

private class MyWarningIconCellRenderer : ColoredTableCellRenderer() {
  override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value is DownloadsAnalyzer.DownloadResult) {
      if (value.status != DownloadsAnalyzer.DownloadStatus.SUCCESS) {
        icon = warningIcon()
      }
      val text = when (value.status) {
        DownloadsAnalyzer.DownloadStatus.SUCCESS -> "Ok"
        DownloadsAnalyzer.DownloadStatus.MISSED -> "Not Found"
        DownloadsAnalyzer.DownloadStatus.FAILURE -> "Error"
      }
      append(text, SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
      toolTipText = "row: $row<br/><br/>" + value.failureMessage?.replace("\n", "<br/>")

    }
    setTextAlign(SwingConstants.RIGHT)
  }
}

private class MyCellRenderer(val textAttributes: SimpleTextAttributes) : ColoredTableCellRenderer() {
  override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value is String) {
      append(value, textAttributes)
    }
  }
}