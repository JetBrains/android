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

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * An action that chooses a custom format and opens a dialog to configure it
 */
internal class LogcatFormatCustomViewAction(
  private val project: Project,
  private val logcatPresenter: LogcatPresenter,
  ): SelectableAction(LogcatBundle.message("logcat.format.action.custom")) {

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = HeaderFormatOptionsDialog(project, logcatPresenter.formattingOptions)
    if (dialog.dialogWrapper.showAndGet()) {
      val formattingOptions = FormattingOptions()
      dialog.applyTo(formattingOptions)
      logcatPresenter.formattingOptions = formattingOptions
    }
  }

  override fun isSelected(): Boolean = logcatPresenter.formattingOptions.getStyle() == null
}