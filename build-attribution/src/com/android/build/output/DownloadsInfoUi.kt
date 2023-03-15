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

import com.android.build.attribution.analyzers.minGradleVersionProvidingDownloadEvents
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildOutputDownloadsInfoEvent
import com.intellij.build.events.BuildEventPresentationData
import com.intellij.build.events.PresentableBuildEvent
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TableViewSpeedSearch
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import org.gradle.util.GradleVersion
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

/**
 * This execution console is installed to build output window and is shown when "Download info" node is selected.
 * It is subscribed to processed download requests events received from Gradle TAPI and is updated live.
 * [DownloadsInfoUIModel] contains the logic of how this page is populated and reacts on user interactions.
 */
class DownloadsInfoExecutionConsole(
  val buildId: ExternalSystemTaskId,
  val buildFinishedDisposable: CheckedDisposable,
  val buildStartTimestampMs: Long,
  val gradleVersion: GradleVersion?
) : ExecutionConsole {
  // TODO (b/271258614): In an unlikely case when build is finished before running this code this will result in an error.
  private val listenBuildEventsDisposable = Disposer.newDisposable(buildFinishedDisposable, "DownloadsInfoExecutionConsole")
  val uiModel = DownloadsInfoUIModel(buildId, listenBuildEventsDisposable)

  val requestsTable = object : TableView<DownloadRequestItem>(uiModel.requestsTableModel) {
    override fun createRowSorter(model: TableModel?): TableRowSorter<TableModel?> {
      return object : DefaultColumnInfoBasedRowSorter(model) {
        override fun toggleSortOrder(column: Int) {
          if (isSortable(column)) {
            val oldOrder = sortKeys.firstOrNull()?.takeIf { it.column == column }?.sortOrder ?: SortOrder.UNSORTED
            sortKeys = listOf(SortKey(column, oldOrder.nextSortOrder()))
          }
        }
      }
    }
    private fun SortOrder.nextSortOrder(): SortOrder = when(this) {
      SortOrder.ASCENDING -> SortOrder.DESCENDING
      SortOrder.DESCENDING -> SortOrder.UNSORTED
      SortOrder.UNSORTED -> SortOrder.ASCENDING
    }
  }.apply {
    name = "requests table"
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setShowGrid(false)
    columnSelectionAllowed = false
    tableHeader.reorderingAllowed = false
    val speedSearch = object : TableViewSpeedSearch<DownloadRequestItem>(this) {
      override fun getItemText(element: DownloadRequestItem): String = element.requestKey.url
    }
    speedSearch.setFilteringMode(true)
  }

  val reposTable = TableView(uiModel.repositoriesTableModel).apply {
    name = "repositories table"
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setShowGrid(false)
    columnSelectionAllowed = false
    tableHeader.reorderingAllowed = false
    selectionModel.addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      uiModel.repoSelectionUpdated(selectedObject)
      logUserEvent(BuildOutputDownloadsInfoEvent.Interaction.SELECT_REPOSITORY_ROW)
    }
  }

  private val panel by lazy { JBPanelWithEmptyText().apply {
    layout = BorderLayout()
    name = "downloads info build output panel"
    withEmptyText(createEmptyText())
    reposTable.visibleRowCount = 5
    val browserLink = BrowserLink(
      "Read more on repositories optimization",
      // TODO (b/231146116):need redirect
      "https://docs.gradle.org/current/userguide/performance.html#optimize_repository_order"
    ).apply {
      border = JBUI.Borders.empty(10)
      addActionListener { logUserEvent(BuildOutputDownloadsInfoEvent.Interaction.CLICK_LEARN_MORE_LINK) }
    }
    val splitter = OnePixelSplitter(true).apply {
      firstComponent = ScrollPaneFactory.createScrollPane(reposTable)
      secondComponent = ScrollPaneFactory.createScrollPane(requestsTable)
    }
    add(browserLink, BorderLayout.NORTH)
    add(splitter, BorderLayout.CENTER)

    addComponentListener(object: ComponentAdapter() {
      override fun componentShown(e: ComponentEvent?) {
        logUserEvent(BuildOutputDownloadsInfoEvent.Interaction.OPEN_DOWNLOADS_INFO_UI)
      }
    })

    uiModel.addAndFireDataUpdateListener {
      val isEmpty = uiModel.repositoriesTableModel.summaryItem.requests.isEmpty()
      components.forEach { it.isVisible = !isEmpty }
    }
  }}

  private fun createEmptyText() = if (gradleVersion?.let { it < minGradleVersionProvidingDownloadEvents } == true) {
    "Minimal Gradle version providing downloads data is ${minGradleVersionProvidingDownloadEvents.version}"
  }
  else {
    "No download requests"
  }

  override fun dispose() {
    Disposer.dispose(listenBuildEventsDisposable)
  }
  override fun getComponent(): JComponent = panel
  override fun getPreferredFocusableComponent(): JComponent = requestsTable

  private fun logUserEvent(reportedInteraction: BuildOutputDownloadsInfoEvent.Interaction) {
    buildId.findProject()?.let { project: Project ->
      val event = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.BUILD_OUTPUT_DOWNLOADS_INFO_USER_INTERACTION)
        .setBuildOutputDownloadsInfoEvent(BuildOutputDownloadsInfoEvent.newBuilder().apply {
          view = if (buildId.type == ExternalSystemTaskType.RESOLVE_PROJECT) BuildOutputDownloadsInfoEvent.View.SYNC_VIEW else BuildOutputDownloadsInfoEvent.View.BUILD_VIEW
          msSinceBuildStart = (System.currentTimeMillis() - buildStartTimestampMs).toInt()
          buildFinished = buildFinishedDisposable.isDisposed
          interaction = reportedInteraction
        })
      UsageTracker.log(event.withProjectId(project))
    }
  }
}

@Suppress("UnstableApiUsage")
class DownloadsInfoPresentableEvent(
  val buildId: ExternalSystemTaskId,
  val buildFinishedDisposable: CheckedDisposable,
  val buildStartTimestampMs: Long,
  val gradleVersion: GradleVersion?
) : PresentableBuildEvent {
  override fun getId(): Any = "Download info"
  override fun getParentId(): Any = buildId
  override fun getEventTime(): Long = 0
  override fun getMessage(): String = "Download info"
  override fun getHint(): String? = null
  override fun getDescription(): String? = null
  override fun getPresentationData(): BuildEventPresentationData = object : BuildEventPresentationData {
    private val downloadsExecutionConsole = DownloadsInfoExecutionConsole(buildId, buildFinishedDisposable, buildStartTimestampMs, gradleVersion)
    override fun getNodeIcon(): Icon = DownloadsNodeIcon(downloadsExecutionConsole.uiModel)
    override fun getExecutionConsole(): ExecutionConsole = downloadsExecutionConsole
    override fun consoleToolbarActions(): ActionGroup? = null
  }

  /**
   * There is no way to update icon of the existing node with current API, so instead we install this special icon
   * that can change its appearance reacting on changes in page model.
   */
  private class DownloadsNodeIcon(model: DownloadsInfoUIModel) : LayeredIcon(
    AllIcons.Actions.Download,
    AnimatedIcon.Default.INSTANCE
  ) {
    init {
      // We do not care about removing listeners because there supposed to be 1-1 presentation to model relationship and they should
      // be released all together.
      model.addAndFireDataUpdateListener {
        setIconRunningStateEnabled(model.repositoriesTableModel.summaryItem.runningNumberOfRequests() > 0)
      }
    }

    private fun setIconRunningStateEnabled(enabled: Boolean) {
      setLayerEnabled(0, !enabled)
      setLayerEnabled(1, enabled)
    }
  }
}