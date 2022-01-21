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
import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.android.tools.idea.logcat.messages.AndroidLogcatFormattingOptions
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatFormatPresetsDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

/**
 * An action that opens the [LogcatFormatPresetsDialog]
 */
internal class LogcatFormatModifyViewsAction(
  private val project: Project,
  private val logcatPresenter: LogcatPresenter,
) : DumbAwareAction(LogcatBundle.message("logcat.format.action.modify")) {

  override fun actionPerformed(e: AnActionEvent) {
    val androidLogcatFormattingOptions = AndroidLogcatFormattingOptions.getInstance()
    val defaultFormatting = androidLogcatFormattingOptions.defaultFormatting
    val initialFormatting = logcatPresenter.formattingOptions.getStyle() ?: defaultFormatting

    val dialog = LogcatFormatPresetsDialog(project, initialFormatting, defaultFormatting)
    if (dialog.dialogWrapper.showAndGet()) {
      androidLogcatFormattingOptions.standardFormattingOptions.copyFrom(dialog.standardFormattingOptions)
      androidLogcatFormattingOptions.compactFormattingOptions.copyFrom(dialog.compactFormattingOptions)
      androidLogcatFormattingOptions.defaultFormatting = dialog.defaultFormatting
      LogcatToolWindowFactory.logcatPresenters.filter { it.formattingOptions.getStyle() != null }.forEach(LogcatPresenter::reloadMessages)
    }
  }
}

private fun FormattingOptions.copyFrom(other: FormattingOptions) {
  timestampFormat = other.timestampFormat
  processThreadFormat = other.processThreadFormat
  tagFormat = other.tagFormat
  appNameFormat = other.appNameFormat
}