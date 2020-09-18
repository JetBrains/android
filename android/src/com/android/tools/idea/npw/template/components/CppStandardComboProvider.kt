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
import com.android.tools.idea.wizard.template.CppStandardType
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.DefaultComboBoxModel
import javax.swing.JList

/**
 * Provides a combobox which presents the user with a list of C++ standards.
 */
class CppStandardComboProvider : ComponentProvider<ComboBox<CppStandardType>>() {
  override fun createComponent(): ComboBox<CppStandardType> = ComboBox(DefaultComboBoxModel(CppStandardType.values())).apply {
    renderer = object : SimpleListCellRenderer<CppStandardType>() {
      override fun customize(list: JList<out CppStandardType>, value: CppStandardType, index: Int, selected: Boolean, hasFocus: Boolean) {
        text = value.name
      }
    }
    toolTipText = message("android.wizard.cppStandard.combo.tooltip")
  }

  override fun createProperty(component: ComboBox<CppStandardType>): AbstractProperty<*> = SelectedItemProperty<String>(component)
}

