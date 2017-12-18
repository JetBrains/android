/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.importer

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.ide.common.resources.configuration.ResourceQualifier

import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ResourceFolderType
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.intellij.openapi.vfs.VirtualFile

/**
 * Find all the [DesignAssetSet] in the given directory
 *
 * @param supportedTypes The file types supported for importation
 */
fun getAssetSets(directory: VirtualFile, supportedTypes: Set<String>): List<DesignAssetSet> {
  return getDesignAssets(directory, supportedTypes)
      .groupBy(
          { (drawableName, _) -> drawableName },
          { (_, designAsset) -> designAsset }
      )
      .map { (drawableName, designAssets) -> DesignAssetSet(drawableName, designAssets) }
      .toList()
}

private fun getDesignAssets(
    child: VirtualFile, supportedTypes: Set<String>): List<Pair<String, DesignAsset>> {
  return child.children
      .filter { it.isDirectory || supportedTypes.contains(it.extension) }
      .flatMap {
        if (it.isDirectory) getDesignAssets(it, supportedTypes) else listOf(createAsset(it))
      }
}

private fun createAsset(child: VirtualFile): Pair<String, DesignAsset> {
  val drawableName = getBaseName(child)
  val qualifiers = getQualifiers(child)
  return drawableName.to(DesignAsset(child, qualifiers, ResourceFolderType.DRAWABLE))
}

/**
 * For now, we simply parse the name looking for the icon@2x.png format
 * and return "icon". Later we'll implement a lexer to parse the full path
 * and looking for more format like icon_16x16@2x.png
 *
 * from [the developer documentation](https://developer.android.com/guide/practices/screens_support.html#alternative_drawables)
 */
private fun getQualifiers(file: VirtualFile): List<ResourceQualifier> {
  // This implementation is for test only for now. It will be changed to a
  // more complex parsing using user configuration
  val textQualifiers = file.nameWithoutExtension.split("_", "@")
  val dimension = textQualifiers.getOrNull(1)
  val nightMode = textQualifiers.contains("dark")
  val density = when (dimension) {
    "2x" -> Density.XHIGH
    "3x" -> Density.XXHIGH
    "4x" -> Density.XXXHIGH
    else -> Density.MEDIUM
  }
  val qualifiersList = mutableListOf<ResourceQualifier>(DensityQualifier(density))

  if (nightMode) {
    qualifiersList += NightModeQualifier(NightMode.NIGHT)
  }
  return qualifiersList.toList()
}

/**
 * Parse the file name and return just the base name
 */
fun getBaseName(file: VirtualFile) = file.nameWithoutExtension.split("@","_")[0]
