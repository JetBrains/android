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
package com.android.tools.idea.uibuilder.property.testutils

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertiesModelTest
import com.android.tools.idea.uibuilder.property.NlPropertiesProvider
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.support.NlControlTypeProvider
import com.android.tools.idea.uibuilder.property.support.NlEnumSupportProvider
import com.android.tools.idea.uibuilder.property.ui.EmptyTablePanel
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EditorContext
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.FlagsPropertyItem
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.LinkPropertyItem
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.impl.model.BooleanPropertyEditorModel
import com.android.tools.property.panel.impl.model.ColorFieldPropertyEditorModel
import com.android.tools.property.panel.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.property.panel.impl.model.FlagPropertyEditorModel
import com.android.tools.property.panel.impl.model.LinkPropertyEditorModel
import com.android.tools.property.panel.impl.model.TextFieldPropertyEditorModel
import com.android.tools.property.panel.impl.model.ThreeStateBooleanPropertyEditorModel
import com.android.tools.property.panel.impl.model.util.FakeComponentLineModel
import com.android.tools.property.panel.impl.model.util.FakeInspectorLineModel
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel
import com.android.tools.property.panel.impl.model.util.FakeLineType
import com.android.tools.property.panel.impl.model.util.FakeTableLineModel
import com.android.tools.property.panel.impl.support.PropertiesTableImpl
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.google.common.truth.Truth.assertThat
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class InspectorTestUtil(
  projectRule: AndroidProjectRule,
  vararg tags: String,
  parentTag: String = "",
  fileName: String = "layout.xml",
) : SupportTestUtil(projectRule, *tags, parentTag = parentTag, fileName = fileName) {

  private val _properties: Table<String, String, NlPropertyItem> = HashBasedTable.create()

  val properties: PropertiesTable<NlPropertyItem> = PropertiesTableImpl(_properties)

  val editorProvider = FakeEditorProviderImpl(model)

  val inspector = FakeInspectorPanel()

  init {
    // Make sure the initial property load by model is done before replacing the properties in the
    // model:
    NlPropertiesModelTest.waitUntilLastSelectionUpdateCompleted(model)

    model.setPropertiesInTest(properties)
  }

  fun addProperty(namespace: String, name: String, type: NlPropertyType) {
    _properties.put(namespace, name, makeProperty(namespace, name, type))
  }

  fun addFlagsProperty(namespace: String, name: String, values: List<String>) {
    _properties.put(namespace, name, makeFlagsProperty(namespace, name, values))
  }

  fun removeProperty(namespace: String, name: String) {
    _properties.remove(namespace, name)
  }

  fun loadProperties() {
    val provider = NlPropertiesProvider(model.facet)
    for (propertyItem in provider.getProperties(model, null, components).values) {
      _properties.put(propertyItem.namespace, propertyItem.name, propertyItem)
    }
    model.setPropertiesInTest(properties)
  }

  fun checkTitle(line: Int, title: String) = inspector.checkTitle(line, title)

  fun checkTitle(line: Int, title: String, expandable: Boolean): FakeInspectorLineModel =
    inspector.checkTitle(line, title, expandable)

  fun checkEditor(line: Int, namespace: String, name: String) =
    inspector.checkEditor(line, namespace, name)

  fun checkTable(line: Int): FakeTableLineModel = inspector.checkTable(line)

  fun checkEmptyTableIndicator(line: Int): InspectorLineModel {
    assertThat(line).isLessThan(inspector.lines.size)
    assertThat(inspector.lines[line].type).isEqualTo(FakeLineType.PANEL)
    val lineModel = inspector.lines[line] as FakeComponentLineModel
    assertThat(lineModel.component).isInstanceOf(EmptyTablePanel::class.java)
    return lineModel
  }

  fun performAction(line: Int, action: Int, icon: Icon) =
    inspector.performAction(line, action, icon)
}

class FakeEditorProviderImpl(model: NlPropertiesModel) : EditorProvider<NlPropertyItem> {
  private val enumSupportProvider = NlEnumSupportProvider(model)
  private val controlTypeProvider = NlControlTypeProvider(enumSupportProvider)

  override fun createEditor(
    property: NlPropertyItem,
    context: EditorContext,
  ): Pair<PropertyEditorModel, JComponent> {
    val enumSupport = enumSupportProvider(property)

    return when (val type = controlTypeProvider(property)) {
      ControlType.COMBO_BOX -> ComboBoxPropertyEditorModel(property, enumSupport!!, true)
      ControlType.DROPDOWN -> ComboBoxPropertyEditorModel(property, enumSupport!!, false)
      ControlType.TEXT_EDITOR -> TextFieldPropertyEditorModel(property, true)
      ControlType.THREE_STATE_BOOLEAN -> ThreeStateBooleanPropertyEditorModel(property)
      ControlType.FLAG_EDITOR -> FlagPropertyEditorModel(property as FlagsPropertyItem<*>)
      ControlType.BOOLEAN -> BooleanPropertyEditorModel(property)
      ControlType.COLOR_EDITOR -> ColorFieldPropertyEditorModel(property)
      ControlType.LINK_EDITOR -> LinkPropertyEditorModel(property as LinkPropertyItem)
      else -> error("Unknown control type: $type")
    } to JPanel()
  }
}
