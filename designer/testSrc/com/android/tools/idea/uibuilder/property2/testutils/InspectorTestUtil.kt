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

import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.FlagPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.TextFieldPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.ThreeStateBooleanPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.PropertiesTableImpl
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NelePropertiesProvider
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.support.NeleControlTypeProvider
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.attrs.AttributeFormat
import javax.swing.JComponent
import javax.swing.JPanel

class InspectorTestUtil(projectRule: AndroidProjectRule, tag: String, parentTag: String = "")
  : SupportTestUtil(projectRule, tag, parentTag) {
  private val _properties: Table<String, String, NelePropertyItem> = HashBasedTable.create()

  var properties: PropertiesTable<NelePropertyItem> = PropertiesTableImpl(_properties)
    private set

  val editorProvider = FakeEditorProviderImpl()

  val inspector = FakeInspectorPanel()

  fun addProperty(namespace: String, name: String, type: NelePropertyType) {
    _properties.put(namespace, name, makeProperty(namespace, name, type))
  }

  fun addFlagsProperty(namespace: String, name: String, values: List<String>) {
    val definition = AttributeDefinition(name, null, null, listOf(AttributeFormat.Flags))
    values.forEach { definition.addValue(it) }
    _properties.put(namespace, name, makeFlagsProperty(namespace, definition))
  }

  fun removeProperty(namespace: String, name: String) {
    _properties.remove(namespace, name)
  }

  fun loadProperties() {
    val provider = NelePropertiesProvider(model)
    for (propertyItem in provider.getProperties(components).values) {
      _properties.put(propertyItem.namespace, propertyItem.name, propertyItem)
    }
  }
}

enum class LineType {
  TITLE, PROPERTY, TABLE, PANEL, SEPARATOR
}

class FakeInspectorLine(val type: LineType) : InspectorLineModel {
  override var visible = true
  override var hidden = false
  override var focusable = true
  override var parent: InspectorLineModel? = null
  var title: String? = null
  var editorModel: PropertyEditorModel? = null
  var tableModel: PTableModel? = null
  var expandable = false
  var expanded = false
  val children = mutableListOf<InspectorLineModel>()
  val childProperties: List<String>
    get() = children.map { it as FakeInspectorLine }.map { it.editorModel!!.property.name }

  var focusWasRequested = false
    private set

  var gotoNextLineWasRequested = false
    private set

  override fun requestFocus() {
    focusWasRequested = true
  }

  override var gotoNextLine: (InspectorLineModel) -> Unit
    get() = { gotoNextLineWasRequested = true }
    set(value) {}

  override fun makeExpandable(initiallyExpanded: Boolean) {
    expandable = true
    expanded = initiallyExpanded
  }

  override fun addChild(child: InspectorLineModel) {
    children.add(child)
  }
}

class FakeInspectorPanel : InspectorPanel {
  val lines = mutableListOf<FakeInspectorLine>()

  override fun addTitle(title: String): InspectorLineModel {
    val line = FakeInspectorLine(LineType.TITLE)
    line.title = title
    lines.add(line)
    return line
  }

  override fun addEditor(editorModel: PropertyEditorModel, editor: JComponent): InspectorLineModel {
    val line = FakeInspectorLine(LineType.PROPERTY)
    editorModel.lineModel = line
    line.editorModel = editorModel
    lines.add(line)
    return line
  }

  override fun addEditor(modelEditorPair: Pair<PropertyEditorModel, JComponent>): InspectorLineModel {
    return addEditor(modelEditorPair.first, modelEditorPair.second)
  }

  override fun addTable(tableModel: PTableModel, searchable: Boolean): InspectorLineModel {
    val line = FakeInspectorLine(LineType.TABLE)
    line.tableModel = tableModel
    lines.add(line)
    return line
  }

  override fun addComponent(component: JComponent): InspectorLineModel {
    val line = FakeInspectorLine(LineType.PANEL)
    lines.add(line)
    return line
  }
}

class FakeEditorProviderImpl: EditorProvider<NelePropertyItem> {
  private val enumSupportProvider = NeleEnumSupportProvider()
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
    }
  }
}
