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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.argumentName
import com.android.tools.idea.naveditor.model.defaultValue
import com.android.tools.idea.naveditor.model.typeAttr
import com.android.tools.idea.naveditor.property.NavDestinationArgumentsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.HashBasedTable
import com.google.common.truth.Truth
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.*
import java.awt.Component
import java.awt.Container

class NavDestinationArgumentsInspectorProviderTest : NavTestCase() {
  fun testIsApplicable() {
    val provider = NavDestinationArgumentsInspectorProvider()
    val surface = mock(NavDesignSurface::class.java)
    val manager = NavPropertiesManager(myFacet, surface)
    Disposer.register(myRootDisposable, surface)
    Disposer.register(myRootDisposable, manager)
    val component1 = mock(NlComponent::class.java)
    val component2 = mock(NlComponent::class.java)
    val model = mock(NlModel::class.java)
    `when`(component1.model).thenReturn(model)
    `when`(component2.model).thenReturn(model)
    `when`(model.facet).thenReturn(myFacet)

    // Simple case: one component, arguments property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Arguments" to NavDestinationArgumentsProperty(listOf(component1))), manager))
    // One component, arguments + other property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Arguments" to NavDestinationArgumentsProperty(listOf(component1)), "foo" to mock(NlProperty::class.java)), manager))
    // Two components
    assertFalse(provider.isApplicable(listOf(component1, component2),
        mapOf("Arguments" to NavDestinationArgumentsProperty(listOf(component1, component2))), manager))
    // zero components
    assertFalse(provider.isApplicable(listOf(), mapOf("Arguments" to NavDestinationArgumentsProperty(listOf())), manager))
    // Non-arguments property only
    assertFalse(provider.isApplicable(listOf(component1), mapOf("foo" to mock(NlProperty::class.java)), manager))
  }

  fun testListContent() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          argument("arg1", type = "reference", value = "value1")
          argument("arg2", value = "value2")
        }
        fragment("f2")
        activity("activity")
      }
    }

    val manager = Mockito.mock(NavPropertiesManager::class.java)
    val navInspectorProviders = Mockito.spy(NavInspectorProviders(manager, myRootDisposable))
    Mockito.`when`(navInspectorProviders.providers).thenReturn(listOf(NavDestinationArgumentsInspectorProvider()))
    Mockito.`when`(manager.getInspectorProviders(ArgumentMatchers.any())).thenReturn(navInspectorProviders)
    Mockito.`when`(manager.facet).thenReturn(myFacet)

    val panel = NavInspectorPanel(myRootDisposable)
    panel.setComponent(listOf(model.find("f1")!!), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    val argumentList = flatten(panel).find { it.name == NAV_LIST_COMPONENT_NAME }!! as JBList<NlProperty>

    assertEquals(2, argumentList.itemsCount)
    val propertiesList = listOf(argumentList.model.getElementAt(0), argumentList.model.getElementAt(1))
    assertSameElements(propertiesList.map { it.name }, listOf("arg1: reference (value1)", "arg2: <inferred type> (value2)"))
  }

  fun testAddNew() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
      }
    }
    val fragment = model.find("f1")!!
    val dialog = spy(AddArgumentDialog(null, fragment))
    `when`(dialog.name).thenReturn("myArgument")
    doReturn("integer").`when`(dialog).type
    `when`(dialog.defaultValue).thenReturn("1234")
    doReturn(true).`when`(dialog).showAndGet()

    NavDestinationArgumentsInspectorProvider { _, _ -> dialog }.addItem(null, listOf(fragment), null)
    assertEquals(1, fragment.childCount)
    val argument = fragment.getChild(0)!!
    assertEquals(TAG_ARGUMENT, argument.tagName)
    assertEquals("myArgument", argument.argumentName)
    assertEquals("integer", argument.typeAttr)
    assertEquals("1234", argument.defaultValue)
    dialog.close(0)
  }

  fun testModify() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          argument("myArgument", type = "integer", value = "1234")
        }
      }
    }
    val fragment = model.find("f1")!!
    val dialog = spy(AddArgumentDialog(model.find("f1")!!.children[0], fragment))
    `when`(dialog.name).thenReturn("myArgument")
    doReturn("integer").`when`(dialog).type
    `when`(dialog.defaultValue).thenReturn("4321")
    doReturn(true).`when`(dialog).showAndGet()

    NavDestinationArgumentsInspectorProvider { _, _ -> dialog }.addItem(null, listOf(fragment), null)
    assertEquals(1, fragment.childCount)
    val argument = fragment.getChild(0)!!
    assertEquals(TAG_ARGUMENT, argument.tagName)
    assertEquals("myArgument", argument.argumentName)
    assertEquals("integer", argument.typeAttr)
    assertEquals("4321", argument.defaultValue)
    dialog.close(0)

  }

  fun testXmlFormatting() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
      }
    }
    val fragment = model.find("f1")!!
    val dialog = spy(AddArgumentDialog(null, fragment))
    `when`(dialog.name).thenReturn("a")
    doReturn(true).`when`(dialog).showAndGet()

    val navDestinationArgumentsInspectorProvider = NavDestinationArgumentsInspectorProvider { _, _ -> dialog }
    navDestinationArgumentsInspectorProvider.addItem(null, listOf(fragment), null)
    `when`(dialog.name).thenReturn("b")
    doReturn("integer").`when`(dialog).type
    navDestinationArgumentsInspectorProvider.addItem(null, listOf(fragment), null)
    FileDocumentManager.getInstance().saveAllDocuments()
    val result = String(model.virtualFile.contentsToByteArray())
    // Don't care about other contents or indent, but argument tags and attributes should be on their own lines.
    Truth.assertThat(result.replace("\n *".toRegex(), "\n")).contains("<argument android:name=\"a\" />\n" +
                                                                      "<argument\n" +
                                                                      "android:name=\"b\"\n" +
                                                                      "app:type=\"integer\" />\n")
    dialog.close(0)
  }
}

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}