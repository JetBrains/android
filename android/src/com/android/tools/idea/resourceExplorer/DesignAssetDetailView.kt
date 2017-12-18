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
package com.android.tools.idea.resourceExplorer

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.view.InternalResourceBrowser
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * View to show and edit the details of a [DesignAssetSet]
 */
class DesignAssetDetailView :
    JPanel(FlowLayout(FlowLayout.CENTER, 40, 40)),
    InternalResourceBrowser.SelectionListener {

  var designAssetSet: DesignAssetSet? = null
    set(value) {
      field = value
      updateData()
    }

  override fun onDesignAssetSetSelected(designAssetSet: DesignAssetSet?) {
    this.designAssetSet = designAssetSet
  }

  private fun updateData() {
    val currentAssets = designAssetSet
    removeAll()
    if (currentAssets == null) {
      revalidate()
      repaint()
      return
    }
    currentAssets.designAssets
        .sortedBy { designAsset ->
          designAsset.qualifiers
              .filterIsInstance<DensityQualifier>()
              .firstOrNull()
              ?.value?.dpiValue ?: 0
        }
        .map { createSingleAssetView(it) }
        .forEach { add(it) }
    revalidate()
    repaint()
  }

  private fun createSingleAssetView(asset: DesignAsset): JPanel {
    val root = JPanel(BorderLayout())
    root.border = BorderFactory.createStrokeBorder(BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1f, floatArrayOf(5f, 0f), 0f))
    root.preferredSize = JBUI.size(50)
    // TODO Use a plugin to get the preview
    val jLabel = JLabel(ImageIcon(asset.file.contentsToByteArray()))
    jLabel.preferredSize = JBUI.size(30)
    root.add(jLabel)
    return root
  }
}