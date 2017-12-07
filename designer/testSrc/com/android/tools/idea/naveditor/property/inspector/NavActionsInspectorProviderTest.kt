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
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.NavActionsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import org.mockito.Mockito.*
import java.awt.Component
import java.awt.Container

class NavActionsInspectorProviderTest : NavTestCase() {
  fun testIsApplicable() {
    val provider = NavActionsInspectorProvider()
    val surface = mock(NavDesignSurface::class.java)
    val manager = NavPropertiesManager(myFacet, surface)
    val component1 = mock(NlComponent::class.java)
    val component2 = mock(NlComponent::class.java)
    // Simple case: one component, actions property
    assertTrue(provider.isApplicable(listOf(component1), mapOf("Actions" to NavActionsProperty(listOf(component1))), manager))
    // One component, actions + other property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Actions" to NavActionsProperty(listOf(component1)), "foo" to mock(NlProperty::class.java)), manager))
    // Two components
    assertFalse(provider.isApplicable(listOf(component1, component2),
        mapOf("Actions" to NavActionsProperty(listOf(component1, component2))), manager))
    // zero components
    assertFalse(provider.isApplicable(listOf(), mapOf("Actions" to NavActionsProperty(listOf())), manager))
    // Non-actions property only
    assertFalse(provider.isApplicable(listOf(component1), mapOf("foo" to mock(NlProperty::class.java)), manager))
    Disposer.dispose(surface)
  }

  fun testListContent() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent("f1")
                .unboundedChildren(
                    actionComponent("a1").withDestinationAttribute("f2"),
                    actionComponent("a2").withDestinationAttribute("activity")),
            fragmentComponent("f2"),
            activityComponent("activity")))
        .build()

    val manager = mock(NavPropertiesManager::class.java)
    val navInspectorProviders = spy(NavInspectorProviders(manager, myRootDisposable))
    `when`(navInspectorProviders.providers).thenReturn(listOf(NavActionsInspectorProvider()))
    `when`(manager.getInspectorProviders(any())).thenReturn(navInspectorProviders)
    `when`(manager.facet).thenReturn(myFacet)

    val panel = NavInspectorPanel(myRootDisposable)
    panel.setComponent(listOf(model.find("f1")!!), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    val actionsList = flatten(panel).find { it.name == NAV_LIST_COMPONENT_NAME }!! as JBList<NlProperty>

    assertEquals(2, actionsList.itemsCount)
    val propertiesList = listOf(actionsList.model.getElementAt(0), actionsList.model.getElementAt(1))
    assertSameElements(propertiesList.map { it.components[0].id }, listOf("a1", "a2"))
    assertSameElements(propertiesList.map { it.name }, listOf("f2", "activity"))
  }
}

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}