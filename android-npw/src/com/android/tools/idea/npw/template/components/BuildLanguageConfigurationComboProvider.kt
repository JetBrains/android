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
package com.android.tools.idea.npw.template.components

import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewModule
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.DefaultComboBoxModel
import javax.swing.JList

/**
 * Provides a combobox which presents the user with a list of build configuration languages.
 */
class BuildConfigurationLanguageComboProvider : ComponentProvider<ComboBox<*>>() {
  override fun createComponent(): ComboBox<BuildConfigurationLanguageForNewModule> = ComboBox(
    DefaultComboBoxModel(BuildConfigurationLanguageForNewModule.values())).apply {
    renderer = object : SimpleListCellRenderer<BuildConfigurationLanguageForNewModule>() {
      override fun customize(list: JList<out BuildConfigurationLanguageForNewModule>,
                             value: BuildConfigurationLanguageForNewModule?,
                             index: Int,
                             selected: Boolean,
                             hasFocus: Boolean) {
        text = value.toString()
      }
    }
    toolTipText = message("android.wizard.buildConfigurationLanguage.combo.tooltip")
  }

  override fun createProperty(component: ComboBox<*>): AbstractProperty<*> = SelectedItemProperty<String>(component)
}

