/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.LogcatPresenter
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory

/**
 * An action that opens a popup menu with Logcat format-related actions
 */
internal class LogcatFormatAction(private val project: Project, private val logcatPresenter: LogcatPresenter)
  : DumbAwareAction(null, LogcatBundle.message("logcat.format.action.description"), AllIcons.Actions.Properties) {

  override fun actionPerformed(e: AnActionEvent) {
    val actionGroup = DefaultActionGroup(
      LogcatFormatPresetAction.Standard(logcatPresenter),
      LogcatFormatPresetAction.Compact(logcatPresenter),
      LogcatFormatCustomViewAction(project, logcatPresenter),
    )
    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, actionGroup, e.dataContext, null, true)
      .showUnderneathOf(e.inputEvent.component)
  }
}
