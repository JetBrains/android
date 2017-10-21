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
import com.android.tools.idea.naveditor.NavigationTestCase
import com.android.tools.idea.naveditor.property.NavActionsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.util.Disposer
import org.mockito.Mockito.*

class NavigationActionsInspectorProviderTest: NavigationTestCase() {
  fun testIsApplicable() {
    val provider = NavigationActionsInspectorProvider()
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
        rootComponent().unboundedChildren(
            fragmentComponent("f1")
                .withLayoutAttribute("activty_main")
                .unboundedChildren(
                    actionComponent("a1").withDestinationAttribute("f2"),
                    actionComponent("a1").withDestinationAttribute("a1")),
            fragmentComponent("f2"),
            activityComponent("a1")))
        .build()

    val manager = mock(NavPropertiesManager::class.java)
    val navInspectorProviders = spy(NavInspectorProviders(manager, myRootDisposable))
    `when`(navInspectorProviders.providers).thenReturn(listOf(NavigationActionsInspectorProvider()))
    `when`(manager.getInspectorProviders(any())).thenReturn(navInspectorProviders)
    `when`(manager.facet).thenReturn(myFacet)

    val panel = NavInspectorPanel(myRootDisposable)
    panel.setComponent(listOf(model.find("f1")!!), HashBasedTable.create<String, String, NlProperty>(), manager)

    assertNotNull(panel.components[0])
  }
}