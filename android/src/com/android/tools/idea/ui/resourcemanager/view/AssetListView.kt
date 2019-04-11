/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.view

import com.android.tools.idea.ui.resourcemanager.model.DesignAssetSet
import com.android.tools.idea.ui.resourcemanager.widget.AssetView
import com.android.tools.idea.ui.resourcemanager.widget.RowAssetView
import com.android.tools.idea.ui.resourcemanager.widget.SingleAssetCard
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ui.JBUI
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.ListModel
import kotlin.properties.Delegates

private val DEFAULT_PREVIEW_SIZE = JBUI.scale(50)

private const val DEFAULT_GRID_MODE = false

/**
 * [JList] to display [DesignAssetSet] and handle switching
 * between grid and list mode.
 */
class AssetListView(
  assets: List<DesignAssetSet>,
  speedSearch: SpeedSearch? = null
) : JBList<DesignAssetSet>() {

  var isGridMode: Boolean by Delegates.observable(DEFAULT_GRID_MODE) { _, _, isGridMode ->
    if (isGridMode) {
      layoutOrientation = JList.HORIZONTAL_WRAP
      assetView = SingleAssetCard()
      setExpandableItemsEnabled(false)
    }
    else {
      layoutOrientation = JList.VERTICAL
      assetView = RowAssetView()
      setExpandableItemsEnabled(true)
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

  private val filteringListModel: FilteringListModel<DesignAssetSet>?

  init {
    isOpaque = false
    visibleRowCount = 0
    isGridMode = DEFAULT_GRID_MODE
    val collectionListModel = CollectionListModel(assets)
    if (speedSearch != null) {
      filteringListModel = createFilteringListModel(speedSearch, collectionListModel)
      model = filteringListModel
    }
    else {
      filteringListModel = null
      model = collectionListModel
    }
  }

  private fun createFilteringListModel(speedSearch: SpeedSearch,
                                       collectionListModel: CollectionListModel<DesignAssetSet>
  ): NameFilteringListModel<DesignAssetSet> {
    speedSearch.setEnabled(true)
    return NameFilteringListModel(collectionListModel, { it.name }, speedSearch::shouldBeShowing,
                                  { StringUtil.notNullize(speedSearch.filter) })
  }

  /**
   * If a [SpeedSearch] was provided in constructor, filters the list items using the [SpeedSearch.getFilter].
   */
  fun refilter() {
    filteringListModel?.refilter()
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