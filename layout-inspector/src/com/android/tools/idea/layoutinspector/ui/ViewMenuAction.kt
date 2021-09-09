/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.actions.DropDownAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import icons.StudioIcons
import kotlin.reflect.KMutableProperty1

object ViewMenuAction : DropDownAction(null, "View options", StudioIcons.Common.VISIBILITY_INLINE) {
  class SettingsAction(name: String, val property: KMutableProperty1<DeviceViewSettings, Boolean>) : ToggleAction(name) {
    override fun isSelected(event: AnActionEvent) =
      event.getData(DEVICE_VIEW_SETTINGS_KEY)?.let { settings -> return property.get(settings) } ?: false

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      event.getData(DEVICE_VIEW_SETTINGS_KEY)?.let { settings -> property.set(settings, state) }
    }
  }

  init {
    add(SettingsAction("Show Borders", DeviceViewSettings::drawBorders))
    add(SettingsAction("Show Layout Bounds", DeviceViewSettings::drawUntransformedBounds))
    add(SettingsAction("Show View Label", DeviceViewSettings::drawLabel))
    add(SettingsAction("Show Fold Hinge and Angle", DeviceViewSettings::drawFold))
  }

  override fun update(e: AnActionEvent) {
    val enabled = e.getData(DEVICE_VIEW_MODEL_KEY)?.isActive ?: return
    e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)?.isEnabled = enabled
  }

  override fun canBePerformed(context: DataContext) = context.getData(DEVICE_VIEW_MODEL_KEY)?.isActive == true
}