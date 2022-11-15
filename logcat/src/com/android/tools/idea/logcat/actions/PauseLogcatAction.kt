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
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * Pauses/Resumes Logcat collection
 */
internal class PauseLogcatAction(private val logcatPresenter: LogcatPresenter)
  : DumbAwareAction(LogcatBundle.message("logcat.pause.action.pause.text"), "", AllIcons.Actions.Pause) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = logcatPresenter.getConnectedDevice() != null
    e.presentation.text = getActionText(logcatPresenter)
    e.presentation.icon = getActionIcon(logcatPresenter)
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (logcatPresenter.isLogcatPaused()) {
      logcatPresenter.resumeLogcat()
    }
    else {
      logcatPresenter.pauseLogcat()
    }
  }
}

private fun getActionText(logcatPresenter: LogcatPresenter) = when {
  logcatPresenter.isLogcatPaused() -> LogcatBundle.message("logcat.pause.action.resume.text")
  else -> LogcatBundle.message("logcat.pause.action.pause.text")
}

private fun getActionIcon(logcatPresenter: LogcatPresenter) = when {
  logcatPresenter.isLogcatPaused() -> AllIcons.Actions.Resume
  else -> AllIcons.Actions.Pause
}
