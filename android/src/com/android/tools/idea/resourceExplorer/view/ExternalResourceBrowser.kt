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
import com.android.tools.idea.resourceExplorer.viewmodel.ExternalBrowserViewModel
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.*

/**
 * File browser to display Design assets outside the project.
 */
class ExternalResourceBrowser(
  facet: AndroidFacet,
  private val resourceBrowserViewModel: ExternalBrowserViewModel,
  qualifierMatcherPanel: QualifierMatcherPanel
) : JPanel(BorderLayout()) {

  private val listItemSize = 120
  private val listColumn = 4
  private val designAssetsList: DesignAssetsList

  init {
    customizeUI()
    val centerPanel = JPanel(BorderLayout())
    val preview = createPreviewPane()
    centerPanel.add(preview, BorderLayout.NORTH)
    centerPanel.add(qualifierMatcherPanel)
    designAssetsList = DesignAssetsList(resourceBrowserViewModel)
    designAssetsList.addListSelectionListener {
      updatePreview(preview)
    }
    designAssetsList.fixedCellWidth = JBUI.scale(listItemSize)
    designAssetsList.fixedCellHeight = JBUI.scale(listItemSize)
    designAssetsList.background = UIUtil.getListBackground()
    add(createFileChooserPanel(facet), BorderLayout.NORTH)
    add(centerPanel)
    add(createImportButton(), BorderLayout.SOUTH)
  }

  private fun customizeUI() {
    border = JBUI.Borders.customLine(JBColor.border(), 0, 1, 0, 0)
  }

  private fun createFileChooserPanel(facet: AndroidFacet): JPanel {
    val browser = JPanel(VerticalFlowLayout())
    val scrollPane = createAssetListScrollPane()
    val browseButton = TextFieldWithBrowseButton(JTextField(), null)
    val lastOpenedFile = FileChooserUtil.getLastOpenedFile(facet.module.project)
    val updateDirectory: (VirtualFile) -> Unit = { file ->
      browseButton.text = file.path
      resourceBrowserViewModel.setDirectory(file)
    }
    browseButton.textField.columns = 10
    browseButton.addActionListener {
      FileChooser.chooseFile(
        FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        facet.module.project,
        lastOpenedFile,
        updateDirectory
      )
    }
    browser.add(browseButton)
    browser.add(scrollPane)
    if (lastOpenedFile != null) {
      updateDirectory(lastOpenedFile)
    }
    return browser
  }

  private fun createAssetListScrollPane(): JScrollPane {
    return JScrollPane(designAssetsList).apply {
      viewport.preferredSize = JBUI.size(listItemSize * listColumn - verticalScrollBar.size.width, 500)
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