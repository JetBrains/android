/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.configurations.ConfigurationListener
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

private const val TEXT_EDGE = "Edge-to-edge"
private const val DESCRIPTION_EDGE = "Edge-to-edge setting"
private const val TEXT_GESTURE = "Gesture nav"
private const val DESCRIPTION_GESTURE = "Gesture nav setting"

/** A Dropdown action that contains the system UI options to be applied in a layout */
class SystemUiOptionsAction(refresh: (e: AnActionEvent) -> Unit = {}) :
  DropDownAction("System UI options", null, null) {

  init {
    addAction(EdgeToEdgeAction(refresh))
    addAction(GestureNavAction(refresh))
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/** Action that sets whether the layout is edge-to-edge */
private class EdgeToEdgeAction(val refresh: (e: AnActionEvent) -> Unit = {}) :
  ToggleAction(TEXT_EDGE, DESCRIPTION_EDGE, null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun displayTextInToolbar(): Boolean = true

  override fun isSelected(e: AnActionEvent) =
    e.getData(CONFIGURATIONS)?.firstOrNull()?.isEdgeToEdge ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getData(CONFIGURATIONS)?.forEach {
      it.isEdgeToEdge = state
      it.updated(ConfigurationListener.CFG_DEVICE_STATE)
    }
    refresh(e)
  }
}

/** Action that sets whether the layout is using the gesture style of navigation bar */
private class GestureNavAction(val refresh: (e: AnActionEvent) -> Unit = {}) :
  ToggleAction(TEXT_GESTURE, DESCRIPTION_GESTURE, null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun displayTextInToolbar(): Boolean = true

  override fun isSelected(e: AnActionEvent) =
    e.getData(CONFIGURATIONS)?.firstOrNull()?.isGestureNav ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getData(CONFIGURATIONS)?.forEach {
      it.isGestureNav = state
      it.updated(ConfigurationListener.CFG_DEVICE_STATE)
    }
    refresh(e)
  }
}
