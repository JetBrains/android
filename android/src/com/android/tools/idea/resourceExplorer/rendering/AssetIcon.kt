package com.android.tools.idea.resourceExplorer.rendering

import com.android.tools.idea.resourceExplorer.model.DesignAsset
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

/**
 * An [Icon] to asynchronously render the [asset].
 *
 * The [AssetIcon] delegates the painting to the icon
 * fetched using the [assetPreviewManager] without keeping it in memory.
 */
class AssetIcon(
  val assetPreviewManager: AssetPreviewManager,
  val asset: DesignAsset,
  var width: Int,
  var height: Int
) : Icon {

  override fun getIconHeight(): Int = height

  override fun getIconWidth(): Int = width

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    val icon = assetPreviewManager
      .getPreviewProvider(asset.type)
      .getIcon(asset, width, height, { c.repaint() }) { c.isShowing }
    val cWidth = c.width
    val cHeight = c.height
    icon.paintIcon(c, g, (cWidth - icon.iconWidth) / 2, (cHeight - icon.iconHeight) / 2)
  }
}