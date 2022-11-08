/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.adtui.util

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JList

class HelpTooltipForList<E> : HelpTooltip() {
  private val isTooltipEnabled = AtomicBoolean(false)
  private var currentHoveredIndex = -1

  /**
   * @param callback Is called with the current mouse over list item index. Use this index to return the list element data, and then
   *  configure the supplied [HelpTooltip]. Return `true` to enable the tooltip for the requested index, `false` when
   *  the index has no tooltip.
   */
  fun installOnList(parentDisposable: Disposable, list: JList<E>, callback: (index: Int, helpTooltip: HelpTooltip) -> Boolean) : HelpTooltipForList<E> {
    val mouseListener = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) = processMouse(e, list, callback)
      override fun mouseExited(e: MouseEvent) = processMouse(e, list, callback)
      override fun mouseMoved(e: MouseEvent) = processMouse(e, list, callback)
    }

    list.addMouseListener(mouseListener)
    list.addMouseMotionListener(mouseListener)
    installOn(list)
    setMasterPopupOpenCondition(list) { isTooltipEnabled.get() } // Needs to be called after installOn()
    Disposer.register(parentDisposable) { dispose(list) }
    return this
  }

  private fun processMouse(e: MouseEvent, list: JList<E>, callback: (index: Int, helpTooltip: HelpTooltip) -> Boolean) {
    var index = list.locationToIndex(e.point)
    if (index < 0 || list.getCellBounds(index, index)?.contains(e.point) != true) {
      index = -1 // Mouse is over the list, but not over any cell
    }

    if (index == currentHoveredIndex) {
      return
    }

    currentHoveredIndex = index
    hidePopup(false)
    isTooltipEnabled.set(index >= 0 && callback(index, this))
  }
}