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
package com.android.tools.idea.naveditor.property

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.testFramework.UsefulTestCase
import org.mockito.Mockito

class NavActionArgumentsPropertyTest : NavTestCase() {
  private lateinit var model: SyncNlModel

  override fun setUp() {
    super.setUp()
    model = model("nav.xml",
        NavModelBuilderUtil.rootComponent("root").unboundedChildren(
            NavModelBuilderUtil.fragmentComponent("f1")
                .unboundedChildren(
                    NavModelBuilderUtil.argumentComponent("arg1").withDefaultValueAttribute("val1"),
                    NavModelBuilderUtil.argumentComponent("arg2").withDefaultValueAttribute("val2")),
            NavModelBuilderUtil.fragmentComponent("f3")
                .unboundedChildren(
                    NavModelBuilderUtil.actionComponent("a1").withDestinationAttribute("f1")
                        .unboundedChildren(NavModelBuilderUtil.argumentComponent("arg2").withDefaultValueAttribute("actionval2")),
                    NavModelBuilderUtil.actionComponent("a3").withDestinationAttribute("f3")
                )))
        .build()
  }

  fun testMultipleArguments() {
    val property = NavActionArgumentsProperty(listOf(model.find("a1")!!), Mockito.mock(NavPropertiesManager::class.java))
    assertEquals(mapOf("arg1" to null, "arg2" to "actionval2"),
        property.properties.associateBy({ it.value }, { it.defaultValueProperty.value }))
  }

  fun testNoArguments() {
    val property = NavActionArgumentsProperty(listOf(model.find("a3")!!), Mockito.mock(NavPropertiesManager::class.java))
    assertEmpty(property.properties)
  }

  fun testModify() {
    val fragment = model.find("f3")!!
    val property = NavActionArgumentsProperty(listOf(model.find("a3")!!), Mockito.mock(NavPropertiesManager::class.java))
    val argument = model.find("f1")!!.getChild(0)!!
    fragment.addChild(argument)
    property.refreshList()
    assertEquals("arg1", property.properties[0].value)
    assertEquals(null, property.properties[0].defaultValueProperty.value)
    fragment.removeChild(argument)
    property.refreshList()
    UsefulTestCase.assertEmpty(property.properties)
  }
}