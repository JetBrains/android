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
package com.android.tools.adtui.toolwindow.splittingtabs.actions

import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsBundle
import com.android.tools.adtui.toolwindow.splittingtabs.actions.MoveTabAction.Direction.LEFT
import com.android.tools.adtui.toolwindow.splittingtabs.actions.MoveTabAction.Direction.RIGHT
import com.android.tools.adtui.toolwindow.splittingtabs.getPosition
import com.intellij.ui.content.Content

/**
 * Base class for [Left] & [Right] actions below.
 */
@Suppress("ComponentNotRegistered")
internal abstract class MoveTabAction private constructor(private val direction: Direction)
  : SplittingTabsContextMenuAction(direction.text) {

  private enum class Direction(val text: String, val offset: Int) {
    LEFT(SplittingTabsBundle.message("SplittingTabsToolWindow.moveTabLeft"), -1) {
      override fun isAvailable(content: Content) = content.getPosition() > 0
    },
    RIGHT(SplittingTabsBundle.message("SplittingTabsToolWindow.moveTabRight"), 1) {
      override fun isAvailable(content: Content): Boolean {
        val position = content.getPosition()
        return position >= 0 && position < (content.manager?.contentCount ?: 0) - 1
      }
    };

    abstract fun isAvailable(content: Content): Boolean
  }

  override fun isEnabled(content: Content) = direction.isAvailable(content)

  override fun actionPerformed(content: Content) {
    if (!direction.isAvailable(content)) {
      return
    }
    val contentManager = content.manager!!
    val thisIndex = contentManager.getIndexOfContent(content)
    val otherIndex = thisIndex + direction.offset
    val otherContent = contentManager.getContent(otherIndex) ?: return
    contentManager.removeContent(otherContent, false, false, false).doWhenDone {
      contentManager.addContent(otherContent, thisIndex)
    }
  }

  /**
   * Move a tab left
   */
  class Left : MoveTabAction(LEFT)

  /**
   * Move a tab right
   */
  class Right : MoveTabAction(RIGHT)
}
