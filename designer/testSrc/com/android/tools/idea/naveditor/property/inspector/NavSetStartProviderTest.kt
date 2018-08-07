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

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.SET_START_DESTINATION_PROPERTY_NAME
import com.google.common.collect.HashBasedTable
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.awt.Component
import java.awt.Container
import javax.swing.JButton

class NavSetStartProviderTest : NavTestCase() {

  private fun createModel() =
    model("nav.xml") {
      navigation("root", startDestination = "f1") {
        fragment("f1")
        navigation("subnav", startDestination = "activity") {
          fragment("f2")
          activity("activity")
        }
      }
    }

  fun testIsApplicable() {
    val model = createModel()
    val provider = NavSetStartProvider()
    assertFalse(isApplicable(provider, model, "root"))
    assertTrue(isApplicable(provider, model, "f1"))
    assertTrue(isApplicable(provider, model, "subnav"))
    assertTrue(isApplicable(provider, model, "f2"))
    assertFalse(isApplicable(provider, model, "activity"))
    assertFalse(isApplicable(provider, model, "f1", "f2"))
    assertFalse(isApplicable(provider, model))
  }

  private fun isApplicable(provider: NavSetStartProvider, model: NlModel, vararg ids: String) =
      provider.isApplicable(ids.map { model.find(it)!! }, mapOf(), mock(NavPropertiesManager::class.java))

  fun testButtonEnabled() {
    val model = createModel()
    val manager = Mockito.mock(NavPropertiesManager::class.java)
    val navInspectorProviders = Mockito.spy(NavInspectorProviders(manager, myRootDisposable))
    Mockito.`when`(navInspectorProviders.providers).thenReturn(listOf(NavSetStartProvider()))
    Mockito.`when`(manager.getInspectorProviders(ArgumentMatchers.any())).thenReturn(navInspectorProviders)
    Mockito.`when`(manager.facet).thenReturn(myFacet)

    val panel = NavInspectorPanel(myRootDisposable)
    val table = HashBasedTable.create<String, String, NlProperty>()

    panel.setComponent(listOf(model.find("f1")!!), table, manager)

    @Suppress("UNCHECKED_CAST")
    val button = flatten(panel).find { it.name == SET_START_DESTINATION_PROPERTY_NAME }!! as JButton

    assertFalse(button.isEnabled)

    panel.setComponent(listOf(model.find("subnav")!!), table, manager)
    assertTrue(button.isEnabled)

    panel.setComponent(listOf(model.find("f2")!!), table, manager)
    assertTrue(button.isEnabled)
  }
}

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}