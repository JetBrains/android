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
import com.android.ide.common.resources.colorToString
import com.android.tools.idea.res.resolveMultipleColors
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.resolveValue
import com.intellij.openapi.project.Project

/**
 * [AssetData] provider for Color resources.
 *
 * For [ResourceAssetSet] returns its resolved color in the [AssetData.subtitle], or "Multiple colors" for 'state list' colors.
 */
class ColorAssetDataProvider(
  private val project: Project,
  private val resourceResolver: ResourceResolver
) : DefaultAssetDataProvider() {

  override fun getAssetSetData(assetSet: ResourceAssetSet): AssetData {
    val defaultData = super.getAssetSetData(assetSet)
    val asset = assetSet.getHighestDensityAsset()
    val colors = resourceResolver.resolveMultipleColors(resourceResolver.resolveValue(asset), project).toSet()
    val subtitle = if (colors.size == 1) colorToString(colors.first()) else "Multiple colors"
    return AssetData(defaultData.title, subtitle, defaultData.metadata)
  }
}