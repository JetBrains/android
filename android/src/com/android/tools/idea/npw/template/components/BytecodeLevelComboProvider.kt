/*
 * Copyright (C) 2020 The Android Open Source ProjecT
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
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ListCellRendererWrapper
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.DefaultComboBoxModel
import javax.swing.JList

/**
 * Provides a combobox which presents the user with a list of possible bytecode support levels (e.g. 1.6, 1.8 etc).
 */
class BytecodeLevelComboProvider : ComponentProvider<ComboBox<*>>() {
  override fun createComponent(): ComboBox<BytecodeLevel> = ComboBox(DefaultComboBoxModel(BytecodeLevel.values())).apply {
    renderer = object : ListCellRendererWrapper<BytecodeLevel>() {
      override fun customize(list: JList<*>, value: BytecodeLevel?, index: Int, selected: Boolean, hasFocus: Boolean) {
        setText(value?.toString() ?: message("android.wizard.language.combo.empty"))
      }
    }
    toolTipText = message("android.wizard.language.combo.tooltip")
  }

  override fun createProperty(component: ComboBox<*>): AbstractProperty<*> = SelectedItemProperty<BytecodeLevel>(component)
}
