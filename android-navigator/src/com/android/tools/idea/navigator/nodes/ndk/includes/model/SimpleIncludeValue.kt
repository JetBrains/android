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

import java.io.File

/**
 * Information about a single include path.
 */
data class SimpleIncludeValue(
   private val packageType : PackageType,
   private val packageDescription : String,
   val simplePackageName : String,
   val relativeIncludeSubFolder : String,
   val includeFolder : File,
   private val packageFamilyBaseFolder : File
) : ClassifiedIncludeValue() {
  override fun getSortKey() = SortOrderKey.SIMPLE_INCLUDE.myKey + toString()
  override fun getPackageType() = packageType
  override fun getPackageFamilyBaseFolder() = packageFamilyBaseFolder
  override fun getPackageDescription() = packageDescription
  override fun toString() = "$simplePackageName ($packageDescription, $packageFamilyBaseFolder, $relativeIncludeSubFolder)"
}