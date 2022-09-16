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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
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
      .getIcon(asset, width, height, c, { c.repaint() }) { c.isShowing }
    val cWidth = c.width
    val cHeight = c.height
    icon.paintIcon(c, g, (cWidth - icon.iconWidth) / 2, (cHeight - icon.iconHeight) / 2)
  }
}