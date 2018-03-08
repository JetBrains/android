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

import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.support.EditorProviderImpl
import com.android.tools.idea.common.property2.impl.support.PropertiesTableImpl
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.support.ControlTypeProviderImpl
import com.android.tools.idea.uibuilder.property2.support.EnumSupportProviderImpl
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.intellij.openapi.Disposable
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.attrs.AttributeFormat
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito.*
import javax.swing.JComponent

class InspectorTestUtil(parent: Disposable, facet: AndroidFacet, fixture: JavaCodeInsightTestFixture,
                        tag: String, parentTag: String = ""): SupportTestUtil(parent, facet, fixture, tag, parentTag) {
  private val _properties: Table<String, String, NelePropertyItem> = HashBasedTable.create()

  val properties: PropertiesTable<NelePropertyItem> = PropertiesTableImpl(_properties)

  val enumSupportProvider = EnumSupportProviderImpl()
  val controlTypeProvider = ControlTypeProviderImpl()
  val formModel = mock(FormModel::class.java)
  val editorProvider = EditorProviderImpl(enumSupportProvider, controlTypeProvider, formModel)

  val inspector = MockInspectorPanel()

  fun addProperty(namespace: String, name: String, type: NelePropertyType) {
    _properties.put(namespace, name, makeProperty(namespace, name, type))
  }

  fun addFlagsProperty(namespace: String, name: String, values: List<String>) {
    val definition = AttributeDefinition(name, null, null, listOf(AttributeFormat.Flag))
    values.forEach { definition.addValue(it) }
    _properties.put(namespace, name, makeFlagsProperty(namespace, definition))
  }

  fun removeProperty(namespace: String, name: String) {
    _properties.remove(namespace, name)
  }
}

enum class LineType {
  TITLE, PROPERTY, PANEL, SEPARATOR
}

class MockInspectorLine(val type: LineType) : InspectorLineModel {
  override var visible = true
  override var hidden = false
  override var focusable = true
  override var focusRequest = false
  var title: String? = null
  var editorModel: PropertyEditorModel? = null
  var expandable = false
  var expanded = false
  val children = mutableListOf<InspectorLineModel>()
  val childProperties: List<String>
    get() = children.map { it as MockInspectorLine }.map { it.editorModel!!.property.name }

  override fun makeExpandable(initiallyExpanded: Boolean) {
    expandable = true
    expanded = initiallyExpanded
  }

  override fun addChild(child: InspectorLineModel) {
    children.add(child)
  }
}

class MockInspectorPanel : InspectorPanel {
  val lines = mutableListOf<MockInspectorLine>()

  override fun addTitle(title: String): InspectorLineModel {
    val line = MockInspectorLine(LineType.TITLE)
    line.title = title
    lines.add(line)
    return line
  }

  override fun addComponent(editorModel: PropertyEditorModel, editor: JComponent): InspectorLineModel {
    val line = MockInspectorLine(LineType.PROPERTY)
    editorModel.line = line
    line.editorModel = editorModel
    lines.add(line)
    return line
  }

  override fun addComponent(modelEditorPair: Pair<PropertyEditorModel, JComponent>): InspectorLineModel {
    return addComponent(modelEditorPair.first, modelEditorPair.second)
  }

  override fun addPanel(panel: JComponent): InspectorLineModel {
    val line = MockInspectorLine(LineType.PANEL)
    lines.add(line)
    return line
  }

  override fun addSeparator(): InspectorLineModel {
    val line = MockInspectorLine(LineType.SEPARATOR)
    lines.add(line)
    return line
  }
}
