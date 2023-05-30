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
import com.android.tools.idea.modes.essentials.EssentialsMode.isEnabled
import com.intellij.ide.EssentialHighlightingMode
import com.intellij.ide.actions.ToggleEssentialHighlightingAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction


class EssentialsModeToggleAction : ToggleAction(
  "Essentials Mode") {

  private val essentialsModeFlag: Flag<Boolean> = StudioFlags.ESSENTIALS_MODE_VISIBLE
  private val essentialHighlightingEnabled: Flag<Boolean> = StudioFlags.ESSENTIALS_HIGHLIGHTING_MODE
  // keeping Essential Highlighting separable from Essentials Mode if it's determined at a future
  // date that most users would prefer this feature not bundled with Essentials Mode
  private val essentialHighlightingAction = ToggleEssentialHighlightingAction()

  override fun isSelected(e: AnActionEvent): Boolean {
    return isEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (essentialHighlightingEnabled.get()) essentialHighlightingAction.setSelected(e, state)
    EssentialsMode.setEnabled(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = essentialsModeFlag.get()
    if (!essentialsModeFlag.get()) {
      EssentialsMode.setEnabled(false)
      EssentialHighlightingMode.setEnabled(false)
    }
  }
}
