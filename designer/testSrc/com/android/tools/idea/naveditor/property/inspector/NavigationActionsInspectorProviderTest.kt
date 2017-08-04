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

import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavigationTestCase
import com.android.tools.idea.naveditor.model.resolvedId
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.google.common.collect.HashBasedTable
import org.mockito.Mockito.*

class NavigationActionsInspectorProviderTest: NavigationTestCase() {
  fun testIsApplicable() {
    val model = model("nav.xml",
        rootComponent().unboundedChildren(
            fragmentComponent("f1")
                .unboundedChildren(actionComponent("a1").withDestinationAttribute("f2")),
            fragmentComponent("f2")
                .unboundedChildren(actionComponent("a2").withDestinationAttribute("f1")),
            fragmentComponent("f2"),
            activityComponent("a1"),
            navigationComponent("subnav"),
            includeComponent("navigation")))
        .build()

    val provider = NavigationActionsInspectorProvider()
    val propertes = mapOf<String, NlProperty>()
    val manager = NavPropertiesManager(myFacet, model.surface)
    assertTrue(provider.isApplicable(listOf(model.find("f1")!!), propertes, manager))
    assertTrue(provider.isApplicable(listOf(model.find("f2")!!), propertes, manager))
    assertFalse(provider.isApplicable(listOf(model.find("f1")!!, model.find("f2")!!), propertes, manager))
    assertFalse(provider.isApplicable(listOf(model.find("a1")!!), propertes, manager))
    assertTrue(provider.isApplicable(listOf(model.find("subnav")!!), propertes, manager))
    val navComponent = model.flattenComponents().filter({ c -> "nav" == c.resolvedId }).findFirst().orElse(null)
    assertFalse(provider.isApplicable(listOf(navComponent!!), propertes, manager))
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
    panel.setComponent(listOf(model.find("f1")), HashBasedTable.create<String, String, NlProperty>(), manager)

    assertNotNull(panel.components[0])
  }
}