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
package com.android.tools.idea.logcat.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.openapi.util.NlsActions.ActionText
import javax.swing.Icon

/**
 * An action that opens a popup with more actions
 */
internal abstract class PopupActionGroupAction(
  text: @ActionText String?,
  description: @ActionDescription String?,
  icon: Icon,
) : DumbAwareAction(text, description, icon) {
  override fun actionPerformed(e: AnActionEvent) {
    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, DefaultActionGroup(getPopupActions()), e.dataContext, null, true)
      .showUnderneathOf(e.inputEvent!!.component)
  }

  abstract fun getPopupActions() : List<AnAction>
}
