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

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.tools.idea.ui.resourcemanager.importer.QualifierMatcher
import com.intellij.openapi.vfs.VirtualFile

/**
 * Represents a set of resource assets grouped by base name.
 *
 * For example, fr/icon@2x.png, fr/icon.jpg  and en/icon.png will be
 * gathered in the same DesignAssetSet under the name "icon"
 */
data class ResourceAssetSet(
  val name: String,
  var assets: List<Asset>
) {

  /**
   * Return the asset in this set with the highest density
   */
  fun getHighestDensityAsset(): Asset {
    return designAssets.maxBy { asset ->
      asset.qualifiers
        .filterIsInstance<DensityQualifier>()
        .map { densityQualifier -> densityQualifier.value.dpiValue }
        .singleOrNull() ?: 0
    } ?: assets[0]
  }
}


/**
 * Find all the [ResourceAssetSet] in the given directory
 *
 * @param supportedTypes The file types supported for importation
 */
fun getAssetSets(
  directory: VirtualFile,
  supportedTypes: Set<String>,
  qualifierMatcher: QualifierMatcher
): List<ResourceAssetSet> {
  return getDesignAssets(directory, supportedTypes, directory, qualifierMatcher)
    .groupBy { designAsset -> designAsset.name }
    .map { (drawableName, designAssets) -> ResourceAssetSet(drawableName, designAssets) }
    .toList()
}