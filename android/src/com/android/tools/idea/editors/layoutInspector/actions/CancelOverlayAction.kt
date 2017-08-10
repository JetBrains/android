/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.layoutInspector.actions

import com.android.tools.idea.editors.layoutInspector.ui.ViewNodeActiveDisplay
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import icons.StudioIcons
import javax.swing.JComponent

/**
 * Only visible if there is an overlay. Cancels the current overlay when clicked.
 */
class CancelOverlayAction(private val myPreview: ViewNodeActiveDisplay) :
    AnAction(ACTION_ID, "Cancel Current Overlay", StudioIcons.Common.CLEAR), CustomComponentAction {
  companion object {
    @JvmField
    val ACTION_ID = "Cancel Overlay"
  }

  override fun createCustomComponent(presentation: Presentation?): JComponent {
    return ActionButtonWithText(this, presentation, "Toolbar", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }
  override fun update(e: AnActionEvent?) {
    super.update(e)
    if (e == null) return
    e.presentation.isVisible = myPreview.hasOverlay()
  }

  override fun actionPerformed(e: AnActionEvent?) {
    myPreview.setOverLay(null, null)
  }
}