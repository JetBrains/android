/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.view

import com.android.tools.idea.resourceExplorer.sketchImporter.presenter.SketchImporterPresenter
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.Gray
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.*


private val PAGE_HEADER_SECONDARY_COLOR = Gray.x66
private val PAGE_HEADER_BORDER = BorderFactory.createCompoundBorder(
  BorderFactory.createEmptyBorder(0, 0, 8, 0),
  JBUI.Borders.customLine(PAGE_HEADER_SECONDARY_COLOR, 0, 0, 1, 0)
)
private val PANEL_SIZE = JBUI.size(600, 400)

class SketchImporterView(private val presenter: SketchImporterPresenter) {

  private val previewPanel = JPanel(VerticalFlowLayout())

  private val configurationPanel: JPanel = JPanel(BorderLayout()).apply {
    preferredSize = PANEL_SIZE
    add(JScrollPane(previewPanel))
  }

  /**
   * Adds a preview for the [vectorDrawableFiles] corresponding to the page named [pageName] of type [pageTypes] [[selectedTypeIndex]])
   */
  fun addIconPage(pageName: String,
                  pageTypes: Array<String>,
                  selectedTypeIndex: Int,
                  vectorDrawableFiles: List<LightVirtualFile>) {
    previewPanel.add(createPageHeader(pageName, pageTypes, selectedTypeIndex))
    val listModel = DefaultListModel<VirtualFile>().apply {
      vectorDrawableFiles.forEach { addElement(it) }
    }
    // Render icons
  }

  /**
   * Creates page header containing the [pageName] and the [JComboBox] with the [pageTypes], where [selectedTypeIndex] is selected.
   */
  private fun createPageHeader(pageName: String, pageTypes: Array<String>, selectedTypeIndex: Int): JComponent {
    return JPanel(BorderLayout()).apply {
      val nameLabel = JBLabel(pageName)
      nameLabel.font = nameLabel.font.deriveFont(24f)
      add(nameLabel)

      val pageTypeList = JComboBox(pageTypes)
      pageTypeList.selectedIndex = selectedTypeIndex
      add(pageTypeList, BorderLayout.EAST)

      border = PAGE_HEADER_BORDER
    }
  }

  /**
   * Creates the dialog allowing the user to preview and choose which assets they would like to import from the sketch file.
   */
  fun createImportDialog(facet: AndroidFacet, view: SketchImporterView) {
    with(DialogBuilder(facet.module.project)) {
      setCenterPanel(view.configurationPanel)
      setOkOperation {
        presenter.importFilesIntoProject(facet)
        dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
      }
      setTitle("Choose the assets you would like to import")
      showModal(true)
    }
  }
}