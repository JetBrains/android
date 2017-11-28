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

import com.android.SdkConstants
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavigationTestCase
import org.mockito.Mockito.mock

class NavArgumentsPropertyTest : NavigationTestCase() {
  private lateinit var model: SyncNlModel

  override fun setUp() {
    super.setUp()
    model = model("nav.xml",
        NavModelBuilderUtil.rootComponent("root").unboundedChildren(
            NavModelBuilderUtil.fragmentComponent("f1")
                .unboundedChildren(
                    NavModelBuilderUtil.argumentComponent("arg1").withDefaultValueAttribute("val1"),
                    NavModelBuilderUtil.argumentComponent("arg2").withDefaultValueAttribute("val2")),
            NavModelBuilderUtil.fragmentComponent("f2")
                .unboundedChildren(NavModelBuilderUtil.argumentComponent("arg3")),
            NavModelBuilderUtil.fragmentComponent("f3")))
        .build()
  }

  fun testMultipleArguments() {
    val property = NavArgumentsProperty(listOf(model.find("f1")!!), mock(NavPropertiesManager::class.java))
    assertEquals(mapOf("arg1" to "val1", "arg2" to "val2", null to null),
        property.properties.associateBy({ it.value }, { it.defaultValueProperty.value }))
  }

  fun testNoArguments() {
    val property = NavArgumentsProperty(listOf(model.find("f3")!!), mock(NavPropertiesManager::class.java))
    assertEquals(mapOf(null to null), property.properties.associateBy({ it.value }, { it.defaultValueProperty.value }))
  }

  fun testModify() {
    val fragment = model.find("f3")!!
    val property = NavArgumentsProperty(listOf(fragment), mock(NavPropertiesManager::class.java))
    val argument = model.find { it.getAttribute(null, SdkConstants.ATTR_NAME) == "arg1" }!!
    fragment.addChild(argument)
    property.refreshList()
    assertEquals(argument, property.properties[0].components[0])
    fragment.removeChild(argument)
    property.refreshList()
    assertEquals(mapOf(null to null), property.properties.associateBy({ it.value }, { it.defaultValueProperty.value }))
  }
}