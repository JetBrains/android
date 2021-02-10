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
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import org.jetbrains.android.util.AndroidBundle.message

class ToggleLiveLiteralsHighlightAction : ToggleAction(message("live.literals.highlight.toggle.show.title"),
                                                       message("live.literals.highlight.toggle.description"), null) {
  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return LiveLiteralsService.getInstance(project).showLiveLiteralsHighlights
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    LiveLiteralsService.getInstance(project).showLiveLiteralsHighlights = state
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
    fun getShortcutLabel(): String {
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

/**
 * Action that opens the keymap configuration for the [ToggleLiveLiteralsHighlightAction].
 */
internal class UpdateHighlightsKeymapAction : AnAction(
  message("live.literals.configure.keymap.title", ToggleLiveLiteralsHighlightAction.getShortcutLabel())) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val keymapConfigurable = ShowSettingsUtilImpl.getConfigurables(project, true).find {
      it.displayName == KeyMapBundle.message("keymap.display.name")
    }
    val settingsGroup = listOf(ConfigurableExtensionPointUtil.getConfigurableGroup(listOf(keymapConfigurable), project))

    SettingsDialogFactory.getInstance().create(
      project,
      settingsGroup,
      keymapConfigurable,
      message("live.literals.highlight.toggle.show.title")
    ).show()
  }
}