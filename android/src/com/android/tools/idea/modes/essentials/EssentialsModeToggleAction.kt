/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.modes.essentials

import com.android.flags.Flag
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction


class EssentialsModeToggleAction : ToggleAction(
  "Essentials Mode") {

  private val essentialsModeFlag: Flag<Boolean> = StudioFlags.ESSENTIALS_MODE_VISIBLE

  override fun isSelected(e: AnActionEvent): Boolean {
    return EssentialsMode.isEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    EssentialsMode.setEnabled(state, e.project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = essentialsModeFlag.get()
  }
}
