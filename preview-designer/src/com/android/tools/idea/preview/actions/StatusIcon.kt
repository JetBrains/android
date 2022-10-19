/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Wraps [component] into a [JComponent] that has a fixed size regardless of the [component] visibility.
 */
private fun fixedSizeWrapper(component: JComponent) =
  JPanel(BorderLayout()).apply {
    border = JBUI.Borders.empty()
    isOpaque = false
    isVisible = true
    add(component, BorderLayout.LINE_END)

    // Make the size to be fixed, even when the no icon is visible
    minimumSize = component.minimumSize
    preferredSize = component.minimumSize
  }

/**
 * Returns a preview status icon from corresponding [AnAction] and a [target] component.
 */
fun createStatusIcon(action: AnAction, target: JComponent): JComponent {
  val component =
    ActionManagerEx.getInstanceEx()
      .createActionToolbar(
        "sceneView",
        DefaultActionGroup(action),
        true,
        false
      )
      .apply {
        targetComponent = target
        (this as? ActionToolbarImpl)?.setForceMinimumSize(true)
        setMiniMode(true)
      }
      .component
      .apply {
        isOpaque = false
        border = JBUI.Borders.empty()
      }

  return fixedSizeWrapper(component)
}
