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
package com.android.tools.idea.resourceExplorer.rendering

import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.view.EMPTY_ICON
import com.intellij.openapi.diagnostic.Logger
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
   *
   * If the rendering of the icon is asynchronous, the implementing method can call
   * [refreshCallback] to notify the caller that the icon has been rendered and [getIcon]
   * method can be called again to retrieve an updated version of the icon.
   *
   * In the case of an asynchronous computation, the caller can provide an optional [shouldBeRendered]
   * function that can be called to verify that the icon should still be rendered. This can be
   * useful when deferring the rendering call: by the time the request to render the icon is made,
   * the component showing this icon might not be visible anymore so we can check [shouldBeRendered] to
   * avoid unnecessary computation.
   */
  fun getIcon(
    assetToRender: DesignAsset,
    width: Int,
    height: Int,
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

  var icon: Icon = ImageIcon(EMPTY_ICON)

  override val supportsTransparency: Boolean = false

  override fun getIcon(
    assetToRender: DesignAsset,
    width: Int,
    height: Int,
    refreshCallback: () -> Unit,
    shouldBeRendered: () -> Boolean) = icon
}
