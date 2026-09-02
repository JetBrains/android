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
// TODO: android-merge; only used by the Groovy filter neutralized below.
// import com.jetbrains.rd.util.remove
import javax.swing.DefaultComboBoxModel
import javax.swing.JList
import org.jetbrains.android.util.AndroidBundle.message

/** Provides a combobox which presents the user with a list of build configuration languages. */
class BuildConfigurationLanguageComboProvider : ComponentProvider<ComboBox<*>>() {
  override fun createComponent(): ComboBox<BuildConfigurationLanguageForNewModule> =
    // TODO: android-merge; BuildConfigurationLanguageForNewModule.Groovy is not in the studio-platform jar, whose copy of the enum
    // has only KTS and DCL, so values() already excludes Groovy and the filter upstream added cannot be expressed here.
    // ComboBox(DefaultComboBoxModel(BuildConfigurationLanguageForNewModule.values().remove(BuildConfigurationLanguageForNewModule.Groovy)))
    ComboBox(DefaultComboBoxModel(BuildConfigurationLanguageForNewModule.values()))
      .apply {
        renderer =
          object : SimpleListCellRenderer<BuildConfigurationLanguageForNewModule>() {
            override fun customize(
              list: JList<out BuildConfigurationLanguageForNewModule>,
              value: BuildConfigurationLanguageForNewModule?,
              index: Int,
              selected: Boolean,
              hasFocus: Boolean,
            ) {
              text = value.toString()
            }
          }
        toolTipText = message("android.wizard.buildConfigurationLanguage.combo.tooltip")
      }

  override fun createProperty(component: ComboBox<*>): AbstractProperty<*> = SelectedItemProperty<String>(component)
}
