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

import com.android.tools.idea.common.model.ChangeType
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.android.util.AndroidBundle.message

/** Action class to switch the overall color blind mode in a [NlDesignSurface]. */
class SetColorBlindModeAction(val colorBlindMode: ColorBlindMode) :
  ToggleAction(
    colorBlindMode.displayName,
    message("android.layout.screenview.action.description", colorBlindMode.displayName),
    null,
  ) {

  override fun isSelected(e: AnActionEvent): Boolean {
    return (e.getData(DESIGN_SURFACE) as? NlDesignSurface)?.colorBlindMode == colorBlindMode
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val surface = e.getData(DESIGN_SURFACE) as? NlDesignSurface ?: return
    surface.colorBlindMode = if (state) colorBlindMode else ColorBlindMode.NONE
    for (manager in surface.sceneManagers) {
      manager.model.configuration.imageTransformation = surface.colorBlindMode.imageTransform
      manager.model.notifyModified(ChangeType.CONFIGURATION_CHANGE)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
