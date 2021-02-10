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
package com.android.tools.idea.editors.literals.actions

import com.android.tools.idea.editors.literals.LiveLiteralsApplicationConfiguration
import com.android.tools.idea.editors.literals.ui.LiveLiteralsConfigurable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import org.jetbrains.android.util.AndroidBundle.message

/**
 * Action that opens the Live Literals settings page for the user to enable/disable live literals.
 */
internal class ToggleLiveLiteralsStatusAction: AnAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.text = if (LiveLiteralsApplicationConfiguration.getInstance().isEnabled)
      message("live.literals.action.disable.title")
    else
      message("live.literals.action.enable.title")
  }

  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, LiveLiteralsConfigurable::class.java)
  }
}