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
package com.android.tools.property.panel.impl.support

import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorContext
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.FlagsPropertyItem
import com.android.tools.property.panel.api.LinkPropertyItem
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.BasePropertyEditorModel
import com.android.tools.property.panel.impl.model.BooleanPropertyEditorModel
import com.android.tools.property.panel.impl.model.ColorFieldPropertyEditorModel
import com.android.tools.property.panel.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.property.panel.impl.model.FlagPropertyEditorModel
import com.android.tools.property.panel.impl.model.LinkPropertyEditorModel
import com.android.tools.property.panel.impl.model.TextFieldPropertyEditorModel
import com.android.tools.property.panel.impl.model.ThreeStateBooleanPropertyEditorModel
import com.android.tools.property.panel.impl.ui.ActionButtonBinding
import com.android.tools.property.panel.impl.ui.FlagPropertyEditor
import com.android.tools.property.panel.impl.ui.PropertyCheckBox
import com.android.tools.property.panel.impl.ui.PropertyComboBox
import com.android.tools.property.panel.impl.ui.PropertyLabel
import com.android.tools.property.panel.impl.ui.PropertyLink
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.android.tools.property.panel.impl.ui.PropertyTextFieldWithLeftButton
import com.android.tools.property.panel.impl.ui.PropertyThreeStateCheckBox
import javax.swing.JComponent

/**
 * A standard provider for a property editor.
 *
 * For a given property this class will provide a model and a UI for an editor of that property.
 * This implementation is computing the [ControlType] using the specified providers.
 *
 * @param P a client defined property class that must implement the interface: [PropertyItem]
 * @property enumSupportProvider provides an [EnumSupport] for a property or null if there isn't any
 * @property controlTypeProvider provides the [ControlType] given a property and its [EnumSupport]
 */
open class EditorProviderImpl<in P : PropertyItem>(
  private val enumSupportProvider: EnumSupportProvider<P>,
  private val controlTypeProvider: ControlTypeProvider<P>,
) : EditorProvider<P> {

  /** Create an editor for [property]. */
  override fun createEditor(
    property: P,
    context: EditorContext,
  ): Pair<PropertyEditorModel, JComponent> =
    when (controlTypeProvider(property)) {
      ControlType.COMBO_BOX ->
        createComboBoxEditor(property, true, enumSupportProvider(property)!!, context)
      ControlType.DROPDOWN ->
        createComboBoxEditor(property, false, enumSupportProvider(property)!!, context)
      ControlType.TEXT_EDITOR -> {
        // For table cell renderers: use a JLabel based component instead of a JTextEdit based
        // component,
        // to avoid unwanted horizontal scrolling.
        val model = TextFieldPropertyEditorModel(property, true)
        val editor =
          if (context != EditorContext.TABLE_RENDERER) PropertyTextField(model)
          else PropertyLabel(model)
        Pair(model, addActionButtonBinding(model, editor))
      }
      ControlType.COLOR_EDITOR -> {
        val model = ColorFieldPropertyEditorModel(property)
        val editor = PropertyTextFieldWithLeftButton(model, context)
        Pair(model, addActionButtonBinding(model, editor))
      }
      ControlType.THREE_STATE_BOOLEAN -> {
        val model = ThreeStateBooleanPropertyEditorModel(property)
        val editor = PropertyThreeStateCheckBox(model, context)
        Pair(model, addActionButtonBinding(model, editor))
      }
      ControlType.FLAG_EDITOR -> {
        val model = FlagPropertyEditorModel(property as FlagsPropertyItem<*>)
        val editor = FlagPropertyEditor(model, context)
        Pair(model, addActionButtonBinding(model, editor))
      }
      ControlType.BOOLEAN -> {
        val model = BooleanPropertyEditorModel(property)
        val editor = PropertyCheckBox(model, context)
        Pair(model, addActionButtonBinding(model, editor))
      }
      ControlType.LINK_EDITOR -> {
        val model = LinkPropertyEditorModel(property as LinkPropertyItem)
        val editor = PropertyLink(model)
        Pair(model, addActionButtonBinding(model, editor))
      }
      ControlType.CUSTOM_EDITOR_1, // Placeholders for custom controls
      ControlType.CUSTOM_EDITOR_2 -> throw NotImplementedError()
    }

  protected open fun createComboBoxEditor(
    property: P,
    editable: Boolean,
    enumSupport: EnumSupport,
    context: EditorContext,
  ): Pair<PropertyEditorModel, JComponent> {
    val model = ComboBoxPropertyEditorModel(property, enumSupport, editable)
    val comboBox = PropertyComboBox(model, context)
    comboBox.renderer = enumSupport.renderer
    return Pair(model, addActionButtonBinding(model, comboBox))
  }

  protected open fun addActionButtonBinding(
    model: BasePropertyEditorModel,
    editor: JComponent,
  ): JComponent {
    return if (model.property.browseButton == null) editor else ActionButtonBinding(model, editor)
  }
}
