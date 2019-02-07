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
package com.android.tools.idea.uibuilder.property2.testutils

import com.android.tools.idea.common.property2.api.ControlType
import com.android.tools.idea.common.property2.api.EditorProvider
import com.android.tools.idea.common.property2.api.FlagsPropertyItem
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.BooleanPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.ColorFieldPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.FlagPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.TextFieldPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.ThreeStateBooleanPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.util.TestInspectorPanel
import com.android.tools.idea.common.property2.impl.support.PropertiesTableImpl
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertiesProvider
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.support.NeleControlTypeProvider
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import javax.swing.JComponent
import javax.swing.JPanel

class InspectorTestUtil(projectRule: AndroidProjectRule, vararg tags: String, parentTag: String = "")
  : SupportTestUtil(projectRule, *tags, parentTag = parentTag) {

  private val _properties: Table<String, String, NelePropertyItem> = HashBasedTable.create()

  val properties: PropertiesTable<NelePropertyItem> = PropertiesTableImpl(_properties)

  val editorProvider = FakeEditorProviderImpl(model)

  val inspector = TestInspectorPanel()

  init {
    model.setPropertiesInTest(properties)
  }

  fun addProperty(namespace: String, name: String, type: NelePropertyType) {
    _properties.put(namespace, name, makeProperty(namespace, name, type))
  }

  fun addFlagsProperty(namespace: String, name: String, values: List<String>) {
    _properties.put(namespace, name, makeFlagsProperty(namespace, name, values))
  }

  fun removeProperty(namespace: String, name: String) {
    _properties.remove(namespace, name)
  }

  fun loadProperties() {
    val provider = NelePropertiesProvider(model.facet)
    for (propertyItem in provider.getProperties(model, null, components).values) {
      _properties.put(propertyItem.namespace, propertyItem.name, propertyItem)
    }
  }
}

class FakeEditorProviderImpl(model: NelePropertiesModel): EditorProvider<NelePropertyItem> {
  private val enumSupportProvider = NeleEnumSupportProvider(model)
  private val controlTypeProvider = NeleControlTypeProvider(enumSupportProvider)

  override fun createEditor(property: NelePropertyItem, asTableCellEditor: Boolean): Pair<PropertyEditorModel, JComponent> {
    val enumSupport = enumSupportProvider(property)
    val controlType = controlTypeProvider(property)

    when (controlType) {
      ControlType.COMBO_BOX ->
        return Pair(ComboBoxPropertyEditorModel(property, enumSupport!!, true), JPanel())

      ControlType.DROPDOWN ->
        return Pair(ComboBoxPropertyEditorModel(property, enumSupport!!, false), JPanel())

      ControlType.TEXT_EDITOR ->
        return Pair(TextFieldPropertyEditorModel(property, true), JPanel())

      ControlType.THREE_STATE_BOOLEAN ->
        return Pair(ThreeStateBooleanPropertyEditorModel(property), JPanel())

      ControlType.FLAG_EDITOR ->
        return Pair(FlagPropertyEditorModel(property as FlagsPropertyItem<*>), JPanel())

      ControlType.BOOLEAN ->
        return Pair(BooleanPropertyEditorModel(property), JPanel())

      ControlType.COLOR_EDITOR ->
        return Pair(ColorFieldPropertyEditorModel(property), JPanel())
    }
  }
}
