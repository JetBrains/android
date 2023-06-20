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
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware

/**
 * Toggles Soft Wrap for Logcat panels.
 */
internal class LogcatToggleUseSoftWrapsToolbarAction : ToggleAction(), DumbAware {
  init {
    ActionUtil.copyFrom(this, IdeActions.ACTION_EDITOR_USE_SOFT_WRAPS)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val logcatPresenter = e.getData(LOGCAT_PRESENTER_ACTION) ?: return false
    return logcatPresenter.isSoftWrapEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val logcatPresenter = e.getData(LOGCAT_PRESENTER_ACTION) ?: return
    logcatPresenter.setSoftWrapEnabled(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread  = ActionUpdateThread.EDT
}
