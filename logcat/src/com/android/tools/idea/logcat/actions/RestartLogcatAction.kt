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
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * An action that restarts Logcat on the connected device.
 */
internal class RestartLogcatAction : DumbAwareAction(LogcatBundle.message("logcat.restart.action.text"), null, AllIcons.Actions.Restart) {

  override fun update(e: AnActionEvent) {
    val logcatPresenter = e.getData(LogcatPresenter.LOGCAT_PRESENTER_ACTION) ?: return
    e.presentation.isEnabled = logcatPresenter.getConnectedDevice() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val logcatPresenter = e.getData(LogcatPresenter.LOGCAT_PRESENTER_ACTION) ?: return
    logcatPresenter.restartLogcat()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
