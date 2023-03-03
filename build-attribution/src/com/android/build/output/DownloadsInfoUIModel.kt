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
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.formatAvgDownloadSpeed
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.Formats
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Alarm
import com.intellij.util.messages.Topic
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellRenderer

/**
 * This class is a middle layer between Build Analyzer events processor and live Downloads info UI.
 * It is responsible for converting data about requests to a format useful for this UI and for notifying all created models
 * about this new data.
 *
 * One single instance of it is created for every build (or sync). It publishes processed updates via message bus, attaching task id
 * of the build it was created for. Models should be subscribed to this message bus and should filter the updates by the id.
 */
class DownloadsInfoUIModelNotifier(
  val project: Project,
  val taskId: ExternalSystemTaskId
) {
  interface Listener {
    fun updateDownloadRequest(taskId: ExternalSystemTaskId, downloadRequest: DownloadRequestItem)
  }
  companion object {
    val DOWNLOADS_OUTPUT_TOPIC: Topic<Listener> = Topic.create("DownloadsImpactUIModelNotifier", Listener::class.java)
  }

  fun downloadStarted(startTimestamp: Long, url: String, repository: DownloadsAnalyzer.Repository) {
    val requestKey = DownloadRequestKey(startTimestamp, url)
    val requestItem = DownloadRequestItem(requestKey, repository,false, 0, 0, null)
    project.messageBus.syncPublisher(DOWNLOADS_OUTPUT_TOPIC).updateDownloadRequest(taskId, requestItem)
  }

  fun downloadFinished(downloadResult: DownloadsAnalyzer.DownloadResult) {
    val requestItem = DownloadRequestItem(
      requestKey = DownloadRequestKey(downloadResult.timestamp, downloadResult.url),
      repository = downloadResult.repository,
      completed = true,
      receivedBytes = downloadResult.bytes,
      duration = downloadResult.duration,
      failureMessage = when(downloadResult.status) {
        DownloadsAnalyzer.DownloadStatus.SUCCESS -> null
        DownloadsAnalyzer.DownloadStatus.MISSED -> "Not Found"
        DownloadsAnalyzer.DownloadStatus.FAILURE -> downloadResult.failureMessage
      }
    )
    project.messageBus.syncPublisher(DOWNLOADS_OUTPUT_TOPIC).updateDownloadRequest(taskId, requestItem)
  }
}

/**
 * This class describes the logic of how build output Downloads info page [DownloadsInfoExecutionConsole] is updated.
 * There should be strictly 1 to 1 relation between the view and this model.
 * Since the view is created and installed by the platform code reacting to us publishing [DownloadsInfoPresentableEvent], we can
 * not control the lifecycle of the view and even how much of them will be created. Because of that code is structured in a way that this
 * model is created for each view created by the platform code. Then the model subscribes to updates form [DownloadsInfoUIModelNotifier]
 * via [DownloadsInfoUIModelNotifier.DOWNLOADS_OUTPUT_TOPIC]. It listens for the updates only during build, [buildFinishedDisposable]
 * is used in order to support this.
 */
class DownloadsInfoUIModel(val taskId: ExternalSystemTaskId, val buildFinishedDisposable: Disposable) {
  val repositoriesTableModel = RepositoriesTableModel()
  val requestsTableModel = RequestsTableModel()
  var selectedRepoItem = repositoriesTableModel.summaryItem

  /**
   * Listener that will be notified that model has changed.
   */
  private val dataUpdatedListeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  private val modelRefresher = ModelRefresher(this)

  init {
    taskId.findProject()?.let { project: Project ->
      project.messageBus.connect(buildFinishedDisposable).subscribe(DownloadsInfoUIModelNotifier.DOWNLOADS_OUTPUT_TOPIC, object : DownloadsInfoUIModelNotifier.Listener {
        override fun updateDownloadRequest(taskId: ExternalSystemTaskId, downloadRequest: DownloadRequestItem) {
          if (this@DownloadsInfoUIModel.taskId != taskId) return
          // The order of 'invokeLater' calls can be reshuffled resulting in incorrect order of updates applied resulting in incorrect
          // data being presented (last arrived events is considered to be the latest known state).
          // To ensure the order of the received updates add them to concurrent queue and pull updates from it in EDT.
          modelRefresher.onNewItemUpdate(downloadRequest)
        }
      })
    }
  }

  /**
   * [downloadRequest] represents new or an updated state of already existing request. Two table models need to be updated. For summary
   * table 'All summary' row is updated, it contains info on all requests, and item corresponding to this request repository is updated.
   * Requests table is updated only if updated [downloadRequest] belongs to the repository selected at the moment.
   */
  @UiThread
  fun updateDownloadRequest(downloadRequest: DownloadRequestItem) {
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
    requestsTableModel.items = selectedRepoItem.requests.values.toList()
  }

  /**
   * This class is responsible for processing incoming updates in time and in right order.
   * Updates are coming in build worker thread but should be passed to model in EDT.
   * The order of scheduled 'invokeLater' can change that's why we need more complex logic using intermediate [updatesQueue]
   * to process updates in the right order. We also try to avoid scheduling too many 'invokeLater' calls in case of EDT lagging behind.
   *
   * To avoid stale view in case of long-running download without new events coming, as well as to avoid unprocessed events
   * left in the queue, [refreshAlarm] is used to regularly re-run model updating until build is finished.
   *
   * Un build finished we also schedule last 'process updates' task to process any possible stale updates. No further updates
   * should be coming after this point.
   */
  class ModelRefresher(val model: DownloadsInfoUIModel) {
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
    private val updatesQueue = ConcurrentLinkedQueue<DownloadRequestItem>()
    @Volatile private var immediateUpdateScheduled: Boolean = false

    init {
      invokeLater {
        object : Runnable {
          override fun run() {
            processUpdatesQueue()
            refreshAlarm.addRequest(this, 5000)
          }
        }
      }
      // On build finished schedule last updates processing task unconditionally
      Disposer.register(model.buildFinishedDisposable) { invokeLater {
        // In order to avoid data-race inside alarm isDisposed state need to dispose alarm in EDT,
        // not directly from builder thread.
        Disposer.dispose(refreshAlarm)
        processUpdatesQueue()
      }}
    }

    fun onNewItemUpdate(downloadRequest: DownloadRequestItem) {
      updatesQueue.add(downloadRequest)
      scheduleImmediateUpdateIfNecessary()
    }

    private fun scheduleImmediateUpdateIfNecessary() {
      if (!immediateUpdateScheduled) {
        immediateUpdateScheduled = true
        invokeLater {
          immediateUpdateScheduled = false
          processUpdatesQueue()
        }
      }
    }

    @UiThread
    fun processUpdatesQueue() {
      while (true) {
        val requestItem = updatesQueue.poll() ?: break
        model.updateDownloadRequest(requestItem)
      }
    }
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
  val requests = mutableMapOf<DownloadRequestKey, DownloadRequestItem>()

  fun totalNumberOfRequests() = requests.size
  fun runningNumberOfRequests() = requests.values.count { !it.completed }
  fun totalAmountOfData() = requests.values.sumOf { it.receivedBytes }
  fun numberOfFailed() = requests.values.count { it.failed }
  fun totalAmountOfTime() = requests.values.sumOf { it.duration }
  fun timeOfFailed() =  requests.values.filter { it.failed }.sumOf { it.duration }
}

class RepositoriesTableModel : ListTableModel<RepositoryTableItem>() {
  val summaryItem = RepositoryTableItem(null)
  val reposData = mutableMapOf<DownloadsAnalyzer.Repository, RepositoryTableItem>()
  init {
    fun column(title: String, tooltip: String? = null, valueOf: (RepositoryTableItem) -> String) =
      object : ColumnInfo<RepositoryTableItem, String>(title) {
        override fun valueOf(found: RepositoryTableItem): String = valueOf(found)
        override fun getPreferredStringValue() = title
        override fun getTooltipText(): String? {
          return tooltip
        }
      }
    columnInfos = arrayOf(
      column("Repository") { repoItem -> when {
        repoItem.repository == null -> "All repositories"
        repoItem.repository is DownloadsAnalyzer.KnownRepository -> repoItem.repository.presentableName
        repoItem.repository is DownloadsAnalyzer.OtherRepository -> repoItem.repository.host
        else -> error("Unexpected repository table item.")
      }},
      column("Requests", "Total number of requests.") {
        val runningRequests = it.runningNumberOfRequests()
        val totalRequests = it.totalNumberOfRequests()
        if (runningRequests > 0) "$totalRequests ($runningRequests running)"
        else totalRequests.toString()
      },
      column("Data", "Total amount of data downloaded.") { Formats.formatFileSize(it.totalAmountOfData()) },
      column("Time", "Total amount of time taken to execute requests.") { durationString(it.totalAmountOfTime()) },
      column("Avg Speed", "Average download speed.") { formatAvgDownloadSpeed(it.totalAmountOfData(), it.totalAmountOfTime()) },
      column("Failed Requests", "Number of failed requests.") { it.numberOfFailed().toString() },
      column("Failed Requests Time", "Total amount of time taken to execute failed requests.") { durationString(it.timeOfFailed()) },
    )
    addRow(summaryItem)
  }

  fun update(downloadRequest: DownloadRequestItem) {
    val repository = downloadRequest.repository
    val repoTableItem = reposData.computeIfAbsent(repository) { RepositoryTableItem(it) }
    repoTableItem.requests[downloadRequest.requestKey] = downloadRequest
    summaryItem.requests[downloadRequest.requestKey] = downloadRequest

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
  }
  val timeColumn = object : ColumnInfo<DownloadRequestItem, String>("Time") {
    override fun valueOf(item: DownloadRequestItem): String = StringUtil.formatDuration(item.duration)
    override fun getRenderer(item: DownloadRequestItem): TableCellRenderer = cellRenderer
    override fun getPreferredStringValue() = "12 s 123 ms"
    override fun getMaxStringValue(): String = preferredStringValue
  }
  val sizeColumn = object : ColumnInfo<DownloadRequestItem, String>("Size") {
    override fun valueOf(item: DownloadRequestItem): String = StringUtil.formatFileSize(item.receivedBytes)
    override fun getRenderer(item: DownloadRequestItem): TableCellRenderer = cellRenderer
    override fun getPreferredStringValue() = "123.45 MB"
    override fun getMaxStringValue(): String = preferredStringValue
  }
  val speedColumn = object : ColumnInfo<DownloadRequestItem, String>("Avg Speed") {
    override fun valueOf(item: DownloadRequestItem): String = formatAvgDownloadSpeed(item.receivedBytes, item.duration)
    override fun getRenderer(item: DownloadRequestItem): TableCellRenderer = cellRenderer
    override fun getPreferredStringValue() = "123.45 MB/s"
    override fun getMaxStringValue(): String = preferredStringValue
  }
  val statusColumn = object : ColumnInfo<DownloadRequestItem, DownloadRequestItem>("Status") {
    val columnCellRenderer = object : ColoredTableCellRenderer() {
      override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        if (value is DownloadRequestItem) {
          toolTipText = ""
          when {
            !value.completed -> {
              icon = AnimatedIcon.Default.INSTANCE
              append("Running", SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
            }
            value.failed -> {
              icon = AllIcons.General.Warning
              append("Failed", SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
              toolTipText = value.failureMessage?.replace("\n", "<br/>")
            }
            else -> {
              icon = AllIcons.RunConfigurations.TestPassed
              append("Finished", SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
            }
          }

        }
        setTextAlign(SwingConstants.RIGHT)
      }
    }
    override fun valueOf(item: DownloadRequestItem): DownloadRequestItem = item
    override fun getRenderer(item: DownloadRequestItem): TableCellRenderer = columnCellRenderer
    override fun getPreferredStringValue() = "Download Failed"
    override fun getMaxStringValue(): String = preferredStringValue
  }


  init{
    columnInfos = arrayOf(
      statusColumn,
      fileNameColumn,
      timeColumn,
      sizeColumn,
      speedColumn
    )
    isSortable = false
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
