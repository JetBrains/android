/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.build.output

import com.android.annotations.concurrency.UiThread
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.ui.formatAvgDownloadSpeed
import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.Formats
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.messages.Topic
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellRenderer


/**
 * This class describes the logic of how build output Downloads info page [DownloadsInfoExecutionConsole] is updated.
 * There should be strictly 1 to 1 relation between the view and this model.
 */
class DownloadsInfoUIModel : DownloadInfoDataModel.Listener {
  val repositoriesTableModel = RepositoriesTableModel()
  val requestsTableModel = RequestsTableModel()
  var selectedRepoItem = repositoriesTableModel.summaryItem

  /**
   * Listener that will be notified that model has changed.
   */
  private val dataUpdatedListeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  /**
   * [downloadRequest] represents new or an updated state of already existing request. Two table models need to be updated. For summary
   * table 'All summary' row is updated, it contains info on all requests, and item corresponding to this request repository is updated.
   * Requests table is updated only if updated [downloadRequest] belongs to the repository selected at the moment.
   */
  @UiThread
  override fun updateDownloadRequest(downloadRequest: DownloadRequestItem) {
    repositoriesTableModel.update(downloadRequest)
    if (selectedRepoItem.repository == null || selectedRepoItem.repository == downloadRequest.repository) {
      requestsTableModel.addOrUpdate(downloadRequest)
    }
    dataUpdatedListeners.forEach { it.invoke() }
  }

  fun addAndFireDataUpdateListener(listener: () -> Unit) {
    dataUpdatedListeners.add(listener)
    listener.invoke()
  }

  fun repoSelectionUpdated(item: RepositoryTableItem?) {
    selectedRepoItem = item ?: repositoriesTableModel.summaryItem
    requestsTableModel.items = selectedRepoItem.requests
  }
}

data class DownloadRequestKey(
  val startTimestamp: Long,
  val url: String,
)

data class DownloadRequestItem(
  val requestKey: DownloadRequestKey,
  val repository: DownloadsAnalyzer.Repository,
  val completed: Boolean = false,
  val receivedBytes: Long = 0,
  val duration: Long = 0,
  val failureMessage: String? = null
) {
  val failed: Boolean get() = !failureMessage.isNullOrBlank()
}

class RepositoryTableItem(
  val repository: DownloadsAnalyzer.Repository?
) {
  private val requestsMap = mutableMapOf<DownloadRequestKey, DownloadRequestItem>()
  val requests: List<DownloadRequestItem> get() = requestsMap.values.toList()
  var totalNumberOfRequests: Int = 0
    private set
  var runningNumberOfRequests: Int = 0
    private set
  var totalAmountOfData: Long = 0L
    private set
  var numberOfFailed: Int = 0
    private set
  var totalAmountOfTime: Long = 0L
    private set
  var timeOfFailed: Long = 0L
    private set

  fun updateRequest(downloadRequest: DownloadRequestItem) {
    requestsMap[downloadRequest.requestKey] = downloadRequest
    totalNumberOfRequests = requestsMap.size
    runningNumberOfRequests = requestsMap.values.count { !it.completed }
    totalAmountOfData = requestsMap.values.sumOf { it.receivedBytes }
    numberOfFailed = requestsMap.values.count { it.failed }
    totalAmountOfTime = requestsMap.values.sumOf { it.duration }
    timeOfFailed =  requestsMap.values.filter { it.failed }.sumOf { it.duration }
  }
}

class RepositoriesTableModel : ListTableModel<RepositoryTableItem>() {
  val summaryItem = RepositoryTableItem(null)
  val reposData = mutableMapOf<DownloadsAnalyzer.Repository, RepositoryTableItem>()

  init {
    // Cell renderer that will highlight first summary line
    val cellRenderer = object : ColoredTableCellRenderer() {
      override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        if (value is String) {
          if (row == 0) {
            append(value, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          }
          else {
            append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          }
        }
      }
    }
    fun column(title: String, tooltip: String? = null, valueOf: (RepositoryTableItem) -> String) =
      object : ColumnInfo<RepositoryTableItem, String>(title) {
        override fun valueOf(found: RepositoryTableItem): String = valueOf(found)
        override fun getPreferredStringValue() = title
        override fun getTooltipText(): String? = tooltip
        override fun getRenderer(item: RepositoryTableItem): TableCellRenderer = cellRenderer
      }
    columnInfos = arrayOf(
      column("Repository") { repoItem -> when {
        repoItem.repository == null -> "Total"
        repoItem.repository is DownloadsAnalyzer.KnownRepository -> repoItem.repository.presentableName
        repoItem.repository is DownloadsAnalyzer.OtherRepository -> repoItem.repository.host
        else -> error("Unexpected repository table item.")
      }},
      column("Requests", "Total number of requests.") {
        val runningRequests = it.runningNumberOfRequests
        val totalRequests = it.totalNumberOfRequests
        if (runningRequests > 0) "$totalRequests ($runningRequests running)"
        else totalRequests.toString()
      },
      column("Data", "Total amount of data downloaded.") { Formats.formatFileSize(it.totalAmountOfData) },
      column("Time", "Total amount of time taken to execute requests.") { StringUtil.formatDuration(it.totalAmountOfTime) },
      column("Avg Speed", "Average download speed.") { formatAvgDownloadSpeed(it.totalAmountOfData, it.totalAmountOfTime) },
      column("Failed Requests", "Number of failed requests.") { it.numberOfFailed.toString() },
      column("Failed Requests Time", "Total amount of time taken to execute failed requests.") { StringUtil.formatDuration(it.timeOfFailed) },
    )
  }

  fun update(downloadRequest: DownloadRequestItem) {
    if (items.isEmpty()) addRow(summaryItem)
    val repository = downloadRequest.repository
    val repoTableItem = reposData.computeIfAbsent(repository) { RepositoryTableItem(it) }
    repoTableItem.updateRequest(downloadRequest)
    summaryItem.updateRequest(downloadRequest)

    fireTableCellUpdated(0, TableModelEvent.ALL_COLUMNS)
    val updatedRepoRowIndex = items.indexOfFirst { it.repository == repository }
    if (updatedRepoRowIndex == -1) {
      addRow(repoTableItem)
    }
    else {
      fireTableCellUpdated(updatedRepoRowIndex, TableModelEvent.ALL_COLUMNS)
    }
  }
}

class RequestsTableModel : ListTableModel<DownloadRequestItem>() {
  private val cellRenderer = object : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      if (value is String) {
        append(value)
      }
    }
  }

  val fileNameColumn = object : ColumnInfo<DownloadRequestItem, String>("File") {
    override fun valueOf(item: DownloadRequestItem): String = item.requestKey.url
    override fun getRenderer(item: DownloadRequestItem): TableCellRenderer = cellRenderer
    override fun getComparator(): Comparator<DownloadRequestItem> = Comparator.comparing { it.requestKey.url }
  }
  val timeColumn = object : ColumnInfo<DownloadRequestItem, String>("Time") {
    override fun valueOf(item: DownloadRequestItem): String = StringUtil.formatDuration(item.duration)
    override fun getRenderer(item: DownloadRequestItem): TableCellRenderer = cellRenderer
    override fun getPreferredStringValue() = "12 s 123 ms"
    override fun getMaxStringValue(): String = preferredStringValue
    override fun getComparator(): Comparator<DownloadRequestItem> = Comparator.comparing { it.duration }
  }
  val sizeColumn = object : ColumnInfo<DownloadRequestItem, String>("Size") {
    override fun valueOf(item: DownloadRequestItem): String = StringUtil.formatFileSize(item.receivedBytes)
    override fun getRenderer(item: DownloadRequestItem): TableCellRenderer = cellRenderer
    override fun getPreferredStringValue() = "123.45 MB"
    override fun getMaxStringValue(): String = preferredStringValue
    override fun getComparator(): Comparator<DownloadRequestItem> = Comparator.comparing { it.receivedBytes }
  }
  val speedColumn = object : ColumnInfo<DownloadRequestItem, String>("Avg Speed") {
    override fun valueOf(item: DownloadRequestItem): String = formatAvgDownloadSpeed(item.receivedBytes, item.duration)
    override fun getRenderer(item: DownloadRequestItem): TableCellRenderer = cellRenderer
    override fun getPreferredStringValue() = "123.45 MB/s"
    override fun getMaxStringValue(): String = preferredStringValue
  }

  class Status(
    val text: String,
    val icon: Icon,
    val tooltip: String
  ) {
    override fun toString(): String = text
  }

  val statusColumn = object : ColumnInfo<DownloadRequestItem, Status>("Status") {
    val columnCellRenderer = object : ColoredTableCellRenderer() {
      override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        if (value is Status) {
          toolTipText = value.tooltip
          icon = value.icon
          isTransparentIconBackground = true
          append(value.text, SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
          setTextAlign(SwingConstants.RIGHT)
        }
      }
    }
    override fun valueOf(item: DownloadRequestItem): Status = when {
      !item.completed -> Status("Running", AnimatedIcon.Default.INSTANCE, "")
      item.failed -> Status("Failed", AllIcons.General.Warning, item.failureMessage?.replace("\n", "<br/>") ?: "")
      else -> Status("Finished", AllIcons.RunConfigurations.TestPassed, "")
    }
    override fun getRenderer(item: DownloadRequestItem): TableCellRenderer = columnCellRenderer
    override fun getPreferredStringValue() = "Download Failed"
    override fun getMaxStringValue(): String = preferredStringValue
    override fun getComparator(): Comparator<DownloadRequestItem> = Comparator.comparing { valueOf(it).text }
  }

  init{
    columnInfos = arrayOf(
      statusColumn,
      fileNameColumn,
      timeColumn,
      sizeColumn,
      speedColumn
    )
    isSortable = true
  }

  fun addOrUpdate(requestItem: DownloadRequestItem) {
    val itemIndex = items.indexOfFirst { requestItem.requestKey == it.requestKey }
    if (itemIndex == -1) {
      addRow(requestItem)
    }
    else {
      setItem(itemIndex, requestItem)
    }
  }
}
