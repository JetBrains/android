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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBColor
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBEditorTabsBorder
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import javax.swing.border.MatteBorder

/**
 * Tabs panel with enabled navigation.
 */
class AnimationTabs(surface: DesignSurface) : JBTabsImpl(surface.project,
                                                         IdeFocusManager.getInstance(surface.project), surface.project) {
  init {
    border = MatteBorder(0, 0, 1, 0, JBColor.border())
    ActionToolbarUtil.makeToolbarNavigable(myMoreToolbar)
  }

  override fun createTabLabel(info: TabInfo): TabLabel =
    FocusableTabLabel(this, info).apply {
      isFocusable = true
      isCreated = true
    }

  override fun createTabBorder() = JBEditorTabsBorder(this)

  inner class FocusableTabLabel(tabs: JBTabsImpl, info: TabInfo) : TabLabel(tabs, info) {
    var isCreated = false
    override fun isFocusable(): Boolean {
      // Make sure label is focusable until it's marked as created.
      return !isCreated || super.isFocusable()
    }
  }
}
