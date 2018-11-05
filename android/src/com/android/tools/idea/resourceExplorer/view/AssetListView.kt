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

import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.widget.AssetView
import com.android.tools.idea.resourceExplorer.widget.RowAssetView
import com.android.tools.idea.resourceExplorer.widget.SingleAssetCard
import com.intellij.ui.CollectionListModel
import com.intellij.util.ui.JBUI
import java.awt.event.MouseEvent
import javax.swing.JList
import kotlin.properties.Delegates

private val DEFAULT_PREVIEW_SIZE = JBUI.scale(50)

private const val DEFAULT_GRID_MODE = false

/**
 * [JList] to display [DesignAssetSet] and handle switching
 * between grid and list mode.
 */
class AssetListView(
  assets: List<DesignAssetSet>
) : JList<DesignAssetSet>() {

  var isGridMode: Boolean by Delegates.observable(DEFAULT_GRID_MODE) { _, _, isGridMode ->
    if (isGridMode) {
      layoutOrientation = JList.HORIZONTAL_WRAP
      assetView = SingleAssetCard()
    }
    else {
      layoutOrientation = JList.VERTICAL
      assetView = RowAssetView()
    }
    updateCellSize()
  }

  lateinit var assetView: AssetView
    private set


  /**
   * Width of the [AssetView] thumbnail container
   */
  var thumbnailWidth: Int  by Delegates.observable(DEFAULT_PREVIEW_SIZE) { _, oldWidth, newWidth ->
    if (oldWidth != newWidth) {
      updateCellSize()
    }
  }

  init {
    model = CollectionListModel(assets)
    isOpaque = false
    visibleRowCount = 0
    isGridMode = DEFAULT_GRID_MODE
  }

  private fun updateCellSize() {
    assetView.viewWidth = thumbnailWidth
    fixedCellWidth = assetView.preferredSize.width
    fixedCellHeight = assetView.preferredSize.height
    revalidate()
    repaint()
  }

  // The default implementation will will generate the tooltip from the
  // list renderer, which is quite expensive in our case, and not needed.
  override fun getToolTipText(event: MouseEvent?): String? = null
}