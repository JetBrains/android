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

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavigationTestCase
import com.android.tools.idea.naveditor.property.NavActionsProperty
import com.android.tools.idea.naveditor.property.NavArgumentsProperty
import com.android.tools.idea.naveditor.property.NavDeeplinkProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.uibuilder.property.NlProperties
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.google.common.collect.Table
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class NavInspectorPanelTest : NavigationTestCase() {
  private lateinit var model: SyncNlModel
  private lateinit var panel: NavInspectorPanel
  private lateinit var manager: NavPropertiesManager
  private lateinit var inspectorProviders: NavInspectorProviders
  private lateinit var properties: Table<String, String, NlPropertyItem>

  override fun setUp() {
    super.setUp()
    model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent("f1")
                .unboundedChildren(
                    actionComponent("a1").withDestinationAttribute("f2"),
                    actionComponent("a2").withDestinationAttribute("f3")),
            fragmentComponent("f2"),
            fragmentComponent("f3"),
            activityComponent("activity"),
            includeComponent("navigation"),
            navigationComponent("subnav")))
        .build()
    panel = NavInspectorPanel(testRootDisposable)
    manager = spy(NavPropertiesManager(myFacet, model.surface))
    inspectorProviders = mock(NavInspectorProviders::class.java)
    `when`(manager.getInspectorProviders(any() ?: testRootDisposable)).thenReturn(inspectorProviders)
    properties = NlProperties.getInstance().getProperties(myFacet, manager, model.components)
  }

  fun testMultipleFragments() {
    panel.setComponent(listOf(model.find("f1")!!, model.find("f2")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    // All the properties will be there, but the specific inspectors can decline to show if more than one is selected
    assertInstanceOf(captor.value["Actions"], NavActionsProperty::class.java)
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavArgumentsProperty::class.java)
  }

  fun testMultipleTypes() {
    panel.setComponent(listOf(model.find("a1")!!, model.find("nav")!!, model.find("activity")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    // Relevant properties will be there, but the specific inspectors can decline to show if different types are selected
    assertFalse(captor.value.containsKey("Actions"))
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavArgumentsProperty::class.java)
  }

  fun testInclude() {
    panel.setComponent(listOf(model.find("nav")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertFalse(captor.value.containsKey("Actions"))
    assertFalse(captor.value.containsKey("Deeplinks"))
    assertFalse(captor.value.containsKey("Arguments"))
  }

  fun testNested() {
    panel.setComponent(listOf(model.find("f2")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertInstanceOf(captor.value["Actions"], NavActionsProperty::class.java)
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavArgumentsProperty::class.java)
  }

  fun testActivity() {
    panel.setComponent(listOf(model.find("activity")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertFalse(captor.value.containsKey("Actions"))
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavArgumentsProperty::class.java)
  }

  fun testAction() {
    panel.setComponent(listOf(model.find("a2")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertFalse(captor.value.containsKey("Actions"))
    assertFalse(captor.value.containsKey("Deeplinks"))
    assertInstanceOf(captor.value["Arguments"], NavArgumentsProperty::class.java)
  }

  fun testFragment() {
    panel.setComponent(listOf(model.find("f3")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertInstanceOf(captor.value["Actions"], NavActionsProperty::class.java)
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavArgumentsProperty::class.java)
  }
}