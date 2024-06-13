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

import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.inspector.androidSortOrder
import com.android.tools.property.panel.api.GroupSpec
import com.android.tools.property.ptable.PTableItem

abstract class AbstractMarginGroup(
  override val name: String,
  private val all: NlPropertyItem?,
  private val left: NlPropertyItem?,
  private val right: NlPropertyItem?,
  private val start: NlPropertyItem?,
  private val end: NlPropertyItem?,
  private val top: NlPropertyItem?,
  private val bottom: NlPropertyItem?,
  private val horizontal: NlPropertyItem?,
  private val vertical: NlPropertyItem?,
) : GroupSpec<NlPropertyItem> {
  override val value: String?
    get() =
      "[${part(all)}, ${part(left, start, horizontal)}, ${part(top, vertical)}, " +
        "${part(right, end, horizontal)}, ${part(bottom, vertical)}]"

  override val itemFilter: (NlPropertyItem) -> Boolean
    get() = {
      when (it) {
        all,
        left,
        right,
        start,
        end,
        top,
        bottom,
        vertical,
        horizontal -> true
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

  private fun part(
    property: NlPropertyItem?,
    override: NlPropertyItem? = null,
    override2: NlPropertyItem? = null,
  ): String {
    return override2?.value ?: override?.value ?: property?.value ?: "?"
  }
}
