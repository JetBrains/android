/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.uibuilder.visual.VisualizationForm.Companion.VISUALIZATION_FORM
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/** The dropdown action used to choose the configuration set in visualization tool. */
class ConfigurationSetMenuAction(defaultSet: ConfigurationSet) :
  DropDownAction(null, "Configuration Set", null) {

  private var currentConfigurationSet = defaultSet

  init {
    var isPreviousGroupEmpty = true
    for (group in ConfigurationSetProvider.getGroupedConfigurationSets()) {
      if (!isPreviousGroupEmpty) {
        addSeparator()
      }
      val groupItems = group.filter { it.visible }
      groupItems.forEach { add(SetConfigurationSetAction(it)) }
      isPreviousGroupEmpty = groupItems.isEmpty()
    }
  }

  override fun displayTextInToolbar() = true

  override fun update(e: AnActionEvent) {
    e.presentation.text = currentConfigurationSet.name
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private inner class SetConfigurationSetAction(private val configurationSet: ConfigurationSet) :
    ToggleAction(configurationSet.name, "Set configuration set to ${configurationSet.name}", null) {

    override fun isSelected(e: AnActionEvent) = currentConfigurationSet.id === configurationSet.id

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state && configurationSet !== currentConfigurationSet) {
        currentConfigurationSet = configurationSet
        e.getData(VISUALIZATION_FORM)?.onSelectedConfigurationSetChanged(configurationSet)
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }
}
