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

import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

/**
 * A collection of relevant information for one (or more) related layout XML files - that is,
 * a base layout with possible alternate (e.g. landscape) configurations.
 */
class BindingLayoutGroup(layouts: List<BindingLayoutInfo>) : ModificationTracker {
  var layouts: List<BindingLayoutInfo> = layouts
    private set

  val mainLayout: BindingLayoutInfo
    // The base info is the one that has the shortest configuration name, e.g. "layout" vs "layout-w600dp"
    // Safe to assume non-null because there should always be at least one layout in a group
    get() = layouts.minBy { layout -> layout.xml.folderName.length }!!

  /**
   * Forcefully updates all the layouts of the current group (if the passed in list of layouts
   * differs from the current list).
   *
   * This will additionally update each layout's modification count, because multiple layouts
   * generate binding classes with a merged API exposing all variables across all configurations.
   */
  fun updateLayouts(layouts: List<BindingLayoutInfo>, modificationCount: Long) {
    if (this.layouts != layouts) {
      this.layouts = layouts
      for (layout in this.layouts) {
        layout.modificationCount = modificationCount
      }
    }
  }

  override fun getModificationCount(): Long {
    return layouts.sumByLong { layout -> layout.modificationCount }
  }
}
