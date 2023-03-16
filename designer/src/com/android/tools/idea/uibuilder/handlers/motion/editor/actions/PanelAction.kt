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
package com.android.tools.idea.uibuilder.handlers.motion.editor.actions

import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.BaseCreatePanel
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnActionButton
import java.awt.Component
import javax.swing.JComponent

/**
 * AnAction for target [BaseCreatePanel].
 */
class PanelAction(private val panel: BaseCreatePanel, private val motionEditor: MotionEditor)
  : AnActionButton(panel.name, panel.icon) {

  /**
   * If current action appears in popup, context should be set, otherwise it will use component which already could be not available.
   */
  var context: Component? = null

  override fun actionPerformed(e: AnActionEvent) {
    panel.doAction((context ?: e.inputEvent.component) as JComponent, motionEditor)
  }
}
