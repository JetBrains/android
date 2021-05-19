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
package com.android.tools.idea.common.actions

import com.android.resources.NightMode
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.actions.DesignerActions.ACTION_TOGGLE_DEVICE_NIGHT_MODE
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.configurations.Configuration
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Toggle night mode between Night and Not Night in the provided [DesignSurface]
 */
class ToggleDeviceNightModeAction : AnAction() {

  override fun update(e: AnActionEvent) {
    if (isActionEventFromJTextField(e)) {
      e.presentation.isEnabled = false
      return
    }
    e.presentation.isEnabled = e.getData(DESIGN_SURFACE) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface: DesignSurface = e.getRequiredData(DESIGN_SURFACE)
    surface.configurations.forEach { configuration: Configuration ->
      val ordinal = configuration.nightMode.ordinal
      val newNightMode = NightMode.getByIndex((ordinal + 1) % NightMode.values().size)
      configuration.nightMode = newNightMode
    }
  }

  companion object {
    @JvmStatic
    val instance: ToggleDeviceNightModeAction
      get() = ActionManager.getInstance().getAction(ACTION_TOGGLE_DEVICE_NIGHT_MODE) as ToggleDeviceNightModeAction
  }
}
