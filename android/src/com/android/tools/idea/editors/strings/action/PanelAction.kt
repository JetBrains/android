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
package com.android.tools.idea.editors.strings.action

import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.StringResourceViewPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.openapi.util.NlsActions.ActionText
import javax.swing.Icon

/**
 * Base class for actions on a [StringResourceViewPanel].
 *
 * Makes accessing the panel from [AnActionEvent] easy so that subclasses need not hold on to a
 * reference to the panel.
 */
abstract class PanelAction(
    @ActionText text: String? = null,
    @ActionDescription description: String? = null,
    icon: Icon? = null
) : AnAction(text, description, icon) {
  /** The [StringResourceViewPanel] associated with `this` [AnActionEvent]. */
  protected val AnActionEvent.panel: StringResourceViewPanel
    get() = (getRequiredData(PlatformDataKeys.FILE_EDITOR) as StringResourceEditor).panel
  /** The non-`null` [Project] associated with `this` [AnActionEvent]. */
  protected val AnActionEvent.requiredProject: Project
    get() = getRequiredData(CommonDataKeys.PROJECT)

  private fun AnActionEvent.hasRequiredData(): Boolean =
      (getData(PlatformDataKeys.FILE_EDITOR) is StringResourceEditor) && (project != null)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  final override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.hasRequiredData() && doUpdate(e)
  }

  /**
   * Handles action-specific [AnAction.update(AnActionEvent)] functionality. Only called if the
   * [AnActionEvent] has a valid `panel` and `requiredProject`.
   *
   * @return whether or not the action should be enabled
   */
  protected abstract fun doUpdate(event: AnActionEvent): Boolean
}
