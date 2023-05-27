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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.intellij.util.ui.EmptyIcon
import java.awt.Component
import javax.swing.Icon

class StubAssetPreviewManager(
  private val iconProvider: AssetIconProvider = StubAssetIconProvider(),
  private val dataProvider: AssetDataProvider = StubAssetDataProvider()
) : AssetPreviewManager {

  constructor(icon: Icon) : this(StubAssetIconProvider(icon))

  override fun getPreviewProvider(resourceType: ResourceType): AssetIconProvider = iconProvider

  override fun getDataProvider(resourceType: ResourceType): AssetDataProvider = dataProvider
}

class StubAssetIconProvider(var icon: Icon = EmptyIcon.ICON_18) : AssetIconProvider {
  override val supportsTransparency = false

  override fun getIcon(
    assetToRender: Asset,
    width: Int,
    height: Int,
    component: Component,
    refreshCallback: () -> Unit,
    shouldBeRendered: () -> Boolean
  ): Icon = icon
}

class StubAssetDataProvider: AssetDataProvider {
  override fun getAssetData(asset: DesignAsset): AssetData {
    return AssetData("name", "file", "size")
  }

  override fun getAssetSetData(assetSet: ResourceAssetSet): AssetData {
    return AssetData("name", "type", "versions")
  }
}