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
package com.android.tools.idea.compose.pickers.common.inspector

import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.property.panel.impl.support.EditorProviderImpl
import com.android.tools.property.panel.impl.ui.PropertyComboBox
import javax.swing.JComponent

/**
 * Custom EditorProvider for PsiProperties, makes sure that we use the correct component and
 * renderer to instantiate Dropdowns.
 */
internal class PsiEditorProvider(
  enumSupportProvider: EnumSupportProvider<PsiPropertyItem>,
  psiControlTypeProvider: PsiPropertyItemControlTypeProvider
) : EditorProviderImpl<PsiPropertyItem>(enumSupportProvider, psiControlTypeProvider) {

  override fun createComboBoxEditor(
    property: PsiPropertyItem,
    editable: Boolean,
    enumSupport: EnumSupport,
    asTableCellEditor: Boolean
  ): Pair<PropertyEditorModel, JComponent> {
    return if (editable) {
      // Use existing components for ComboBox
      val model = ComboBoxPropertyEditorModel(property, enumSupport, editable)
      val comboBox = PropertyComboBox(model, asTableCellEditor)
      comboBox.renderer = enumSupport.renderer
      Pair(model, addActionButtonBinding(model, comboBox))
    } else {
      // Use a specific component for DropDown
      val model = PsiDropDownModel(property, enumSupport)
      val comboBox = PsiPropertyDropDown(model, asTableCellEditor, enumSupport.renderer)
      Pair(model, addActionButtonBinding(model, comboBox))
    }
  }
}

/** [ControlTypeProvider] for [PsiPropertyItem]s that provides a text editor for every property. */
internal typealias PsiPropertyItemControlTypeProvider = ControlTypeProvider<PsiPropertyItem>
