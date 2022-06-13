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
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class DownloadsInfoPageView(
  val pageModel: DownloadsInfoPageModel,
  val actionHandlers: ViewActionHandlers
) : BuildAnalyzerDataPageView {

  private val resultsTable = TableView(pageModel.repositoriesTableModel).apply {
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setShowGrid(false)
    tableHeader.reorderingAllowed = false
    setEmptyState(pageModel.repositoriesTableEmptyText)
  }

  override val component: JPanel = JPanel().apply {
    name = "downloads-info-view"
    border = JBUI.Borders.empty(20)
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(JPanel().apply {
      layout = BorderLayout(0, JBUI.scale(10))
      maximumSize = JBUI.size(800, Int.MAX_VALUE)
      alignmentX = Component.LEFT_ALIGNMENT

      add(createScrollPane(resultsTable), BorderLayout.CENTER)
    })
  }

  override val additionalControls: JPanel = JPanel().apply { name = "downloads-info-view-additional-controls" }
}