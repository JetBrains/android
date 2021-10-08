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
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

/**
 * An action that opens a [HeaderFormatOptionsDialog] and applies changes to the document.
 */
internal class HeaderFormatOptionsAction(
  private val project: Project,
  private val logcatPresenter: LogcatPresenter,
  private val formattingOptions: FormattingOptions,
) : DumbAwareAction(
  LogcatBundle.message("logcat.header.options.title"),
  LogcatBundle.message("logcat.header.options.description"),
  AllIcons.General.LayoutEditorPreview) {

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = HeaderFormatOptionsDialog(project, formattingOptions)
    if (dialog.dialogWrapper.showAndGet()) {
      dialog.applyTo(formattingOptions)
      logcatPresenter.reloadMessages()
    }
  }
}