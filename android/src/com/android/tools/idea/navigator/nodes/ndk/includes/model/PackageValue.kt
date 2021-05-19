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
package com.android.tools.idea.navigator.nodes.ndk.includes.model

import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths.findCommonParentFolder
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths.trimPathSeparators

/**
 * A collection of includes that represents a single logical package that has multiple include folders.
 *
 * Visually, it will be a folder structure like this in the Android Project View:
 *
 * <pre>
 * [-] MyGame
 * [-] Includes
 * [-] CDep [A node where PackageType is CDepPackage]
 * [-] protobuf [A node represented by PackageValue]
 * protobuf.h
</pre> *
 */
data class PackageValue(
  private val key: PackageKey,
  val description : String,
  val includes: List<SimpleIncludeValue>) : ClassifiedIncludeValue() {

  // Relative path that is common to the complete set of includes.
  private val commonRelativeFolder : String
    get() =  findCommonParentFolder(includes.map { it.relativeIncludeSubFolder } )

  override fun getPackageDescription() = description

  val descriptiveText: String
    get() {
      val commonRelativeFolderWithNoSlashes = trimPathSeparators(commonRelativeFolder)
      return if (commonRelativeFolderWithNoSlashes.isEmpty()) {
        "${includes.size} include paths"
      }
      else commonRelativeFolderWithNoSlashes
    }

  val simplePackageName : String
    get() = key.simplePackageName

  override fun toString() = "${key.simplePackageName} (${key.packageType.myDescription}, $descriptiveText)"
  override fun getSortKey() = SortOrderKey.PACKAGING.myKey + toString()
  override fun getPackageType() = key.packageType
  override fun getPackageFamilyBaseFolder() = key.packagingFamilyBaseFolder
}
