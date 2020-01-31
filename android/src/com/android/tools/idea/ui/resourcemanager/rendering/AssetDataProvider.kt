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
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet

private const val VERSION = "version"

/**
 * An [AssetDataProvider] provides an [AssetData] object that holds some basic information to be displayed for either [ResourceAssetSet] or
 * [DesignAsset].
 */
interface AssetDataProvider {

  /** Returns an [AssetData] for an specific [DesignAsset]. */
  fun getAssetData(asset: DesignAsset): AssetData

  /** Returns an [AssetData] for an specific [ResourceAssetSet]. I.e: information about a group of [ResourceAssetSet.assets]. */
  fun getAssetSetData(assetSet: ResourceAssetSet): AssetData

}

/**
 * Provides [AssetData] with the default (most common) information.
 * For [DesignAsset] returns it's concatenated qualifiers, the name of the resource and it's size, if available.
 * For [ResourceAssetSet] returns the resource's name, the type of resource (Eg: Drawable, Layout, Font) and the number of versions
 * available (qualifiers supported for this resource).
 */
open class DefaultAssetDataProvider: AssetDataProvider {

  override fun getAssetData(asset: DesignAsset): AssetData {
    val title = asset.qualifiers.getReadableConfigurations()
    val subtitle = asset.file.name
    val metadata = asset.getDisplayableFileSize()
    return AssetData(title, subtitle, metadata)
  }

  override fun getAssetSetData(assetSet: ResourceAssetSet): AssetData {
    val asset = assetSet.getHighestDensityAsset()
    val title = assetSet.name
    val subtitle = asset.type.displayName
    val metadata = assetSet.versionCountString()
    return AssetData(title, subtitle, metadata)
  }
}

/**
 * Data class to store the information to display in [com.android.tools.idea.ui.resourcemanager.widget.AssetView]
 */
data class AssetData(
  val title: String,
  val subtitle: String,
  val metadata: String
)

private fun String.pluralize(size: Int) = this + (if (size > 1) "s" else "")

private fun ResourceAssetSet.versionCountString(): String {
  val size = assets.size
  return "$size $VERSION".pluralize(size)
}