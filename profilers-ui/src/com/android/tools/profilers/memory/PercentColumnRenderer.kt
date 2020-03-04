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
package com.android.tools.profilers.memory

import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.memory.adapters.MemoryObject
import java.awt.Graphics
import java.util.function.Function
import javax.swing.Icon
import javax.swing.JTree

internal class PercentColumnRenderer<T: MemoryObject>(
  textGetter: Function<MemoryObjectTreeNode<T>, String>,
  iconGetter: Function<MemoryObjectTreeNode<T>, Icon>,
  alignment: Int,
  private val percentGetter: Function<MemoryObjectTreeNode<T>, Int>
): SimpleColumnRenderer<T>(textGetter, iconGetter, alignment) {

  private var percent = 0

  override fun paintComponent(g: Graphics) {
    if (percent > 0) {
      g.color = if (mySelected) ProfilerColors.CAPTURE_SPARKLINE_SELECTED else ProfilerColors.CAPTURE_SPARKLINE
      val barWidth = width * percent / 100
      g.fillRect(width - barWidth, 0, barWidth, height)
      g.color = if (mySelected) ProfilerColors.CAPTURE_SPARKLINE_SELECTED_ACCENT else ProfilerColors.CAPTURE_SPARKLINE_ACCENT
      g.fillRect(width - barWidth, height - ACCENT_BAR_WIDTH, barWidth, ACCENT_BAR_WIDTH)
    }
    // The percent bar is supposed to  be background, so the call to super should be last
    super.paintComponent(g)
  }

  override fun customizeCellRenderer(tree: JTree,
                                     value: Any?,
                                     selected: Boolean,
                                     expanded: Boolean,
                                     leaf: Boolean,
                                     row: Int,
                                     hasFocus: Boolean) {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    if (value is MemoryObjectTreeNode<*>) {
      percent = percentGetter.apply(value as MemoryObjectTreeNode<T>)
      require (percent >= 0)
    }
  }

  private companion object {
    const val ACCENT_BAR_WIDTH = 2
  }
}