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

import com.intellij.build.events.BuildEventPresentationData
import com.intellij.build.events.PresentableBuildEvent
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * This execution console is installed to build output window and is shown when "Download info" node is selected.
 * It is subscribed to processed download requests events received from Gradle TAPI and is updated live.
 * [DownloadsInfoUIModel] contains the logic of how this page is populated and reacts on user interactions.
 */
class DownloadsInfoExecutionConsole(
  buildId: ExternalSystemTaskId,
  val buildFinishedDisposable: Disposable
) : ExecutionConsole {
  val uiModel = DownloadsInfoUIModel(buildId, buildFinishedDisposable)
  val table = TableView(uiModel.requestsTableModel).apply {
    name = "requests table"
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setShowGrid(false)
    tableHeader.reorderingAllowed = false
    setEmptyState("No download requests")
  }

  val reposTable = TableView(uiModel.repositoriesTableModel).apply {
    name = "repositories table"
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setShowGrid(false)
    tableHeader.reorderingAllowed = false
    selectionModel.addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      uiModel.repoSelectionUpdated(selectedObject)
    }
  }

  private val panel by lazy { JPanel().apply {
    layout = BorderLayout()
    reposTable.visibleRowCount = 5
    val browserLink = BrowserLink(
      "Read more on repositories optimization",
      // TODO (b/231146116):need redirect
      "https://docs.gradle.org/current/userguide/performance.html#optimize_repository_order"
    ).apply {
      border = JBUI.Borders.empty(10)
    }
    val splitter = OnePixelSplitter(true).apply {
      firstComponent = ScrollPaneFactory.createScrollPane(reposTable)
      secondComponent = ScrollPaneFactory.createScrollPane(table)
    }
    add(browserLink, BorderLayout.NORTH)
    add(splitter, BorderLayout.CENTER)
  }}

  override fun dispose() {
    Disposer.dispose(buildFinishedDisposable)
  }
  override fun getComponent(): JComponent = panel
  override fun getPreferredFocusableComponent(): JComponent = table
}

@Suppress("UnstableApiUsage")
class DownloadsInfoPresentableEvent(
  val buildId: ExternalSystemTaskId,
  val buildFinishedDisposable: Disposable
) : PresentableBuildEvent {
  override fun getId(): Any = "Downloads info"
  override fun getParentId(): Any = buildId
  override fun getEventTime(): Long = 0
  override fun getMessage(): String = "Downloads info"
  override fun getHint(): String? = null

  override fun getDescription(): String? = null

  override fun getPresentationData(): BuildEventPresentationData = object : BuildEventPresentationData {
    private val downloadsExecutionConsole = DownloadsInfoExecutionConsole(buildId, buildFinishedDisposable)
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