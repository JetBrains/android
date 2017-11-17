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
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavigationTestCase
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.SET_START_DESTINATION_PROPERTY_NAME
import com.google.common.collect.HashBasedTable
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.awt.Component
import java.awt.Container
import javax.swing.JButton

class NavSetStartProviderTest : NavigationTestCase() {
  lateinit var myModel: SyncNlModel

  override fun setUp() {
    super.setUp()
    myModel = model("nav.xml",
        NavModelBuilderUtil.rootComponent("root")
            .withStartDestinationAttribute("f1")
            .unboundedChildren(
                NavModelBuilderUtil.fragmentComponent("f1"),
                NavModelBuilderUtil.navigationComponent("subnav")
                    .withStartDestinationAttribute("activity")
                    .unboundedChildren(
                        NavModelBuilderUtil.fragmentComponent("f2"),
                        NavModelBuilderUtil.activityComponent("activity"))))
        .build()
  }

  fun testIsApplicable() {
    val provider = NavSetStartProvider()
    assertFalse(isApplicable(provider, myModel, "root"))
    assertTrue(isApplicable(provider, myModel, "f1"))
    assertTrue(isApplicable(provider, myModel, "subnav"))
    assertTrue(isApplicable(provider, myModel, "f2"))
    assertTrue(isApplicable(provider, myModel, "activity"))
    assertFalse(isApplicable(provider, myModel, "f1", "f2"))
    assertFalse(isApplicable(provider, myModel))
  }

  private fun isApplicable(provider: NavSetStartProvider, model: NlModel, vararg ids: String) =
      provider.isApplicable(ids.map { model.find(it)!! }, mapOf(), mock(NavPropertiesManager::class.java))

  fun testButtonEnabled() {
    val manager = Mockito.mock(NavPropertiesManager::class.java)
    val navInspectorProviders = Mockito.spy(NavInspectorProviders(manager, myRootDisposable))
    Mockito.`when`(navInspectorProviders.providers).thenReturn(listOf(NavSetStartProvider()))
    Mockito.`when`(manager.getInspectorProviders(ArgumentMatchers.any())).thenReturn(navInspectorProviders)
    Mockito.`when`(manager.facet).thenReturn(myFacet)

    val panel = NavInspectorPanel(myRootDisposable)
    val table = HashBasedTable.create<String, String, NlProperty>()

    panel.setComponent(listOf(myModel.find("f1")!!), table, manager)

    @Suppress("UNCHECKED_CAST")
    val button = flatten(panel).find { it.name == SET_START_DESTINATION_PROPERTY_NAME }!! as JButton

    assertFalse(button.isEnabled)

    panel.setComponent(listOf(myModel.find("subnav")!!), table, manager)
    assertTrue(button.isEnabled)

    panel.setComponent(listOf(myModel.find("activity")!!), table, manager)
    assertFalse(button.isEnabled)

    panel.setComponent(listOf(myModel.find("f2")!!), table, manager)
    assertTrue(button.isEnabled)
  }
}

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}