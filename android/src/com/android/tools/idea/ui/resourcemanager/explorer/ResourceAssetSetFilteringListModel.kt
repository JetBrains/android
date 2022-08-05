/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.intellij.openapi.util.Condition
import com.intellij.ui.CollectionListModel
import com.intellij.ui.speedSearch.FilteringListModel

/**
 * [FilteringListModel] for [ResourceAssetSet] matching name and [ResourceType.STRING] values.
 */
class ResourceAssetSetFilteringListModel(
  collectionListModel: CollectionListModel<ResourceAssetSet>,
  private val filter: Condition<String>
) : FilteringListModel<ResourceAssetSet>(collectionListModel) {
  init {
    setFilter(::isMatch)
  }

  private fun isMatch(assetSet: ResourceAssetSet): Boolean {
    if (filter.value(assetSet.name)) {
      return true
    }
    for (asset in assetSet.assets) {
      if (asset.type == ResourceType.STRING) {
        val value = asset.resourceItem.resourceValue?.value
        if (value?.let { filter.value(it) } == true) {
          return true
        }
      }
    }
    return false
  }
}
