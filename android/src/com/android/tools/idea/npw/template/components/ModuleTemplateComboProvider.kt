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
package com.android.tools.idea.npw.template.components

import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import javax.swing.DefaultComboBoxModel
import javax.swing.JList

/**
 * Provides a combobox which presents the user with a list of source sets.
 *
 * @see NamedModuleTemplate
 */
class ModuleTemplateComboProvider(private val templates: List<NamedModuleTemplate>) : ComponentProvider<ComboBox<*>>() {
  override fun createComponent(): ComboBox<*> {
    val comboBoxModel = DefaultComboBoxModel<Any>()
    templates.forEach {
      comboBoxModel.addElement(it)
    }

    return ComboBox(comboBoxModel).apply {
      toolTipText = "<html>The source set within which to generate new project files.<br>" +
                    "If you specify a source set that does not yet exist on disk, a folder will be created for it.</html>"
      renderer = object : SimpleListCellRenderer<Any>() {
        override fun customize(list: JList<*>, value: Any, index: Int, selected: Boolean, hasFocus: Boolean) {
          text = (value as NamedModuleTemplate).name
        }
      }
    }
  }

  override fun createProperty(component: ComboBox<*>): AbstractProperty<*> = SelectedItemProperty<NamedModuleTemplate>(component)
}

