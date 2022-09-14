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

import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.keymap.KeymapUtil
import org.jetbrains.android.util.AndroidBundle.message

class ToggleLiveLiteralsHighlightAction : AnAction(message("live.literals.highlight.toggle.title"),
                                                   message("live.literals.highlight.toggle.description"), null) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    LiveLiteralsService.getInstance(project).showLiveLiteralsHighlights = !LiveLiteralsService.getInstance(
      project).showLiveLiteralsHighlights
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    val project = e.project ?: return
    e.presentation.apply {
      isEnabledAndVisible = LiveLiteralsService.getInstance(project).isEnabled
      text = if (LiveLiteralsService.getInstance(project).showLiveLiteralsHighlights)
        message("live.literals.highlight.toggle.hide.title")
      else
        message("live.literals.highlight.toggle.show.title")
    }
  }

  companion object {
    private fun getShortcutLabel(): String {
      val highlightAction = ActionManager.getInstance().getAction("Compose.Live.Literals.ToggleHighlight")
      return highlightAction.shortcutSet.shortcuts.firstOrNull()?.let {
        " (${KeymapUtil.getShortcutText(it)})"
      } ?: ""
    }

    fun getActionAndShortcutLabel(): String {
      val highlightAction = ActionManager.getInstance().getAction("Compose.Live.Literals.ToggleHighlight")
      return "\"${highlightAction.templateText}\"${getShortcutLabel()}"
    }
  }
}