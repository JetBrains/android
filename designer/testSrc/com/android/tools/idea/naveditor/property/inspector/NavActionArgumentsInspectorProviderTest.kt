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

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.NavActionArgumentsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.editors.TextEditor
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.uibuilder.property.editors.NlTableCellEditor
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.table.JBTable
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE
import org.mockito.Mockito.*
import java.awt.Component
import java.awt.Container

class NavActionArgumentsInspectorProviderTest: NavTestCase() {
  fun testIsApplicable() {
    val provider = NavActionArgumentsInspectorProvider()
    val surface = mock(NavDesignSurface::class.java)
    val manager = NavPropertiesManager(myFacet, surface)
    val component1 = mock(NlComponent::class.java)
    val component2 = mock(NlComponent::class.java)
    val model = mock(NlModel::class.java)
    `when`(component1.model).thenReturn(model)
    `when`(component2.model).thenReturn(model)
    `when`(model.facet).thenReturn(myFacet)

    // Simple case: one component, arguments property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Arguments" to NavActionArgumentsProperty(listOf(component1), manager)), manager))
    // One component, arguments + other property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Arguments" to NavActionArgumentsProperty(listOf(component1), manager), "foo" to mock(NlProperty::class.java)), manager))
    // Two components
    assertFalse(provider.isApplicable(listOf(component1, component2),
        mapOf("Arguments" to NavActionArgumentsProperty(listOf(component1, component2), manager)), manager))
    // zero components
    assertFalse(provider.isApplicable(listOf(), mapOf("Arguments" to NavActionArgumentsProperty(listOf(), manager)), manager))
    // Non-arguments property only
    assertFalse(provider.isApplicable(listOf(component1), mapOf("foo" to mock(NlProperty::class.java)), manager))
    Disposer.dispose(surface)
  }

  fun testListContent() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent("f1")
                .unboundedChildren(
                    argumentComponent("arg1").withDefaultValueAttribute("value1"),
                    argumentComponent("arg2").withDefaultValueAttribute("value2")),
            fragmentComponent("f2")
                .unboundedChildren(
                    actionComponent("a1").withDestinationAttribute("f1")
                        .unboundedChildren(
                            argumentComponent("arg1").withDefaultValueAttribute("actionvalue1")
                        ),
                    actionComponent("a2").withDestinationAttribute("activity")
                ),
            activityComponent("activity")))
        .build()

    val panel = NavInspectorPanel(myRootDisposable)
    panel.setComponent(listOf(model.find("a1")!!), HashBasedTable.create<String, String, NlProperty>(),
        NavPropertiesManager(myFacet, model.surface))

    @Suppress("UNCHECKED_CAST")
    val argumentsTable = flatten(panel).find { it.name == NAV_ACTION_ARGUMENTS_COMPONENT_NAME }!! as JBTable

    assertEquals(2, argumentsTable.rowCount)
    assertEquals(2, argumentsTable.columnCount)
    assertEquals("arg1", (argumentsTable.getValueAt(0, 0) as NlProperty).value)
    assertEquals("actionvalue1", (argumentsTable.getValueAt(0, 1) as NlProperty).value)
    assertEquals("arg2", (argumentsTable.getValueAt(1, 0) as NlProperty).value)
    assertEquals(null, (argumentsTable.getValueAt(1, 1) as NlProperty).value)

    // edit the second default value
    setValue("foo", 1, 1, argumentsTable)

    assertEquals(2, argumentsTable.rowCount)
    assertEquals("foo", (argumentsTable.getValueAt(1, 1) as NlProperty).value)
    assertEquals(2, model.find("a1")!!.childCount)
    assertEquals("foo",
        model.find("a1")!!
            .children
            .first { it.getAttribute(ANDROID_URI, ATTR_NAME) == "arg2" }
            .getAttribute(ANDROID_URI, ATTR_DEFAULT_VALUE))

    // Now delete the first one
    setValue("", 0, 1, argumentsTable)

    assertEquals(1, model.find("a1")!!.childCount)
    assertEquals(2, argumentsTable.rowCount)
    assertEquals("arg1", (argumentsTable.getValueAt(0, 0) as NlProperty).value)
    assertEquals(null, (argumentsTable.getValueAt(0, 1) as NlProperty).value)
    assertEquals("arg2", (argumentsTable.getValueAt(1, 0) as NlProperty).value)
    assertEquals("foo", (argumentsTable.getValueAt(1, 1) as NlProperty).value)
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