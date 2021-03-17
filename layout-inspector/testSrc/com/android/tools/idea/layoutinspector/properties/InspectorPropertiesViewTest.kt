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
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants.ANDROID_URI
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.PropertySection.DECLARED
import com.android.tools.idea.layoutinspector.properties.PropertyType.STRING
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.ui.ResolutionElementEditor
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel
import com.android.tools.property.panel.impl.model.util.FakeTableLineModel
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.testing.ApplicationRule
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.awt.Component

class InspectorPropertiesViewTest {
  @get:Rule
  val rule = ApplicationRule()

  @Test
  fun testResolutionElementEditorFromRendererCache() {
    val context = object : ViewNodeAndResourceLookup {
      override val resourceLookup: ResourceLookup = mock()
      override val selection: ViewNode? = null
      override val stats: SessionStatistics = mock()
      override fun get(id: Long): ViewNode? = null
    }
    val id = 3L
    val text = InspectorPropertyItem(ANDROID_URI, "text", STRING, "Hello", DECLARED, null, id, context)
    val prop = InspectorGroupPropertyItem(ANDROID_URI, "prop", STRING, "Value", null, DECLARED, null, id, context, listOf())
    val inspector = createInspector(listOf(text, prop))
    assertThat(inspector.lines).hasSize(5)
    val declared = inspector.lines[2] as FakeTableLineModel
    declared.checkItemCount(2)
    // Regression test for: b/182947968
    // It used to be that property with resolution stack would show up as a simple PropertyTextField editor if a prior property
    // of the same control type was rendered first.
    assertThat(declared.getComponentFor(text)).isInstanceOf(PropertyTextField::class.java)
    assertThat(declared.getComponentFor(prop)).isInstanceOf(ResolutionElementEditor::class.java)
  }

  private fun createInspector(properties: List<InspectorPropertyItem>): FakeInspectorPanel {
    val table = HashBasedTable.create<String, String, InspectorPropertyItem>()
    properties.forEach { table.addProperty(it) }
    val propertiesModel = InspectorPropertiesModel()
    val propertiesView = InspectorPropertiesView(propertiesModel)
    val inspector = FakeInspectorPanel()
    val tab = propertiesView.tabs.single()
    propertiesModel.properties = PropertiesTable.create(table)
    tab.attachToInspector(inspector)
    return inspector
  }

  private fun Table<String, String, InspectorPropertyItem>.addProperty(property: InspectorPropertyItem) =
    this.put(property.namespace, property.name, property)

  private fun FakeTableLineModel.getComponentFor(property: InspectorPropertyItem): Component? {
    val table: PTable = mock()
    val renderProvider = tableUI.tableCellRendererProvider.invoke(table, property, PTableColumn.VALUE)
    val component = renderProvider.getEditorComponent(table, property, PTableColumn.VALUE, 0, false, false, false)
    return component?.components?.single()
  }
}
