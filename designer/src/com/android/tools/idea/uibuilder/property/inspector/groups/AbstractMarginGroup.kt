/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.inspector.groups

import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.panel.api.GroupSpec
import com.android.tools.idea.uibuilder.property.NelePropertyItem
import com.android.tools.idea.uibuilder.property.inspector.androidSortOrder

abstract class AbstractMarginGroup(override val name: String,
                                   private val all: NelePropertyItem?,
                                   private val left: NelePropertyItem?,
                                   private val right: NelePropertyItem?,
                                   private val start: NelePropertyItem?,
                                   private val end: NelePropertyItem?,
                                   private val top: NelePropertyItem?,
                                   private val bottom: NelePropertyItem?,
                                   private val horizontal: NelePropertyItem?,
                                   private val vertical: NelePropertyItem?): GroupSpec<NelePropertyItem> {
  override val value: String?
    get() = "[${part(all)}, ${part(left, start, horizontal)}, ${part(top, vertical)}, " +
            "${part(right, end, horizontal)}, ${part(bottom, vertical)}]"

  override val itemFilter: (NelePropertyItem) -> Boolean
    get() = {
      when (it) {
        all, left, right, start, end, top, bottom, vertical, horizontal -> true
        else -> false
      }
    }

  override val comparator: Comparator<PTableItem>
    get() = androidSortOrder

  override fun hashCode(): Int {
    return name.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    return name == (other as? AbstractMarginGroup)?.name
  }

  private fun part(property: NelePropertyItem?, override: NelePropertyItem? = null, override2: NelePropertyItem? = null): String {
    return override2?.value ?: override?.value ?: property?.value ?: "?"
  }
}
