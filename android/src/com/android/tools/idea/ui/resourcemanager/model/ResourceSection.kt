/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.model

import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType

data class ResourceSection(
  val libraryName: String = "",
  val assetSets: List<ResourceAssetSet>
)

fun createResourceSection(libraryName: String, resourceItems: List<ResourceItem>): ResourceSection {
  val designAssets = resourceItems
    .map { Asset.fromResourceItem(it) }
    .groupBy(Asset::name)
    // TODO: Add an 'indexToPreview' or 'assetToPreview' value for ResourceAssetSet, instead of previewing the first asset by default.
    .map { (name, assets) -> ResourceAssetSet(name, assets) }
  return ResourceSection(libraryName, designAssets)
}

/** Creates a [ResourceSection] forcing the displayed [ResourceType]. E.g: Attributes resources may represent other type of resources. */
fun createResourceSection(libraryName: String, resourceItems: List<ResourceItem>, resourceType: ResourceType): ResourceSection {
  val designAssets = resourceItems
    .map { Asset.fromResourceItem(it, resourceType) }
    .groupBy(Asset::name)
    .map { (name, assets) -> ResourceAssetSet(name, assets) }
  return ResourceSection(libraryName, designAssets)
}