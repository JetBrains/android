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
package com.android.tools.idea.configurations

import com.android.resources.NightMode
import com.android.tools.adtui.actions.DropDownAction
import com.intellij.openapi.actionSystem.DataContext
import icons.StudioIcons

class NightModeMenuAction(private val renderContext: ConfigurationHolder)
  : DropDownAction("Set Night Mode", "Changes the current Night Mode value", StudioIcons.DeviceConfiguration.NIGHT_MODE) {

  override fun updateActions(context: DataContext): Boolean {
    removeAll()
    renderContext.configuration?.nightMode?.let { currentNightMode ->
      enumValues<NightMode>().forEach { mode ->
        add(SetNightModeAction(renderContext, mode.shortDisplayValue, mode, mode == currentNightMode))
      }
    }
    return true
  }
}

private class SetNightModeAction(renderContext: ConfigurationHolder, title: String, private val nightMode: NightMode, checked: Boolean)
  : ConfigurationAction(renderContext, title) {

  init {
    templatePresentation.putClientProperty(SELECTED_PROPERTY, checked)
  }

  override fun updateConfiguration(configuration: Configuration, commit: Boolean) {
      configuration.nightMode = nightMode
  }
}
