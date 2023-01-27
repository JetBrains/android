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
package com.android.tools.idea.streaming.emulator.actions

import com.android.emulator.control.DisplayModeValue
import com.android.tools.idea.streaming.emulator.EMULATOR_MAIN_TOOLBAR_ID
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import icons.StudioIcons

/**
 * Displays a popup menu of display modes of a resizable AVD.
 */
internal class DisplayModeActionGroup : DefaultActionGroup() {

  override fun isDumbAware(): Boolean {
    return true
  }

  override fun canBePerformed(context: DataContext): Boolean {
    return true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val currentDisplayMode = getCurrentDisplayMode(event)
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null, this, event.dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, true, null, -1,
        { action -> action is DisplayModeAction && action.mode == currentDisplayMode },
        ActionPlaces.getPopupPlace(EMULATOR_MAIN_TOOLBAR_ID))
    val inputEvent = event.inputEvent
    if (inputEvent == null) {
      popup.showInFocusCenter()
    }
    else {
      val component = inputEvent.component
      if (component is ActionButtonComponent) {
        popup.showUnderneathOf(component)
      }
      else {
        popup.showInCenterOf(component)
      }
    }
  }

  override fun update(event: AnActionEvent) {
    val hasDisplayModes = getEmulatorConfig(event)?.displayModes?.isNotEmpty() ?: false
    val presentation = event.presentation
    presentation.isVisible = hasDisplayModes
    if (hasDisplayModes) {
      presentation.isEnabled = isEmulatorConnected(event)
      presentation.icon = when (getCurrentDisplayMode(event)) {
        DisplayModeValue.DESKTOP -> StudioIcons.Emulator.Menu.MODE_DESKTOP
        DisplayModeValue.FOLDABLE -> StudioIcons.Emulator.Menu.MODE_FOLDABLE
        DisplayModeValue.PHONE -> StudioIcons.Emulator.Menu.MODE_PHONE
        DisplayModeValue.TABLET -> StudioIcons.Emulator.Menu.MODE_TABLET
        else -> AllIcons.Toolbar.Unknown
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
