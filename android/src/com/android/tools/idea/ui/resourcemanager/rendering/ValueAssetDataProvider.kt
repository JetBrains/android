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

import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.resolveValue
import com.intellij.openapi.util.text.StringUtil

/**
 * [AssetData] provider for most value-like resources (E.g: Dimensions, Strings). Which usually do not have an obvious/useful Icon
 * representation.
 *
 * Will try to return the resolved value of the resource in [AssetData.metadata] for [DesignAsset]s, and in [AssetData.subtitle] for
 * [ResourceAssetSet].
 */
class ValueAssetDataProvider (
  private val resourceResolver: ResourceResolver
): DefaultAssetDataProvider() {

  override fun getAssetData(asset: DesignAsset): AssetData {
    val defaultData = super.getAssetData(asset)
    val metadata = resourceResolver.resolveValue(asset)?.getReadableValue()?.truncate()?: defaultData.subtitle
    return AssetData(defaultData.title, defaultData.subtitle, metadata)
  }

  override fun getAssetSetData(assetSet: ResourceAssetSet): AssetData {
    val defaultData = super.getAssetSetData(assetSet)
    val asset = assetSet.getHighestDensityAsset()
    val subtitle = resourceResolver.resolveValue(asset)?.getReadableValue()?: defaultData.subtitle
    return AssetData(defaultData.title, subtitle, defaultData.metadata)
  }
}

private fun String.truncate() = StringUtil.first(this, 17, true)