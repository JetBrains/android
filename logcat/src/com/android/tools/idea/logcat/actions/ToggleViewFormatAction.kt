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

import com.android.tools.idea.logcat.LogcatPresenter.Companion.LOGCAT_PRESENTER_ACTION
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * An action that toggles between Compact and Standard views
 *
 * This action is registered in the plugin XML and is not visible. It's only available as a KB shortcut. Since it's registered with the
 * plugin, it's discoverable via Ctrl-Shift-A and the shortcut is configurable via the Keymap settings
 */
internal class ToggleViewFormatAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val formattingOptions = e.getData(LOGCAT_PRESENTER_ACTION)?.formattingOptions
    e.presentation.isVisible = formattingOptions === COMPACT.formattingOptions || formattingOptions === STANDARD.formattingOptions
  }

  override fun actionPerformed(e: AnActionEvent) {
    val logcatPresenter = e.getData(LOGCAT_PRESENTER_ACTION) ?: return

    logcatPresenter.formattingOptions = when {
      logcatPresenter.formattingOptions === COMPACT.formattingOptions -> STANDARD.formattingOptions
      logcatPresenter.formattingOptions === STANDARD.formattingOptions -> COMPACT.formattingOptions
      else -> return
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}