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
package com.android.tools.adtui.toolwindow.splittingtabs

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.ui.Splitter
import javax.swing.Icon

/**
 * Encapsulates the presentation and behavior of a split action.
 *
 * Note that the splitter related icons and texts in AllIcons & ActionsBundle use an orientation that is reversed from that used by
 * [Splitter].
 *
 * The text and icon ascribe the orientation to the separator while the [Splitter] definition ascribe it to the panels.
 *
 * For example, the text (Split Right) and icon ([AllIcons.Actions.SplitVertically]) for a vertical split describe a vertical splitter
 * divider with 2 panels side by side while the [Splitter] definition describes a vertical split as 2 panels one above the other separated
 * by a horizontal line.
 *
 * Thus, when converting SplitOrientation to and from [Splitter#isVertical], they need to be reversed.
 */
internal enum class SplitOrientation(val text: String, val icon: Icon, private val isVertical: Boolean) {
  VERTICAL(ActionsBundle.message("action.SplitVertically.text"), AllIcons.Actions.SplitVertically, isVertical = true),
  HORIZONTAL(ActionsBundle.message("action.SplitHorizontally.text"), AllIcons.Actions.SplitHorizontally, isVertical = false);

  fun toSplitter(): Boolean = !isVertical

  companion object {
    fun fromSplitter(splitter: Splitter) = if (splitter.isVertical) HORIZONTAL else VERTICAL
  }
}