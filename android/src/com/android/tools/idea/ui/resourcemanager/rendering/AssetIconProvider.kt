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

import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.intellij.openapi.diagnostic.Logger
import java.awt.Component
import javax.swing.Icon
import javax.swing.ImageIcon

private val LOG = Logger.getInstance(AssetIconProvider::class.java)

/**
 * An [AssetIconProvider] provides an [Icon] for a [DesignAsset].
 */
interface AssetIconProvider {

  /**
   * Indicates that the implementing [AssetIconProvider] supports transparency.
   * This is used to display a chessboard background to better see the transparent
   * areas.
   */
  val supportsTransparency: Boolean

  /**
   * Returns an [Icon] representing the [assetToRender].
   * The icon should have the size defined by [width] and [height].
   * This method is meant to be called on the UI thread and should be fast.
   *
   * If it takes some time to render the icon, the rendering should be done
   * asynchronously and the resulting image should be cached.
   * (see [com.android.tools.idea.ui.resourcemanager.ImageCache]).
   *
   * If the rendering of the icon is asynchronous, the implementing method can call
   * [refreshCallback] to notify the caller that the icon has been rendered and [getIcon]
   * method can be called again to retrieve an updated version of the icon.
   *
   * The same instance of [Icon] is reused for all renders to save memory the same way
   * a [javax.swing.ListCellRenderer] reuse a view as a stamp. If an instance of [Icon] needs to
   * be kept - for instance when rendering an icon not in a JList - [AssetIcon] can be used.
   *
   * In the case of an asynchronous computation, the caller can provide an optional [shouldBeRendered]
   * function that can be called to verify that the icon should still be rendered. This can be
   * useful when deferring the rendering call: by the time the request to render the icon is made,
   * the component showing this icon might not be visible anymore so we can check [shouldBeRendered] to
   * avoid unnecessary computation.
   *
   * Example code when the [Icon] instance needs to be kept:
   * ```
   * class MyPanel {
   *     val thumbnail = JBLabel(
   *     AssetIcon(assetPreviewManager, asset, width, height))
   * }
   * ```
   *
   *  Example code with a [javax.swing.ListCellRenderer]
   * ```
   * class DesignAssetCellRenderer : ListCellRenderer<DesignAsset> {
   *
   *   val label = JLabel()
   *
   *   override fun getListCellRendererComponent(
   *   list: JList<out DesignAssetSet>,
   *   value: DesignAsset,
   *   index: Int,
   *   isSelected: Boolean,
   *   cellHasFocus: Boolean
   *   ): Component {
   *       label.icon = iconProvider.getIcon(value,
   *       width, height, list,
   *       { list.repaint(list.getCellBounds(index, index)) },
   *       { ScrollingUtil.isIndexFullyVisible(list, index) })
   *   }
   *}
   * ```
   *
   * @see AssetIcon
   */
  fun getIcon(
    assetToRender: Asset,
    width: Int,
    height: Int,
    component: Component,
    refreshCallback: () -> Unit = {},
    shouldBeRendered: () -> Boolean = { true }): Icon
}

/**
 * An [AssetIconProvider] that always returns an empty icon.
 */
class DefaultIconProvider private constructor() : AssetIconProvider {
  companion object {
    val INSTANCE = DefaultIconProvider()
  }

  var icon: Icon = ImageIcon(EMPTY_IMAGE)

  override val supportsTransparency: Boolean = false

  override fun getIcon(
    assetToRender: Asset,
    width: Int,
    height: Int,
    component: Component,
    refreshCallback: () -> Unit,
    shouldBeRendered: () -> Boolean) = icon
}
