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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.AnActionButton
import javax.swing.Icon

/**
 * Action which will open popup with other [actions].
 */
abstract class OpenPopUpAction : AnActionButton {

  constructor(name: String, icon: Icon) : super(name, icon)
  constructor(name: String) : super(name)

  abstract val actions: List<PanelAction>
  override fun actionPerformed(e: AnActionEvent) {
    // Context should be set to actions in popup as otherwise it will use component which already could be not available.
    actions.forEach { it.context = e.inputEvent.component }
    val menu = JBPopupFactory.getInstance().createActionGroupPopup(
      null, DefaultActionGroup(actions), e.dataContext, null, true)
    menu.showUnderneathOf(e.inputEvent.component)
  }
}