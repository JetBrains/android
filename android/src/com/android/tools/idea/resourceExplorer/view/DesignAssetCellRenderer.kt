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

import com.android.tools.idea.resourceExplorer.editor.RESOURCE_DEBUG
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.rendering.AssetPreviewManager
import com.android.tools.idea.resourceExplorer.rendering.AssetIconProvider
import com.android.tools.idea.resourceExplorer.rendering.ColorIconProvider
import com.android.tools.idea.resourceExplorer.widget.IssueLevel
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollingUtil
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.image.BufferedImage
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

val EMPTY_ICON = createIcon(if (RESOURCE_DEBUG) JBColor.GREEN else Color(0, 0, 0, 0))
val ERROR_ICON = if (RESOURCE_DEBUG) createIcon(JBColor.RED) else EMPTY_ICON

private const val VERSION = "version"

private fun String.pluralize(size: Int) = this + (if (size > 1) "s" else "")

fun createIcon(color: Color?): BufferedImage = UIUtil.createImage(
  80, 80, BufferedImage.TYPE_INT_ARGB
).apply {
  with(createGraphics()) {
    this.color = color
    fillRect(0, 0, 80, 80)
    dispose()
  }
}

/**
 * [ListCellRenderer] to render [DesignAssetSet] using an [AssetIconProvider]
 * returned by the [assetPreviewManager].
 */
class DesignAssetCellRenderer(
  private val assetPreviewManager: AssetPreviewManager
) : ListCellRenderer<DesignAssetSet> {

  val label = JLabel().apply { horizontalAlignment = JLabel.CENTER }

  override fun getListCellRendererComponent(
    list: JList<out DesignAssetSet>,
    value: DesignAssetSet,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    val assetView = (list as AssetListView).assetView
    assetView.thumbnail = label
    val thumbnailSize = assetView.thumbnailSize
    val assetToRender = value.getHighestDensityAsset()
    val iconProvider: AssetIconProvider = assetPreviewManager.getPreviewProvider(assetToRender.type)
    label.icon = iconProvider.getIcon(assetToRender,
                                      thumbnailSize.width,
                                      thumbnailSize.height,
                                      { list.getCellBounds(index, index)?.let(list::repaint) },
                                      { ScrollingUtil.isIndexFullyVisible(list, index) })
    assetView.withChessboard = iconProvider.supportsTransparency
    assetView.selected = isSelected
    with(getAssetData(value, iconProvider)) {
      assetView.title = title
      assetView.subtitle = subtitle
      assetView.metadata = metadata
    }
    if (RESOURCE_DEBUG) {
      assetView.issueLevel = IssueLevel.ERROR
      assetView.isNew = true
    }
    return assetView
  }
}

/**
 * Data class to store the information to display in [com.android.tools.idea.resourceExplorer.widget.AssetView]
 */
private data class AssetData(
  var title: String,
  var subtitle: String,
  var metadata: String
)

private fun getAssetData(assetSet: DesignAssetSet,
                         iconProvider: AssetIconProvider): AssetData {
  val title = assetSet.name
  val subtitle = if (iconProvider is ColorIconProvider) {
    val colors = iconProvider.colors
    if (colors.size == 1) "#${ColorUtil.toHex(colors.first())}" else "Multiple colors"
  }
  else {
    assetSet.getHighestDensityAsset().type.displayName
  }
  val metadata = assetSet.versionCountString()
  return AssetData(title, subtitle, metadata)
}

private fun DesignAssetSet.versionCountString(): String {
  val size = designAssets.size
  return "$size $VERSION".pluralize(size)
}