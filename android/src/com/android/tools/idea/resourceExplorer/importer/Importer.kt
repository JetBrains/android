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

import com.android.resources.ResourceFolderType
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Find all the [DesignAssetSet] in the given directory
 *
 * @param supportedTypes The file types supported for importation
 */
fun getAssetSets(directory: VirtualFile,
                 supportedTypes: Set<String>,
                 qualifierMatcher: QualifierMatcher
): List<DesignAssetSet> {
  return getDesignAssets(directory, supportedTypes, directory, qualifierMatcher)
      .groupBy(
          { (drawableName, _) -> drawableName },
          { (_, designAsset) -> designAsset }
      )
      .map { (drawableName, designAssets) -> DesignAssetSet(drawableName, designAssets) }
      .toList()
}

private fun getDesignAssets(
    directory: VirtualFile,
    supportedTypes: Set<String>,
    root: VirtualFile,
    qualifierMatcher: QualifierMatcher
): List<Pair<String, DesignAsset>> {
  return directory.children
      .filter { it.isDirectory || supportedTypes.contains(it.extension) }
      .flatMap {
        if (it.isDirectory) getDesignAssets(it, supportedTypes, root, qualifierMatcher)
        else listOf(createAsset(it, root, qualifierMatcher))
      }
}

private fun createAsset(child: VirtualFile, root: VirtualFile, matcher: QualifierMatcher): Pair<String, DesignAsset> {
  val relativePath = VfsUtil.getRelativePath(child, root) ?: child.path
  val (resourceName, qualifiers1) = matcher.parsePath(relativePath)
  return resourceName.to(DesignAsset(child, qualifiers1.toList(), ResourceFolderType.DRAWABLE))
}