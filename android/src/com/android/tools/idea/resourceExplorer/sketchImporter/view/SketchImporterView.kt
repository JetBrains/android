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
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.Gray
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private val PAGE_HEADER_SECONDARY_COLOR = Gray.x66
private val PAGE_HEADER_BORDER = BorderFactory.createCompoundBorder(
  BorderFactory.createEmptyBorder(0, 0, 8, 0),
  JBUI.Borders.customLine(PAGE_HEADER_SECONDARY_COLOR, 0, 0, 1, 0)
)

class SketchImporterView {
  private fun getPreviewPanel(filePath: String, facet: AndroidFacet): JPanel {

    return JPanel(VerticalFlowLayout()).apply {
      val presenter = SketchImporterPresenter(filePath)
      val pageIdToFiles = presenter.generateFiles(facet.module.project)

      if (pageIdToFiles == null)
        add(JLabel("Invalid Sketch file!"))
      else
        for (pageId in pageIdToFiles.keys) {
          add(createPageHeader(presenter.importOptions.getOptions(pageId)!!.name))
        }
    }
  }

  fun getConfigurationPanel(facet: AndroidFacet, fileExtension: String): JPanel {

    return JPanel(BorderLayout()).apply {
      preferredSize = JBUI.size(600, 400)
      val filePath = JLabel()
      val fileDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(fileExtension)
      FileChooser.chooseFiles(fileDescriptor, facet.module.project, null) { files ->
        filePath.text = FileUtil.toSystemDependentName(files[0].path)
      }

      add(filePath, BorderLayout.NORTH)

      add(getPreviewPanel(filePath.text, facet))
    }
  }

  private fun createPageHeader(name: String): JComponent = JPanel(BorderLayout()).apply {
    val nameLabel = JBLabel(name)
    nameLabel.font = nameLabel.font.deriveFont(24f)
    add(nameLabel)

    border = PAGE_HEADER_BORDER
  }
}