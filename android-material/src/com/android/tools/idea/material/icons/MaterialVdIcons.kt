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
package com.android.tools.idea.material.icons

import com.android.ide.common.vectordrawable.VdIcon

/**
 * The model for the Material [VdIcon]s loaded.
 */
class MaterialVdIcons(
  private val styleCategoryToSortedIcons: Map<String, Map<String, Array<VdIcon>>>,
  private val styleToSortedIcons: Map<String, Array<VdIcon>>
) {

  val styles: Array<String> = styleCategoryToSortedIcons.keys.sorted().toTypedArray()

  fun getCategories(style: String): Array<String> {
    return styleCategoryToSortedIcons[style]?.keys?.sorted()?.toTypedArray() ?: arrayOf<String>()
  }

  fun getIcons(style: String, category: String): Array<VdIcon> {
    return styleCategoryToSortedIcons[style]?.get(category) ?: arrayOf<VdIcon>()
  }

  fun getAllIcons(style: String): Array<VdIcon> {
    return styleToSortedIcons[style] ?: arrayOf()
  }

  companion object {
    /**
     * The default empty instance. Returns empty arrays for every method.
     */
    @JvmField
    val EMPTY = MaterialVdIcons(emptyMap(), emptyMap())
  }
}