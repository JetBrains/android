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

import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.ResourceImportDialogViewModel
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.*

private val ASSET_H_GAP = JBUI.scale(50)
private val ASSET_V_GAP = JBUI.scale(5)
private val BTN_GAP = JBUI.scale(10)
private val DIALOG_SIZE = JBUI.size(400, 300)

/**
 * Dialog allowing user to edit option before importing resources.
 */
class ResourceImportDialog(assetSets: List<DesignAssetSet>,
                           facet: AndroidFacet,
                           private val viewModel: ResourceImportDialogViewModel = ResourceImportDialogViewModel(facet, assetSets)) {

  val content = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    assetSets.forEach {
      add(designAssetSetView(it))
    }
  }

  val root = JBScrollPane(content).apply {
    preferredSize = DIALOG_SIZE
  }

  private fun designAssetSetView(assetSet: DesignAssetSet) = JPanel(GridLayout(0, 1)).apply {
    border = BorderFactory.createTitledBorder(assetSet.name)
    assetSet.designAssets.forEach { asset ->
      add(singleAssetView(asset))
    }
  }

  private fun singleAssetView(asset: DesignAsset) = JPanel(BorderLayout()).apply {
    val density = viewModel.getAssetDensity(asset)
    val icon = ImageIcon(viewModel.getAssetPreview(asset))
    val imageRealSize = viewModel.getRealSize(asset)

    add(JLabel(icon), BorderLayout.WEST)
    add(JPanel(FlowLayout(FlowLayout.LEFT, ASSET_H_GAP, ASSET_V_GAP)).apply {
      add(qualifierDetails(density, imageRealSize))
    })
    add(buttonsBar(), BorderLayout.EAST)
  }

  private fun qualifierDetails(primary: String, secondary: String) = Box.createVerticalBox().apply {
    add(JLabel(primary))
    add(JLabel(secondary))
  }

  private fun buttonsBar() = JPanel().apply {
    add(
      JPanel(GridLayout(1, 2, BTN_GAP, BTN_GAP)).apply {
        add(JButton(StudioIcons.Common.EDIT))
        add(JButton(StudioIcons.Common.DELETE))
      })
  }

  fun show() {
    with(DialogBuilder()) {
      centerPanel(root)
      title("Import drawables")
      resizable(false)
      setOkOperation {
        viewModel.doImport()
        dialogWrapper.close(0, true)
      }
      showModal(true)
    }

    content.requestFocus()
  }
}