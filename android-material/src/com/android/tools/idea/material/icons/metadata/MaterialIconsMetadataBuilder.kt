/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.material.icons.metadata

import java.util.SortedMap
import java.util.TreeMap

/**
 * A builder class for [MaterialIconsMetadata] that incrementally takes [MaterialMetadataIcon].
 */
class MaterialIconsMetadataBuilder(
  private val host: String,
  private val urlPattern: String,
  private val families: Array<String>
) {
  /**
   * [MaterialIconsMetadata] is expected to be sorted. Using a sorted map since the list of icons is in the thousands.
   */
  private val iconsMap: SortedMap<String, MaterialMetadataIcon> = TreeMap()

  /**
   * Add a [MaterialMetadataIcon] to the list of existing icons.
   *
   * Overwrites duplicate values.
   */
  fun addIconMetadata(iconMetadata: MaterialMetadataIcon) {
    iconsMap[iconMetadata.name] = iconMetadata
  }

  /**
   * Remove [iconMetadata] from the list of icons.
   */
  fun removeIconMetadata(iconMetadata: MaterialMetadataIcon) {
    iconsMap.remove(iconMetadata.name)
  }

  /**
   * Create a copy of [MaterialMetadataIcon] containing the current list of icons added into this instance through [addIconMetadata].
   */
  fun build(): MaterialIconsMetadata {
    return MaterialIconsMetadata(host, urlPattern, families, iconsMap.values.toTypedArray())
  }
}