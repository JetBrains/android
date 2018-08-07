/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.NavArgumentDefaultValuesProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.editors.TextEditor
import com.android.tools.idea.uibuilder.property.editors.NlTableCellEditor
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.table.JBTable
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE
import org.mockito.Mockito.mock
import java.awt.Component
import java.awt.Container

class NavArgumentDefaultValuesInspectorProviderTest : NavTestCase() {
  fun testIsApplicable() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("component1", destination = "f2")
        }
        fragment("f2") {
          action("component2", destination = "f1")
        }
        fragment("f3") {
          action("pop", popUpTo = "f1")
        }
      }
    }
    val provider = NavArgumentDefaultValuesInspectorProvider()
    val manager = NavPropertiesManager(myFacet, model.surface)
    val component1 = model.find("component1")!!
    val component2 = model.find("component2")!!
    val popAction = model.find("pop")!!

    // Simple case: one component, arguments property
    assertTrue(provider.isApplicable(listOf(component1),
                                     mapOf("Arguments" to NavArgumentDefaultValuesProperty(listOf(component1), manager)), manager))
    // One component, arguments + other property
    assertTrue(provider.isApplicable(listOf(component1),
                                     mapOf("Arguments" to NavArgumentDefaultValuesProperty(listOf(component1), manager),
                                           "foo" to mock(NlProperty::class.java)), manager))
    // Two components
    assertFalse(provider.isApplicable(listOf(component1, component2),
                                      mapOf("Arguments" to NavArgumentDefaultValuesProperty(listOf(component1, component2), manager)),
                                      manager))
    // zero components
    assertFalse(provider.isApplicable(listOf(), mapOf("Arguments" to NavArgumentDefaultValuesProperty(listOf(), manager)), manager))
    // Non-arguments property only
    assertFalse(provider.isApplicable(listOf(component1), mapOf("foo" to mock(NlProperty::class.java)), manager))
    // pop action with no explicit destination
    assertFalse(
      provider.isApplicable(listOf(popAction), mapOf("Arguments" to NavArgumentDefaultValuesProperty(listOf(component1), manager)),
                            manager))

    Disposer.dispose(manager)
  }

  fun testListContent() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          argument("arg1", value = "value1", type = "integer")
          argument("arg2", value = "value2")
        }
        fragment("f2") {
          action("a1", destination = "f1") {
            argument("arg1", value = "actionvalue1")
          }
          action("a2", destination = "activity")
        }
        activity("a2")
      }
    }

    val panel = NavInspectorPanel(myRootDisposable)
    val navPropertiesManager = NavPropertiesManager(myFacet, model.surface)
    panel.setComponent(listOf(model.find("a1")!!), HashBasedTable.create<String, String, NlProperty>(),
                       navPropertiesManager)

    @Suppress("UNCHECKED_CAST")
    val argumentsTable = flatten(panel).find { it.name == NAV_ACTION_ARGUMENTS_COMPONENT_NAME }!! as JBTable

    assertEquals(2, argumentsTable.rowCount)
    assertEquals(3, argumentsTable.columnCount)
    assertEquals("arg1", (argumentsTable.getValueAt(0, 0) as NlProperty).value)
    assertEquals("integer", (argumentsTable.getValueAt(0, 1) as NlProperty).value)
    assertEquals("actionvalue1", (argumentsTable.getValueAt(0, 2) as NlProperty).value)
    assertEquals("arg1", (argumentsTable.getValueAt(0, 0) as NlProperty).value)
    assertEquals(null, (argumentsTable.getValueAt(1, 1) as NlProperty).value)
    assertEquals(null, (argumentsTable.getValueAt(1, 2) as NlProperty).value)

    // edit the second default value
    setValue("foo", 1, 2, argumentsTable)

    assertEquals(2, argumentsTable.rowCount)
    assertEquals("foo", (argumentsTable.getValueAt(1, 2) as NlProperty).value)
    assertEquals(2, model.find("a1")!!.childCount)
    assertEquals("foo",
                 model.find("a1")!!
                   .children
                   .first { it.getAttribute(ANDROID_URI, ATTR_NAME) == "arg2" }
                   .getAttribute(ANDROID_URI, ATTR_DEFAULT_VALUE))

    // Now delete the first one
    setValue("", 0, 2, argumentsTable)

    assertEquals(1, model.find("a1")!!.childCount)
    assertEquals(2, argumentsTable.rowCount)
    assertEquals("arg1", (argumentsTable.getValueAt(0, 0) as NlProperty).value)
    assertEquals(null, (argumentsTable.getValueAt(0, 2) as NlProperty).value)
    assertEquals("arg2", (argumentsTable.getValueAt(1, 0) as NlProperty).value)
    assertEquals("foo", (argumentsTable.getValueAt(1, 2) as NlProperty).value)
    Disposer.dispose(navPropertiesManager)
  }

  private fun setValue(value: String, row: Int, column: Int, argumentsTable: JBTable) {
    val editor = argumentsTable.getCellEditor(row, column) as NlTableCellEditor
    argumentsTable.editCellAt(row, column)
    ApplicationManager.getApplication().runWriteAction { (editor.editor as TextEditor).setText(value) }
    editor.stopCellEditing()
  }
}

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}