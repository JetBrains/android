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
package com.android.tools.idea.material.icons

/**
 * A builder class for [MaterialIconsMetadata] that incrementally takes [MaterialMetadataIcon].
 */
class MaterialIconsMetadataBuilder(
  private val host: String,
  private val urlPattern: String,
  private val families: Array<String>
) {

  private val icons: ArrayList<MaterialMetadataIcon> = ArrayList(1200) // There are around 1057 icons available.

  /**
   * Add a [MaterialMetadataIcon] to the list of existing icons.
   *
   * Note that it doesn't check for duplicates.
   */
  fun addIconMetadata(iconMetadata: MaterialMetadataIcon) {
    icons.add(iconMetadata)
  }

  /**
   * Create a copy of [MaterialMetadataIcon] containing the current list of icons added into this instance through [addIconMetadata].
   */
  fun build(): MaterialIconsMetadata {
    return MaterialIconsMetadata(host, urlPattern, families, icons.toTypedArray())
  }
}