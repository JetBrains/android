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
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.uibuilder.property.NlProperties
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.google.common.collect.Table
import com.intellij.testFramework.UsefulTestCase
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
        rootComponent().unboundedChildren(
            fragmentComponent("f1")
                .unboundedChildren(actionComponent("a1").withDestinationAttribute("f2"),
                    actionComponent("a2").withDestinationAttribute("f3")),
            fragmentComponent("f2"),
            fragmentComponent("f3"),
            activityComponent("activity")))
        .build()
    panel = NavInspectorPanel(testRootDisposable)
    manager = spy(NavPropertiesManager(myFacet, model.surface))
    inspectorProviders = mock(NavInspectorProviders::class.java)
    `when`(manager.getInspectorProviders(any() ?: testRootDisposable)).thenReturn(inspectorProviders)
    properties = NlProperties.getInstance().getProperties(myFacet, manager, model.components)
  }

  fun testMultipleActions() {
    panel.setComponent(listOf(model.find("f1")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    UsefulTestCase.assertInstanceOf(captor.value["Actions"], NavActionsProperty::class.java)
  }

  fun testNoActions() {
    panel.setComponent(listOf(model.find("f2")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    UsefulTestCase.assertInstanceOf(captor.value["Actions"], NavActionsProperty::class.java)
  }

  fun testNoActionsInActivity() {
    panel.setComponent(listOf(model.find("activity")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertFalse(captor.value.containsKey("Actions"))
  }

  fun testNoActionsInAction() {
    panel.setComponent(listOf(model.find("a2")!!), properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertFalse(captor.value.containsKey("Actions"))
  }
}