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
package com.android.tools.idea.naveditor.property

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.inspector.DefaultValueInspectorBuilder
import com.android.tools.idea.naveditor.property.ui.DefaultValuePanel
import com.android.tools.idea.naveditor.property.ui.DefaultValueTableModel
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertiesProvider
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel

class DefaultValueInspectorBuilderTest : NavTestCase() {
  fun testValues() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          argument("argument1", "int", value = "10")
          argument("argument3", "float", value = "20f")
          argument("argument2", "string", value = "foo")
        }
        action("action1", "fragment1") {
          argument("argument1", value = "15")
        }
      }
    }

    val action1 = model.treeReader.find("action1")!!

    val propertiesModel = NlPropertiesModel(myRootDisposable, myFacet)
    val provider = NlPropertiesProvider(myFacet)
    val propertiesTable = provider.getProperties(propertiesModel, null, listOf(action1))
    val panel = FakeInspectorPanel()
    val builder = DefaultValueInspectorBuilder()
    builder.attachToInspector(panel, propertiesTable)

    val lineModel = panel.lines[1]
    val defaultValuePanel = lineModel.component as DefaultValuePanel
    val tableModel = defaultValuePanel.table.model as DefaultValueTableModel
    assertEquals(3, tableModel.rowCount)
    assertEquals(tableModel, 0, "argument1", "int", "15")
    assertEquals(tableModel, 1, "argument3", "float", "")
    assertEquals(tableModel, 2, "argument2", "string", "")
  }

  fun testUpdates() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          argument("argument1", "int", value = "10")
        }
        action("action1", "fragment1") {
          argument("argument1", value = "15")
        }
      }
    }

    val action1 = model.treeReader.find("action1")!!

    val propertiesModel = NlPropertiesModel(myRootDisposable, myFacet)
    val provider = NlPropertiesProvider(myFacet)
    val propertiesTable = provider.getProperties(propertiesModel, null, listOf(action1))
    val panel = FakeInspectorPanel()
    val builder = DefaultValueInspectorBuilder()
    builder.attachToInspector(panel, propertiesTable)

    val lineModel = panel.lines[1]
    val defaultValuePanel = lineModel.component as DefaultValuePanel
    val tableModel = defaultValuePanel.table.model as DefaultValueTableModel
    assertEquals(1, tableModel.rowCount)
    assertEquals(tableModel, 0, "argument1", "int", "15")

    action1.model.treeWriter.delete(listOf(action1.children[0]))
    lineModel.refresh()
    assertEquals(1, tableModel.rowCount)
    assertEquals(tableModel, 0, "argument1", "int", "")
  }

  private fun assertEquals(table: DefaultValueTableModel, row: Int, name: String, type: String, defaultValue: String) {
    assertEquals(table.getValueAt(row, 0), name)
    assertEquals(table.getValueAt(row, 1), type)
    assertEquals(table.getValueAt(row, 2), defaultValue)
  }
}

