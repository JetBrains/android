/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

/** A Dropdown action that contains a checkbox of the color-blind modes to be applied in a layout */
class ColorBlindModeAction : DropDownAction("Color Blind Modes", null, null) {

  init {
    ColorBlindMode.values().forEach { addAction(SetColorBlindModeAction(it)) }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = e.getData(DESIGN_SURFACE) is NlDesignSurface
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
