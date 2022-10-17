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

import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.model.DownloadsInfoPageModel
import com.intellij.openapi.ui.setEmptyState
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class DownloadsInfoPageView(
  private val pageModel: DownloadsInfoPageModel,
  val actionHandlers: ViewActionHandlers
) : BuildAnalyzerDataPageView {

  val resultsTable = TableView(pageModel.repositoriesTableModel).apply {
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    setShowGrid(false)
    tableHeader.reorderingAllowed = false
    setEmptyState(pageModel.repositoriesTableEmptyText)
    selectionModel.addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      pageModel.selectedRepositoriesUpdated(selectedObjects)
    }
  }

  val requestsList = TableView(pageModel.requestsListModel).apply {
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setShowGrid(false)
    tableHeader.reorderingAllowed = false
    setEmptyState("Select repositories on the left to read request details")
  }

  override val component: JPanel = JPanel().apply {
    name = "downloads-info-view"
    border = JBUI.Borders.empty(20)
    layout = BorderLayout(0, JBUI.scale(10))

    val linksHandler = HtmlLinksHandler(actionHandlers)
    val learnMoreLink = linksHandler.externalLink("Learn more", BuildAnalyzerBrowserLinks.DOWNLOADS)

    val pageHeaderText = """
      Incremental builds should not consistently download artifacts. This could indicate use of<BR/>
      dynamic versions of dependencies or other issues in your configuration. $learnMoreLink.<BR/>
      <BR/>
      Time required for Grade to download artifacts from repositories<BR/>
    """.trimIndent()
    // Need to wrap in another panel here otherwise some BorderLayout layout magic makes text label be of 0px height.
    val header = JPanel(BorderLayout()).apply {
      add(htmlTextLabelWithFixedLines(pageHeaderText, linksHandler), BorderLayout.CENTER)
    }
    val splitter = OnePixelSplitter(0.4f)
    splitter.firstComponent = createScrollPane(resultsTable)
    if (pageModel.repositoriesTableModel.rowCount > 0) {
      splitter.secondComponent = createScrollPane(requestsList)
    }

    add(header, BorderLayout.NORTH)
    add(splitter, BorderLayout.CENTER)
  }

  override val additionalControls: JPanel = JPanel().apply { name = "downloads-info-view-additional-controls" }
}
