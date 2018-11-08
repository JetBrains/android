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
package com.android.tools.idea.resourceExplorer.view

import com.android.resources.ResourceFolderType
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.FileImportRowViewModel
import com.android.tools.idea.resourceExplorer.viewmodel.ResourceImportDialogViewModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JPanel

private const val DIALOG_TITLE = "Import drawables"
private val DIALOG_SIZE = JBUI.size(1000, 700)

private val ASSET_GROUP_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
  JBUI.Borders.empty(12, 0, 8, 0))

private val NORTH_PANEL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
  JBUI.Borders.empty(16))

private val CONTENT_PANEL_BORDER = JBUI.Borders.empty(0, 20)

/**
 * Dialog allowing user to edit option before importing resources.
 */
class ResourceImportDialog(
  private val dialogViewModel: ResourceImportDialogViewModel
) : DialogWrapper(dialogViewModel.facet.module.project, true, DialogWrapper.IdeModalityType.MODELESS) {

  constructor(facet: AndroidFacet, assetSets: List<DesignAssetSet>) :
    this(ResourceImportDialogViewModel(facet, assetSets))


  private val content = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = CONTENT_PANEL_BORDER
    dialogViewModel.assetSets.forEach {
      add(designAssetSetView(it))
    }
  }

  val root = JBScrollPane(content).apply {
    preferredSize = DIALOG_SIZE
    border = null
  }

  private val northPanel = JPanel(BorderLayout()).apply {
    border = NORTH_PANEL_BORDER
    val importedAssetCount = dialogViewModel.fileCount
    add(JBLabel("$importedAssetCount ${StringUtil.pluralize("resource", importedAssetCount)} ready to be imported"), BorderLayout.WEST)
    add(JBLabel("Import more assets", AllIcons.Actions.Upload, JBLabel.LEFT), BorderLayout.EAST)
  }

  init {
    setOKButtonText("Import")
    setSize(DIALOG_SIZE.width(), DIALOG_SIZE.height())
    setResizable(false)
    isOKActionEnabled = true
    title = DIALOG_TITLE
    init()
  }

  override fun doOKAction() {
    dialogViewModel.doImport()
    super.doOKAction()
  }

  override fun createCenterPanel() = root
  override fun createNorthPanel() = northPanel
  override fun getStyle() = DialogStyle.COMPACT

  private fun designAssetSetView(assetSet: DesignAssetSet): JPanel {
    val assetNameLabel = JBLabel(assetSet.name, UIUtil.ComponentStyle.LARGE)
    val itemNumberLabel = JBLabel(dialogViewModel.getItemNumberString(assetSet),
                                  UIUtil.ComponentStyle.SMALL,
                                  UIUtil.FontColor.BRIGHTER)
    val newAlternativeButton = JBLabel("New alternative", StudioIcons.Common.ADD, JBLabel.RIGHT)

    return JPanel(VerticalFlowLayout(true, false)).apply {
      add(
        JPanel(BorderLayout()).apply {
          border = ASSET_GROUP_BORDER
          add(JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            (layout as FlowLayout).alignOnBaseline = true
            add(assetNameLabel)
            add(itemNumberLabel)
          }, BorderLayout.WEST)
          add(newAlternativeButton, BorderLayout.EAST)
        }
      )
      assetSet.designAssets.forEach { asset ->
        add(singleAssetView(asset))
      }
    }
  }

  private fun singleAssetView(asset: DesignAsset): FileImportRow {
    val fileImportRow = FileImportRow(FileImportRowViewModel(asset, ResourceFolderType.DRAWABLE))
    fileImportRow.preview.icon = ImageIcon(dialogViewModel.getAssetPreview(asset))
    return fileImportRow
  }
}