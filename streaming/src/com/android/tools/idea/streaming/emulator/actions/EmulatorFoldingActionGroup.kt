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
package com.android.tools.idea.streaming.emulator.actions

import com.android.tools.idea.streaming.core.findComponentForAction
import com.android.tools.idea.streaming.emulator.EMULATOR_MAIN_TOOLBAR_ID
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory

/**
 * Displays a popup menu of available postures of a foldable or a rollable device.
 */
internal class EmulatorFoldingActionGroup : DefaultActionGroup(), DumbAware {

  init {
    templatePresentation.isPerformGroup = true
    templatePresentation.text = "Fold/Unfold"
  }

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorView = getEmulatorView(event) ?: return
    val currentPosture = emulatorView.currentPosture
    if (currentPosture == null) {
      ActionManager.getInstance().getAction(EmulatorShowVirtualSensorsAction.ID).actionPerformed(event)
    }
    else {
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null, this, event.dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, true, null, -1,
        { action -> action is EmulatorFoldingAction && action.posture == currentPosture },
        ActionPlaces.getPopupPlace(EMULATOR_MAIN_TOOLBAR_ID)
      )
      event.findComponentForAction(this)?.let { popup.showUnderneathOf(it) } ?: popup.showInFocusCenter()
    }
  }

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    if (event == null) {
      return emptyArray()
    }
    val emulatorView = getEmulatorView(event) ?: return emptyArray()
    val postures = emulatorView.emulator.emulatorConfig.postures
    if (postures.isEmpty() || emulatorView.displayMode?.hasPostures == false) {
      return emptyArray()
    }
    val children = mutableListOf<AnAction>()
    if (emulatorView.currentPosture != null) {
      for (posture in postures) {
        children.add(EmulatorFoldingAction(posture))
      }
      if (children.isNotEmpty()) {
        children.add(Separator.getInstance())
      }
    }
    children.add(ActionManager.getInstance().getAction(EmulatorShowVirtualSensorsAction.ID))
    return children.toTypedArray()
  }

  override fun update(event: AnActionEvent) {
    val emulatorView = getEmulatorView(event)
    val enabled = getEmulatorConfig(event)?.postures?.isNotEmpty() == true &&
                  emulatorView != null && emulatorView.displayMode?.hasPostures != false
    val presentation = event.presentation
    presentation.isEnabledAndVisible = enabled
    if (enabled) {
      emulatorView?.currentPosture?.let { posture ->
        presentation.icon = posture.icon
        presentation.text = "${templatePresentation.text} (currently ${posture.displayName})"
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
