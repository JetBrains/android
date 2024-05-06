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
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.android.tools.idea.logcat.messages.LogcatFormatDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

/** An action that opens the [LogcatFormatDialog] */
internal class LogcatFormatModifyViewsAction(
  private val project: Project,
  private val logcatPresenter: LogcatPresenter,
) : DumbAwareAction(LogcatBundle.message("logcat.format.modify.action.text")) {

  override fun actionPerformed(e: AnActionEvent) {
    val androidLogcatFormattingOptions = AndroidLogcatFormattingOptions.getInstance()
    val defaultFormatting = androidLogcatFormattingOptions.defaultFormatting
    val initialFormatting = logcatPresenter.formattingOptions.getStyle() ?: defaultFormatting
    LogcatFormatDialog(project, initialFormatting, defaultFormatting) {
        standardOptions,
        compactOptions,
        defaultStyle ->
        LogcatToolWindowFactory.logcatPresenters.forEach {
          when (it.formattingOptions.getStyle()) {
            STANDARD -> it.formattingOptions = standardOptions
            COMPACT -> it.formattingOptions = compactOptions
            else -> {}
          }
        }
        androidLogcatFormattingOptions.standardFormattingOptions = standardOptions
        androidLogcatFormattingOptions.compactFormattingOptions = compactOptions
        androidLogcatFormattingOptions.defaultFormatting = defaultStyle
      }
      .dialogWrapper
      .show()
  }
}
