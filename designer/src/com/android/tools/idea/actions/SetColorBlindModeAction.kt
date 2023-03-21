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
package com.android.tools.idea.actions

import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.android.util.AndroidBundle.message

/**
 * Action class to switch the [ScreenViewProvider.colorBlindFilter] in a [NlDesignSurface].
 */
class SetColorBlindModeAction(
  val colorBlindMode: ColorBlindMode,
  private val designSurface: NlDesignSurface) : ToggleAction(
  colorBlindMode.displayName, message("android.layout.screenview.action.description", colorBlindMode.displayName), null) {

  var isSelected = false

  override fun isSelected(e: AnActionEvent) = isSelected

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    isSelected = state
    designSurface.setColorBlindMode(if (isSelected) colorBlindMode else ColorBlindMode.NONE)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}