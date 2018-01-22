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

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.tools.idea.concurrent.EdtExecutor
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.DesignAssetDetailViewModel
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.*

/**
 * View to show and edit the details of a [DesignAssetSet]
 */
class DesignAssetDetailView(
  private val designAssetDetailViewModel: DesignAssetDetailViewModel
) :
  JPanel(FlowLayout(FlowLayout.CENTER, 40, 40)),
  InternalResourceBrowser.SelectionListener {

  private var designAssetSet: DesignAssetSet? = null
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

  private fun createSingleAssetView(asset: DesignAsset) = JLabel().apply {
    border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
    preferredSize = JBUI.size(64)
    horizontalAlignment = JLabel.CENTER
    val fetchAssetImageFuture = designAssetDetailViewModel.fetchAssetImage(asset, preferredSize)
    fetchAssetImageFuture.addListener(Runnable {
      val image = fetchAssetImageFuture.get()
      if (image != null) {
        icon = ImageIcon(image)
      }
    }, EdtExecutor.INSTANCE)
  }
}