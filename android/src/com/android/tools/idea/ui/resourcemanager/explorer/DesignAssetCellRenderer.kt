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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.ui.resourcemanager.RESOURCE_DEBUG
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.rendering.AssetData
import com.android.tools.idea.ui.resourcemanager.rendering.AssetIconProvider
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManager
import com.android.tools.idea.ui.resourcemanager.rendering.DefaultIconProvider
import com.android.tools.idea.ui.resourcemanager.widget.IssueLevel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

val EMPTY_ICON = createIcon(if (RESOURCE_DEBUG) JBColor.GREEN else Color(0, 0, 0, 0))
val ERROR_ICON = createIcon(if (RESOURCE_DEBUG) JBColor.RED else Color(10, 10, 10, 10))

fun createIcon(color: Color?): BufferedImage = UIUtil.createImage(
  80, 80, BufferedImage.TYPE_INT_ARGB
).apply {
  with(createGraphics()) {
    this.color = color
    fillRect(0, 0, 80, 80)
    dispose()
  }
}

fun createFailedIcon(dimension: Dimension): BufferedImage {
  @Suppress("UndesirableClassUsage") // Dimensions for BufferedImage are pre-scaled.
  val image =  BufferedImage(dimension.width, dimension.height, BufferedImage.TYPE_INT_ARGB)
  val label = JBLabel("Failed preview", StudioIcons.Common.WARNING, SwingConstants.CENTER).apply {
    verticalTextPosition = SwingConstants.BOTTOM
    horizontalTextPosition = SwingConstants.CENTER
    foreground = AdtUiUtils.DEFAULT_FONT_COLOR
    bounds = Rectangle(0, 0, dimension.width, dimension.height)
    validate()
  }
  image.createGraphics().let { g ->
    val labelFont = JBUI.Fonts.label(10f)
    val stringWidth = labelFont.getStringBounds(label.text, g.fontRenderContext).width
    val targetWidth = dimension.width - JBUI.scale(4) // Add some minor padding
    val scale = minOf(targetWidth.toFloat() / stringWidth.toFloat(), 1f) // Only scale down to fit.
    label.font = labelFont.deriveFont(scale * labelFont.size)
    label.paint(g)
    g.dispose()
  }
  return image
}

/**
 * [ListCellRenderer] to render [ResourceAssetSet] using an [AssetIconProvider]
 * returned by the [assetPreviewManager].
 */
class DesignAssetCellRenderer(
  private val assetPreviewManager: AssetPreviewManager
) : ListCellRenderer<ResourceAssetSet> {

  val label = JLabel().apply { horizontalAlignment = JLabel.CENTER }

  override fun getListCellRendererComponent(
    list: JList<out ResourceAssetSet>,
    value: ResourceAssetSet,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    val assetView = (list as AssetListView).assetView
    val thumbnailSize = assetView.thumbnailSize
    val assetToRender = value.getHighestDensityAsset()

    val iconProvider: AssetIconProvider = assetPreviewManager.getPreviewProvider(assetToRender.type)
    label.icon = if (assetToRender is DesignAsset) {
      iconProvider.getIcon(assetToRender,
                           thumbnailSize.width,
                           thumbnailSize.height,
                           { list.getCellBounds(index, index)?.let(list::repaint) },
                           { index in list.firstVisibleIndex..list.lastVisibleIndex })
    } else null
    // DefaultIconProvider provides an empty icon, to avoid comparison, we just set the thumbnail to null.
    assetView.thumbnail = if (iconProvider is DefaultIconProvider) null else label
    assetView.withChessboard = iconProvider.supportsTransparency
    assetView.selected = isSelected
    assetView.focused = cellHasFocus
    with(assetPreviewManager.getAssetSetData(value)) {
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

private fun AssetPreviewManager.getAssetSetData(assetSet: ResourceAssetSet): AssetData {
  val asset = assetSet.getHighestDensityAsset()
  return getDataProvider(asset.type).getAssetSetData(assetSet)
}