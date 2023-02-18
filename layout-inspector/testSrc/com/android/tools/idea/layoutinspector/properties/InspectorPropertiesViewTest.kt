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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.FLAG_IS_INLINED
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.model.packageNameHash
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterItem
import com.android.tools.idea.layoutinspector.properties.PropertySection.DECLARED
import com.android.tools.idea.layoutinspector.properties.PropertySection.DEFAULT
import com.android.tools.idea.layoutinspector.properties.PropertySection.LAYOUT
import com.android.tools.idea.layoutinspector.properties.PropertySection.MERGED
import com.android.tools.idea.layoutinspector.properties.PropertySection.PARAMETERS
import com.android.tools.idea.layoutinspector.properties.PropertySection.RECOMPOSITIONS
import com.android.tools.idea.layoutinspector.properties.PropertySection.UNMERGED
import com.android.tools.idea.layoutinspector.properties.PropertyType.DIMENSION
import com.android.tools.idea.layoutinspector.properties.PropertyType.FLOAT
import com.android.tools.idea.layoutinspector.properties.PropertyType.INT32
import com.android.tools.idea.layoutinspector.properties.PropertyType.STRING
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.ui.ResolutionElementEditor
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.impl.model.util.FakeInspectorLineModel
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel
import com.android.tools.property.panel.impl.model.util.FakeTableLineModel
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableColumn
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.ClassRule
import org.junit.Test
import java.awt.Component
import javax.swing.JTextArea

private val EXAMPLE = packageNameHash("com.example.myexampleapp")

class InspectorPropertiesViewTest {
  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @Test
  fun testResolutionElementEditorFromRendererCache() {
    val context = object : ViewNodeAndResourceLookup {
      override val resourceLookup: ResourceLookup = mock()
      override val selection: ViewNode? = null
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

  @Test
  fun testPropertiesShowUpInCorrectTable() {
    val context = object : ViewNodeAndResourceLookup {
      override val resourceLookup: ResourceLookup = mock()
      override val selection: ViewNode? = null
      override fun get(id: Long): ViewNode? = null
    }
    val id = 3L
    val text = InspectorPropertyItem(ANDROID_URI, "text", STRING, "Hello", DECLARED, null, id, context)
    val width = InspectorPropertyItem(ANDROID_URI, "layout_width", DIMENSION, "2", LAYOUT, null, id, context)
    val alpha = InspectorPropertyItem(ANDROID_URI, "alpha", FLOAT, "0.5", DEFAULT, null, id, context)
    val param = ParameterItem("modifier", STRING, "", PARAMETERS, id, context, -1, 0)
    val semantic1 = ParameterItem("Text", STRING, "Hello", MERGED, id, context, -1, 0)
    val semantic2 = ParameterItem("ContentDescription", STRING, "Hello", UNMERGED, id, context, -1, 0)
    val counts = InspectorPropertyItem(NAMESPACE_INTERNAL, "count", INT32, "7", RECOMPOSITIONS, null, id, context)
    val skips = InspectorPropertyItem(NAMESPACE_INTERNAL, "skips", INT32, "14", RECOMPOSITIONS, null, id, context)
    val inspector = createInspector(listOf(text, width, alpha, param, semantic1, semantic2, counts, skips))
    assertThat(inspector.lines).hasSize(13)
    checkStandardSections(inspector, text, width, alpha, param, semantic1, semantic2)
  }

  @Test
  fun testPropertiesShowUpInCorrectTableWhenShowingRecompositionCounts() {
    val context = object : ViewNodeAndResourceLookup {
      override val resourceLookup: ResourceLookup = mock()
      override val selection: ViewNode? = null
      override fun get(id: Long): ViewNode? = null
    }
    val id = 3L
    val text = InspectorPropertyItem(ANDROID_URI, "text", STRING, "Hello", DECLARED, null, id, context)
    val width = InspectorPropertyItem(ANDROID_URI, "layout_width", DIMENSION, "2", LAYOUT, null, id, context)
    val alpha = InspectorPropertyItem(ANDROID_URI, "alpha", FLOAT, "0.5", DEFAULT, null, id, context)
    val param = ParameterItem("modifier", STRING, "", PARAMETERS, id, context, -1, 0)
    val semantic1 = ParameterItem("Text", STRING, "Hello", MERGED, id, context, -1, 0)
    val semantic2 = ParameterItem("ContentDescription", STRING, "Hello", UNMERGED, id, context, -1, 0)
    val counts = InspectorPropertyItem(NAMESPACE_INTERNAL, "count", INT32, "7", RECOMPOSITIONS, null, id, context)
    val skips = InspectorPropertyItem(NAMESPACE_INTERNAL, "skips", INT32, "14", RECOMPOSITIONS, null, id, context)
    val propertiesView = createView(listOf(text, width, alpha, param, semantic1, semantic2, counts, skips), ::showRecompositions)
    val propertiesModel = propertiesView.model as InspectorPropertiesModel
    var inspector = FakeInspectorPanel()
    val tab = propertiesView.tabs.single()

    // Check that all sections are available including the Recomposition section
    tab.attachToInspector(inspector)
    assertThat(inspector.lines).hasSize(15)
    checkStandardSections(inspector, text, width, alpha, param, semantic1, semantic2)
    assertThat(inspector.lines[13].title).isEqualTo("Recomposition")
    assertTable(inspector.lines[14], counts, skips)

    // Check that the Recomposition section is omitted if showRecompositions is off
    val treeSettings = propertiesModel.layoutInspector!!.treeSettings
    treeSettings.showRecompositions = false
    inspector = FakeInspectorPanel()
    tab.attachToInspector(inspector)
    assertThat(inspector.lines).hasSize(13)
    checkStandardSections(inspector, text, width, alpha, param, semantic1, semantic2)

    // Check that the Recomposition section is omitted if the recomposition numbers are reset
    treeSettings.showRecompositions = true
    val model = propertiesModel.layoutInspector!!.inspectorModel
    model.maxRecomposition.reset()
    inspector = FakeInspectorPanel()
    tab.attachToInspector(inspector)
    assertThat(inspector.lines).hasSize(13)
    checkStandardSections(inspector, text, width, alpha, param, semantic1, semantic2)
  }

  @Test
  fun testInlinedComposable() {
    val model = model {
      view(ROOT) {
        compose(VIEW1, "MyApplicationTheme") {
          compose(VIEW2, "Column", composeFlags = FLAG_IS_INLINED, composePackageHash = EXAMPLE) {
            compose(VIEW3, "Text", composePackageHash = EXAMPLE)
          }
        }
      }
    }
    model.setSelection(model[VIEW2], SelectionOrigin.INTERNAL)
    val x = InspectorPropertyItem(NAMESPACE_INTERNAL, "x", DIMENSION, "0", PropertySection.DIMENSION, null, VIEW2, model)
    val y = InspectorPropertyItem(NAMESPACE_INTERNAL, "y", DIMENSION, "0", PropertySection.DIMENSION, null, VIEW2, model)
    val propertiesView = createView(listOf(x, y), model = model)
    val inspector = FakeInspectorPanel()
    val tab = propertiesView.tabs.single()

    // Check that only the dimensions and the empty Parameters section is available
    tab.attachToInspector(inspector)
    assertThat(inspector.lines).hasSize(3)
    assertThat(inspector.lines[1].title).isEqualTo("Parameters")
    val component = inspector.lines[2].component as? JTextArea
    assertThat(component?.text).isEqualTo(
      "The selected composable is inlined. Parameters are not available at this time for inline composables in the Layout Inspector.")
  }

  private fun checkStandardSections(
    inspector: FakeInspectorPanel,
    text: InspectorPropertyItem,
    width: InspectorPropertyItem,
    alpha: InspectorPropertyItem,
    param: ParameterItem,
    semantic1: ParameterItem,
    semantic2: ParameterItem
  ) {
    assertThat(inspector.lines[1].title).isEqualTo("Declared Attributes")
    assertTable(inspector.lines[2], text)
    assertThat(inspector.lines[3].title).isEqualTo("Layout")
    assertTable(inspector.lines[4], width)
    assertThat(inspector.lines[5].title).isEqualTo("All Attributes")
    assertTable(inspector.lines[6], alpha, width, text)
    assertThat(inspector.lines[7].title).isEqualTo("Parameters")
    assertTable(inspector.lines[8], param)
    assertThat(inspector.lines[9].title).isEqualTo("Merged Semantics")
    assertTable(inspector.lines[10], semantic1)
    assertThat(inspector.lines[11].title).isEqualTo("Declared Semantics")
    assertTable(inspector.lines[12], semantic2)
  }

  private fun createView(
    properties: List<InspectorPropertyItem>,
    customize: (InspectorPropertiesModel) -> Unit = {},
    model: InspectorModel = model {}
  ): InspectorPropertiesView {
    val table = HashBasedTable.create<String, String, InspectorPropertyItem>()
    properties.forEach { table.addProperty(it) }
    val propertiesModel = InspectorPropertiesModel()
    val propertiesView = InspectorPropertiesView(propertiesModel)
    propertiesModel.properties = PropertiesTable.create(table)
    val settings = FakeTreeSettings()
    val client: InspectorClient = mock()
    whenever(client.stats).thenReturn(mock())
    val layoutInspector = LayoutInspector(mock(), mock(), client, model, settings)
    propertiesModel.layoutInspector = layoutInspector
    customize(propertiesModel)
    return propertiesView
  }

  private fun createInspector(
    properties: List<InspectorPropertyItem>,
    customize: (InspectorPropertiesModel) -> Unit = {}
  ): FakeInspectorPanel {
    val propertiesView = createView(properties, customize)
    val inspector = FakeInspectorPanel()
    val tab = propertiesView.tabs.single()
    tab.attachToInspector(inspector)
    return inspector
  }

  private fun showRecompositions(propertiesModel: InspectorPropertiesModel) {
    val model = propertiesModel.layoutInspector!!.inspectorModel
    model.maxRecomposition.count = 7
    model.maxRecomposition.skips = 14
    val settings = propertiesModel.layoutInspector!!.treeSettings
    settings.showRecompositions = true
  }

  private fun Table<String, String, InspectorPropertyItem>.addProperty(property: InspectorPropertyItem) =
    this.put(property.namespace, property.name, property)

  private fun FakeTableLineModel.getComponentFor(property: InspectorPropertyItem): Component? {
    val table: PTable = mock()
    val renderProvider = tableUI.tableCellRendererProvider.invoke(table, property, PTableColumn.VALUE)
    val component = renderProvider.getEditorComponent(table, property, PTableColumn.VALUE, 0, false, false, false)
    return component?.components?.single()
  }

  private fun assertTable(line: FakeInspectorLineModel, vararg items: InspectorPropertyItem) {
    val table = line as FakeTableLineModel
    table.checkItemCount(items.size)
    items.forEachIndexed { i, item -> table.checkItem(i, item.namespace, item.name) }
  }
}
