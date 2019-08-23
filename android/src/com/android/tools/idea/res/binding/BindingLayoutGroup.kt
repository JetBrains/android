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
package com.android.tools.idea.res.binding

import com.google.common.collect.ImmutableList
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

/**
 * A collection of relevant information for one (or more) related layout XML files - that is,
 * a base layout with possible alternate (e.g. landscape) configurations.
 */
class BindingLayoutGroup(layouts: Collection<BindingLayoutInfo>) : ModificationTracker {
  var layouts: List<BindingLayoutInfo> = ImmutableList.copyOf(layouts)
    private set

  val mainLayout: BindingLayoutInfo
    // Safe to assume non-null because there should always be at least one layout in a group.
    get() = layouts.firstOrNull { layout -> layout.data.folderConfiguration.isDefault() } ?: layouts.first()

  /**
   * Forcefully updates all the layouts of the current group (if the passed in layouts differ from
   * the current list).
   */
  fun updateLayouts(layouts: Collection<BindingLayoutInfo>) {
    if (!isSameContent(this.layouts, layouts)) {
      this.layouts = ImmutableList.copyOf(layouts)
    }
  }

  override fun getModificationCount(): Long {
    return layouts.sumByLong { layout -> layout.modificationCount }
  }
}

private fun isSameContent(col1: Collection<Any>, col2: Collection<Any>): Boolean {
  if (col1.size != col2.size) {
    return false;
  }
  val col2Iter = col2.iterator();
  for (obj in col1) {
    if (obj != col2Iter.next()) {
      return false;
    }
  }
  return true;
}
