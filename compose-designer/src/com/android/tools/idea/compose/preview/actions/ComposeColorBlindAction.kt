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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.actions.ColorBlindScreenViewProvider
import com.android.tools.idea.actions.SetColorBlindModeAction
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

/** [DropDownAction] to allow setting different color-blind modes to Compose Previews. */
class ComposeColorBlindAction(private val surface: NlDesignSurface) :
  DropDownAction(message("action.scene.mode.colorblind.dropdown.title"), null, null) {

  init {
    ColorBlindMode.values().forEach { addAction(SetColorBlindModeAction(it, surface)) }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    getChildren(e).forEach { action ->
      val screenViewProvider = surface.screenViewProvider
      action as SetColorBlindModeAction
      if (screenViewProvider is ColorBlindScreenViewProvider) {
        action.isSelected = screenViewProvider.colorBlindMode == action.colorBlindMode
      } else {
        // Provider is ComposeScreenViewProvider. Mode is Original.
        action.isSelected = action.colorBlindMode == ColorBlindMode.NONE
      }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
