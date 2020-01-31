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
package com.android.tools.idea.naveditor.property2

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property2.inspector.DefaultValueInspectorBuilder
import com.android.tools.idea.naveditor.property2.ui.DefaultValuePanel
import com.android.tools.idea.naveditor.property2.ui.DefaultValueTableModel
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertiesProvider
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel

class DefaultValueInspectorBuilderTest : NavTestCase() {
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

    val action1 = model.find("action1")!!

    val propertiesModel = NelePropertiesModel(myRootDisposable, myFacet)
    val provider = NelePropertiesProvider(myFacet)
    val propertiesTable = provider.getProperties(propertiesModel, null, listOf(action1))
    val panel = FakeInspectorPanel()
    val builder = DefaultValueInspectorBuilder()
    builder.attachToInspector(panel, propertiesTable)

    val lineModel = panel.lines[1]
    val defaultValuePanel = lineModel.component as DefaultValuePanel
    val tableModel = defaultValuePanel.table.model as DefaultValueTableModel
    assertEquals(1, tableModel.rowCount)
    assertEquals(tableModel, "15")

    action1.model.delete(listOf(action1.children[0]))
    lineModel.refresh()
    assertEquals(1, tableModel.rowCount)
    assertEquals(tableModel, "")
  }

  private fun assertEquals(table: DefaultValueTableModel, defaultValue: String) {
    assertEquals(table.getValueAt(0, 0), "argument1")
    assertEquals(table.getValueAt(0, 1), "int")
    assertEquals(table.getValueAt(0, 2), defaultValue)
  }
}

