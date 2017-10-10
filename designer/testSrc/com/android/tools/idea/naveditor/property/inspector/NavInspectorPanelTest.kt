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
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.uibuilder.property.NlProperties
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class NavInspectorPanelTest: NavigationTestCase() {
  fun testCollectProperties() {
    val model = model("nav.xml",
        rootComponent().unboundedChildren(
            fragmentComponent("f1")
                .withLayoutAttribute("activty_main")
                .unboundedChildren(actionComponent("a1").withDestinationAttribute("f2"),
                    actionComponent("a2").withDestinationAttribute("f3")),
            fragmentComponent("f2"),
            fragmentComponent("f3")))
        .build()
    val panel = NavInspectorPanel(testRootDisposable)
    val manager = spy(NavPropertiesManager(myFacet, model.surface))
    val inspectorProviders = mock(NavInspectorProviders::class.java)
    `when`(manager.getInspectorProviders(any() ?: testRootDisposable)).thenReturn(inspectorProviders)
    val components = listOf(model.find("f1")!!)
    panel.setComponent(components, NlProperties.getInstance().getProperties(myFacet, manager, model.components), manager)

    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    val propertyMap = captor.value
    assertEquals(model.find("a1"), propertyMap.get("@id/f2")!!.components[0])
    assertEquals(model.find("a2"), propertyMap.get("@id/f3")!!.components[0])
  }
}