/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.android.tools.idea.logcat.util.FilterHint.AppName
import com.android.tools.idea.logcat.util.getFilterHint
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/** An action that adds or a tag to the global ignore tag set */
internal class IgnoreAppAction : DumbAwareAction("Ignore Tag") {

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = false
    val appId = e.findApplicationIdAtCaret() ?: return
    e.presentation.isVisible = true
    e.presentation.text = LogcatBundle.message("logcat.ignore.app", appId)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val appId = e.findApplicationIdAtCaret() ?: return
    val settings = AndroidLogcatSettings.getInstance()
    settings.ignoredApps += appId
    LogcatToolWindowFactory.logcatPresenters.forEach { it.reloadMessages() }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

private fun AnActionEvent.findApplicationIdAtCaret(): String? {
  val editor = getEditor() ?: return null
  val formattingOptions = getLogcatPresenter()?.formattingOptions ?: return null
  val offset = editor.caretModel.offset
  return editor.getFilterHint(offset, formattingOptions)?.takeIf { it is AppName }?.text
}
