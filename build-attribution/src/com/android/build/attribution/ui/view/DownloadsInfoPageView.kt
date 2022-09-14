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

import com.android.build.attribution.ui.model.DownloadsInfoPageModel
import com.intellij.openapi.ui.setEmptyState
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class DownloadsInfoPageView(
  private val pageModel: DownloadsInfoPageModel,
  val actionHandlers: ViewActionHandlers
) : BuildAnalyzerDataPageView {

  private val resultsTable = TableView(pageModel.repositoriesTableModel).apply {
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    setShowGrid(false)
    tableHeader.reorderingAllowed = false
    setEmptyState(pageModel.repositoriesTableEmptyText)
    selectionModel.addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      pageModel.selectedRepositoriesUpdated(selectedObjects)
    }
  }

  private val requestsList = TableView(pageModel.requestsListModel).apply {
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setShowGrid(false)
    tableHeader.reorderingAllowed = false
    setEmptyState("Select one or more repositories on the left to see requests info.")
  }

  override val component: JPanel = JPanel().apply {
    name = "downloads-info-view"
    border = JBUI.Borders.empty(20)
    layout = BorderLayout(0, JBUI.scale(10))

    val pageHeaderText = "This table shows time Gradle took to download artifacts from repositories."
    add(JBLabel(pageHeaderText), BorderLayout.NORTH)
    val splitter = OnePixelSplitter(0.4f)
    splitter.firstComponent = createScrollPane(resultsTable)
    splitter.secondComponent = createScrollPane(requestsList)
    add(splitter, BorderLayout.CENTER)
  }

  override val additionalControls: JPanel = JPanel().apply { name = "downloads-info-view-additional-controls" }
}
