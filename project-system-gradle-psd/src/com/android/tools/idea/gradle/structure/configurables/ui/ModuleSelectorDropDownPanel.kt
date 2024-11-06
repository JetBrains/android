/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.tools.idea.gradle.structure.configurables.BasePerspectiveConfigurable
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import java.awt.BorderLayout
import javax.swing.JPanel

class ModuleSelectorDropDownPanel(
  context: PsContext,
  perspective: BasePerspectiveConfigurable
) : JPanel(BorderLayout()) {

  private val actions = createToolbarActions(context, perspective)
  private val toolbar = createToolbar(actions)

  init {
    addToolbar(toolbar)
  }

  fun update() {
    toolbar.updateActionsAsync()
  }

  private fun addToolbar(toolbar: ActionToolbar) {
    add(toolbar.component, BorderLayout.CENTER)
  }
}

private fun createToolbarActions(context: PsContext, perspective: BasePerspectiveConfigurable) =
  DefaultActionGroup(
    ModulesComboBoxAction(context, perspective),
    object : DumbAwareAction("Restore 'Modules' List", "", AllIcons.Actions.MoveTo2) {
      override fun actionPerformed(e: AnActionEvent) =
        with(context.uiSettings) {
          MODULES_LIST_MINIMIZE = false
          fireUISettingsChanged()
        }
    })

private fun createToolbar(actions: ActionGroup) =
  ActionManager.getInstance().createActionToolbar("TOP", actions, true).apply {
    setTargetComponent(null)
    component.border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
  }

