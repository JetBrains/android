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
package com.android.tools.adtui.actions

import com.android.tools.adtui.ZOOMABLE_KEY
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.JComponent

/**
 * Similar to ZoomFitAction, but it's just a button with a text that reads 'Reset' (no icon).
 */
object ZoomResetAction : SetZoomAction(ZoomType.FIT), CustomComponentAction {
  private const val BLUE_COLOR_RGB = 0x1a7dc4
  private val myTextColor = Color(BLUE_COLOR_RGB) // For both light and dark mode.

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.icon = EmptyIcon.ICON_0
    event.presentation.disabledIcon = EmptyIcon.ICON_0
    event.presentation.isEnabled = event.getData(ZOOMABLE_KEY)?.canZoomToFit() ?: false
    if (event.place.contains("Surface")) {
      event.presentation.text = "Reset"
    }
    else {
      event.presentation.text = "Reset Zoom"
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return ActionButtonWithText(this, presentation, place, JBUI.size(60, 0)).apply {
      foreground = myTextColor
    }
  }
}