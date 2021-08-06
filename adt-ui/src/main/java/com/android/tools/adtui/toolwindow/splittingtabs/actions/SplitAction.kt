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

import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation
import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.HORIZONTAL
import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.VERTICAL
import com.android.tools.adtui.toolwindow.splittingtabs.findFirstSplitter
import com.intellij.ui.content.Content

@Suppress("ComponentNotRegistered")
internal sealed class SplitAction(private val orientation: SplitOrientation)
  : SplittingTabsContextMenuAction(orientation.text) {

  init {
    templatePresentation.icon = orientation.icon
  }

  override fun actionPerformed(content: Content) {
    content.findFirstSplitter()?.split(orientation)
  }

  class Vertical : SplitAction(VERTICAL)

  class Horizontal : SplitAction(HORIZONTAL)
}