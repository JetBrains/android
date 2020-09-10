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
package com.android.tools.idea.emulator.actions

import com.android.tools.idea.emulator.EMULATOR_MAIN_TOOLBAR_ID
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory

class BootOptionsPopupActionGroup : DefaultActionGroup() {

  override fun isDumbAware(): Boolean {
    return true
  }

  override fun canBePerformed(context: DataContext): Boolean {
    return true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      null, this, event.dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, true, EMULATOR_MAIN_TOOLBAR_ID)
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
    event.presentation.isVisible = !StudioFlags.EMBEDDED_EMULATOR_NEW_SNAPSHOT_UI.get()
    event.presentation.isEnabled = isEmulatorConnected(event)
  }
}