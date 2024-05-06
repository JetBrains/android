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
package com.android.tools.idea.streaming.device.actions

import com.android.tools.idea.streaming.core.findComponentForAction
import com.android.tools.idea.streaming.device.DEVICE_MAIN_TOOLBAR_ID
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory

/**
 * Displays a popup menu of available postures of a foldable device.
 */
internal class DeviceFoldingActionGroup : DefaultActionGroup(), DumbAware {

  init {
    templatePresentation.isPerformGroup = true
    templatePresentation.text = "Fold/Unfold"
  }

  override fun actionPerformed(event: AnActionEvent) {
    val controller = getDeviceController(event) ?: return
    val currentFoldingState = controller.currentFoldingState
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      null, this, event.dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, true, null, -1,
      { action -> action is DeviceFoldingAction && action.foldingState.id == currentFoldingState?.id },
      ActionPlaces.getPopupPlace(DEVICE_MAIN_TOOLBAR_ID))
    event.findComponentForAction(this)?.let { popup.showUnderneathOf(it) } ?: popup.showInFocusCenter()
  }

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    if (event == null) {
      return emptyArray()
    }
    val controller = getDeviceController(event) ?: return emptyArray()
    return controller.supportedFoldingStates.map { DeviceFoldingAction(it) }.toTypedArray()
  }

  override fun update(event: AnActionEvent) {
    val controller = getDeviceController(event)
    val presentation = event.presentation
    presentation.isEnabledAndVisible = controller?.supportedFoldingStates?.isNotEmpty() ?: false
    val currentFoldingState = controller?.currentFoldingState
    presentation.text = "${templatePresentation.text} (currently ${currentFoldingState?.name ?: "unknown state"})"
    currentFoldingState?.icon.let { presentation.icon = it }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
