/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.actions

import com.android.tools.idea.streaming.core.FloatingToolbarContainer
import com.android.tools.idea.streaming.xr.XrInputMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.ui.popup.JBPopupFactory
import icons.StudioIcons

/** Displays a popup menu of XR input modes. */
internal class StreamingXrInputModePopupGroup : DefaultActionGroup(), Toggleable {

  init {
    templatePresentation.isPerformGroup = true
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val controller = getXrInputController(event)
    if (controller?.isXrInputAvailable != true || !isHandAndEyeTrackingEnabled(event)) {
      presentation.isEnabledAndVisible = false
      return
    }

    // We only want to show as a group IF the floating toolbar is active (expanded). The reason for this is that
    // when collapsed, the first click will simply expand the floating toolbar first.
    val container = FloatingToolbarContainer.fromActionEvent(event)
    presentation.isPopupGroup = childrenCount > 1 && (container == null || !container.collapsible || container.isActive)

    presentation.icon =
      when (controller.lastAppInteractionMode) {
        XrInputMode.HAND -> StudioIcons.Emulator.XR.HAND_TRACKING
        XrInputMode.EYE -> StudioIcons.Emulator.XR.EYE_GAZE
        else -> StudioIcons.Emulator.Toolbar.HARDWARE_INPUT
      }

    Toggleable.setSelected(presentation, !controller.inputMode.isNavigation)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val multipleChildren = childrenCount > 1
    val toolbarContainer = FloatingToolbarContainer.fromActionEvent(event)
    // If the group has multiple children and the floating toolbar is collapsible but not active, in this click, activate.
    if (multipleChildren && toolbarContainer?.collapsible == true && !toolbarContainer.isActive) {
      FloatingToolbarContainer.triggerActivation(event)
      return
    }

    val controller = getXrInputController(event) ?: return
    val previousInputMode = controller.inputMode
    controller.apply { inputMode = lastAppInteractionMode }

    if (!previousInputMode.isNavigation) {
      // Manually show the popup since isPerformGroup = true might bypass the default toolbar behavior
      val popup =
        JBPopupFactory.getInstance()
          .createActionGroupPopup(
            null,
            this,
            event.dataContext,
            JBPopupFactory.ActionSelectionAid.MNEMONICS,
            true,
            null,
            -1,
            null,
            event.place,
          )
      event.inputEvent?.component?.let { popup.showUnderneathOf(it) } ?: popup.showInFocusCenter()
    }
  }
}
