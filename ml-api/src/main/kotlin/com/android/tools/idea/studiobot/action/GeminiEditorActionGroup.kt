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
package com.android.tools.idea.studiobot.action

import com.android.tools.idea.studiobot.StudioBot
import com.intellij.ide.actions.NonTrivialActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.MainMenuPresentationAware
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware

/**
 * Action group showing an "Ask Studio Bot" menu in the editor, *if* the user is signed in and has
 * completed onboarding for Studio Bot. The menu contains actions like
 * [com.android.studio.ml.bot.action.ExplainCodeAction] and
 * [com.android.studio.ml.bot.action.transform.DocumentElementAction].
 */
class GeminiEditorActionGroup : NonTrivialActionGroup(), DumbAware, MainMenuPresentationAware {

  override fun alwaysShowIconInMainMenu() = true // Ensure the Gemini icon shows on MacOS.

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = isEditorActionGroupVisible(e)
    e.presentation.isEnabled = isActionAllowedByAiExclude(e)
  }

  /**
   * These rules are applied to the action group, but are exposed so that the same logic can be
   * applied to the individual [EditorCodeAction]s.
   */
  companion object {
    fun isEditorActionGroupVisible(e: AnActionEvent): Boolean {
      if (e.project == null) return false
      if (!service<StudioBot>().isAvailable()) return false
      val editor = e.getData(CommonDataKeys.EDITOR)
      return editor is EditorImpl
    }

    fun isActionAllowedByAiExclude(e: AnActionEvent): Boolean {
      if (!isEditorActionGroupVisible(e)) return false

      // Check the editor file against aiexclude
      val project = e.project ?: return false
      val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
      return !service<StudioBot>().aiExcludeService(project).isFileExcluded(virtualFile)
    }
  }
}
