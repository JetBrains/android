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
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.*
import com.android.tools.idea.uibuilder.property.NlProperties
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertDoesntContain
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class NavInspectorPanelTest : NavTestCase() {
  private lateinit var model: SyncNlModel
  private lateinit var panel: NavInspectorPanel
  private lateinit var manager: NavPropertiesManager
  private lateinit var inspectorProviders: NavInspectorProviders

  override fun setUp() {
    super.setUp()
    model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", destination = "f2")
          action("a2", destination = "f3")
        }
        fragment("f2")
        fragment("f3")
        activity("activity")
        include("navigation")
        navigation("subnav")
      }
    }
    panel = NavInspectorPanel(testRootDisposable)
    val realPropertiesManager = NavPropertiesManager(myFacet, model.surface)
    manager = spy(realPropertiesManager)
    Disposer.register(myRootDisposable, realPropertiesManager)
    inspectorProviders = mock(NavInspectorProviders::class.java)
    `when`(manager.getInspectorProviders(any() ?: testRootDisposable)).thenReturn(inspectorProviders)

    // hack: make AttributeProcessingUtil.getNamespaceKeyByResourcePackage give us the right namespace
    myFacet.properties.ALLOW_USER_CONFIGURATION = false
  }

  fun testMultipleFragments() {
    val components = listOf(model.find("f1")!!, model.find("f2")!!)
    val properties = NlProperties.getInstance().getProperties(myFacet, manager, components)
    panel.setComponent(components, properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    // All the properties will be there, but the specific inspectors can decline to show if more than one is selected
    assertInstanceOf(captor.value["Actions"], NavActionsProperty::class.java)
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavDestinationArgumentsProperty::class.java)
    assertInstanceOf(captor.value[SET_START_DESTINATION_PROPERTY_NAME], SetStartDestinationProperty::class.java)
  }

  fun testMultipleTypes() {
    val components = listOf(model.find("a1")!!, model.find("nav")!!, model.find("activity")!!)
    val properties = NlProperties.getInstance().getProperties(myFacet, manager, components)
    panel.setComponent(components, properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    // Relevant properties will be there, but the specific inspectors can decline to show if different types are selected
    assertFalse(captor.value.containsKey("Actions"))
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavArgumentsProperty::class.java)
    assertInstanceOf(captor.value[SET_START_DESTINATION_PROPERTY_NAME], SetStartDestinationProperty::class.java)
  }

  fun testInclude() {
    val components = listOf(model.find("nav")!!)
    val properties = NlProperties.getInstance().getProperties(myFacet, manager, components)
    panel.setComponent(components, properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertFalse(captor.value.containsKey("Actions"))
    assertFalse(captor.value.containsKey("Deeplinks"))
    assertFalse(captor.value.containsKey("Arguments"))
    assertInstanceOf(captor.value[SET_START_DESTINATION_PROPERTY_NAME], SetStartDestinationProperty::class.java)
    validateProperties("include", captor.value.keys)
  }

  fun testNested() {
    val components = listOf(model.find("subnav")!!)
    val properties = NlProperties.getInstance().getProperties(myFacet, manager, components)
    panel.setComponent(components, properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertInstanceOf(captor.value["Actions"], NavActionsProperty::class.java)
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavDestinationArgumentsProperty::class.java)
    assertInstanceOf(captor.value[SET_START_DESTINATION_PROPERTY_NAME], SetStartDestinationProperty::class.java)
    validateProperties("navigation", captor.value.keys)
  }

  fun testActivity() {
    val components = listOf(model.find("activity")!!)
    val properties = NlProperties.getInstance().getProperties(myFacet, manager, components)
    panel.setComponent(components, properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertFalse(captor.value.containsKey("Actions"))
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavDestinationArgumentsProperty::class.java)
    assertInstanceOf(captor.value[SET_START_DESTINATION_PROPERTY_NAME], SetStartDestinationProperty::class.java)
    validateProperties("activity", captor.value.keys)
  }

  fun testAction() {
    val components = listOf(model.find("a2")!!)
    val properties = NlProperties.getInstance().getProperties(myFacet, manager, components)
    panel.setComponent(components, properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertFalse(captor.value.containsKey("Actions"))
    assertFalse(captor.value.containsKey("Deeplinks"))
    assertInstanceOf(captor.value["Arguments"], NavActionArgumentsProperty::class.java)
    assertFalse(captor.value.containsKey(SET_START_DESTINATION_PROPERTY_NAME))
    validateProperties("action", captor.value.keys)
  }

  fun testFragment() {
    val components = listOf(model.find("f3")!!)
    val properties = NlProperties.getInstance().getProperties(myFacet, manager, components)
    panel.setComponent(components, properties, manager)
    @Suppress("UNCHECKED_CAST")
    val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, NlProperty>>
    verify(inspectorProviders).createInspectorComponents(any(), captor.capture(), any())
    assertInstanceOf(captor.value["Actions"], NavActionsProperty::class.java)
    assertInstanceOf(captor.value["Deeplinks"], NavDeeplinkProperty::class.java)
    assertInstanceOf(captor.value["Arguments"], NavDestinationArgumentsProperty::class.java)
    assertInstanceOf(captor.value[SET_START_DESTINATION_PROPERTY_NAME], SetStartDestinationProperty::class.java)
    validateProperties("fragment", captor.value.keys)
  }
}

private val typeToProperty: Multimap<String, String> = createPropertyMap()

private fun createPropertyMap(): Multimap<String, String> {
  val map: Multimap<String, String> = HashMultimap.create()
  map.putAll("activity", listOf("name", "action", "data", "dataPattern"))
  map.putAll("fragment", listOf("name"))
  map.putAll("navigation", listOf("startDestination"))
  map.putAll("include", listOf("graph"))
  map.keySet().forEach { map.putAll(it, listOf("id", "label"))}

  map.putAll("action", listOf("id", "destination", "launchSingleTop", "launchDocument", "clearTask", "popUpTo",
      "popUpToInclusive", "enterAnim", "exitAnim"))
  return map
}

private fun validateProperties(type: String, properties: Set<String>) {
  assertContainsElements(properties, typeToProperty[type])
  val others = typeToProperty.values() - typeToProperty[type]
  assertDoesntContain(properties, others)
}
