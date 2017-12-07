/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.view

import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.ResourceBrowserViewModel
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.*

/**
 * File browser to display [DesignAssetSet].
 */
class ExternalResourceBrowser(facet: AndroidFacet) : JPanel(BorderLayout()) {

  private val resourceBrowserViewModel = ResourceBrowserViewModel(facet)

  private val designAssetsList = DesignAssetsList(resourceBrowserViewModel.designAssetListModel)

  init {
    customizeUI()
    val preview = createPreviewPane()
    designAssetsList.addListSelectionListener {
      updatePreview(preview)
    }

    add(createFileChooserPanel(facet), BorderLayout.NORTH)
    add(preview)
    add(createImportButton(), BorderLayout.SOUTH)
  }

  private fun customizeUI() {
    border = JBUI.Borders.customLine(JBColor.border(), 0, 1, 0, 0)
    preferredSize = JBUI.size(400, -1)
  }

  private fun createFileChooserPanel(facet: AndroidFacet): JPanel {
    val browser = JPanel(VerticalFlowLayout())
    val browseButton = TextFieldWithBrowseButton(JTextField(), null)
    browseButton.addActionListener {
      FileChooser.chooseFile(
          FileChooserDescriptorFactory.createSingleFolderDescriptor(),
          facet.module.project,
          null, { file ->
        browseButton.text = file.path
        resourceBrowserViewModel.setDirectory(file)
      })
    }
    browser.add(browseButton)
    browser.add(createAssetListScrollPane())
    return browser
  }

  private fun createAssetListScrollPane(): JScrollPane {
    return JScrollPane(designAssetsList).apply {
      preferredSize = JBUI.size(-1, 400)
    }
  }

  private fun createImportButton(): JButton {
    return JButton("Import").apply {
      addActionListener {
        designAssetsList.selectedValue
            ?.apply(resourceBrowserViewModel::importDesignAssetSet)
      }
    }
  }

  private fun createPreviewPane() = JPanel(VerticalFlowLayout())

  private fun updatePreview(preview: JPanel) {
    preview.removeAll()
    designAssetsList.selectedValue
        .designAssets
        .map(DesignAsset::file)
        .map { file -> JLabel(file.name, ImageIcon(file.path), JLabel.LEFT) }
        .forEach { preview.add(it) }
    preview.revalidate()
    preview.repaint()
  }
}