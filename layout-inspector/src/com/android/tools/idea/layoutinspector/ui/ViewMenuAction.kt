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

object ViewMenuAction : DropDownAction(null, "View options", StudioIcons.Common.VISIBILITY_INLINE) {
  init {
    add(object : ToggleAction("Show Borders") {

      override fun isSelected(event: AnActionEvent): Boolean {
        return event.getData(DEVICE_VIEW_SETTINGS_KEY)?.drawBorders == true
      }

      override fun setSelected(event: AnActionEvent, state: Boolean) {
        event.getData(DEVICE_VIEW_SETTINGS_KEY)?.drawBorders = state
      }
    })
    add(object : ToggleAction("Show Layout Bounds") {
      override fun isSelected(event: AnActionEvent): Boolean {
        return event.getData(DEVICE_VIEW_SETTINGS_KEY)?.drawUntransformedBounds == true
      }

      override fun setSelected(event: AnActionEvent, state: Boolean) {
        event.getData(DEVICE_VIEW_SETTINGS_KEY)?.drawUntransformedBounds = state
      }
    })
    add(object : ToggleAction("Show View Label") {
      override fun isSelected(event: AnActionEvent): Boolean {
        return event.getData(DEVICE_VIEW_SETTINGS_KEY)?.drawLabel == true
      }

      override fun setSelected(event: AnActionEvent, state: Boolean) {
        event.getData(DEVICE_VIEW_SETTINGS_KEY)?.drawLabel = state
      }

    })
  }

  override fun update(e: AnActionEvent) {
    val enabled = e.getData(DEVICE_VIEW_MODEL_KEY)?.isActive ?: return
    e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)?.isEnabled = enabled
  }

  override fun canBePerformed(context: DataContext) = context.getData(DEVICE_VIEW_MODEL_KEY)?.isActive == true
}