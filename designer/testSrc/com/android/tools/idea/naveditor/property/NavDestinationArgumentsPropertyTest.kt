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
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import org.mockito.Mockito.mock

class NavDestinationArgumentsPropertyTest : NavTestCase() {
  private lateinit var model: SyncNlModel

  override fun setUp() {
    super.setUp()
    model = model("nav.xml") {
      navigation {
        fragment("f1") {
          argument("arg1", "val1")
          argument("arg2", "val2")
        }
        fragment("f2") {
          argument("arg3")
        }
        fragment("f3")
      }
    }
  }

  fun testMultipleArguments() {
    val property = NavDestinationArgumentsProperty(listOf(model.find("f1")!!), mock(NavPropertiesManager::class.java))
    assertEquals(mapOf("arg1" to "val1", "arg2" to "val2", null to null),
        property.properties.associateBy({ it.value }, { it.defaultValueProperty.value }))
  }

  fun testNoArguments() {
    val property = NavDestinationArgumentsProperty(listOf(model.find("f3")!!), mock(NavPropertiesManager::class.java))
    assertEquals(mapOf(null to null), property.properties.associateBy({ it.value }, { it.defaultValueProperty.value }))
  }

  fun testModify() {
    val fragment = model.find("f3")!!
    val property = NavDestinationArgumentsProperty(listOf(fragment), mock(NavPropertiesManager::class.java))
    val argument = model.find { it.getAndroidAttribute(SdkConstants.ATTR_NAME) == "arg1" }!!
    fragment.addChild(argument)
    property.refreshList()
    assertEquals(argument, property.properties[0].components[0])
    fragment.removeChild(argument)
    property.refreshList()
    assertEquals(mapOf(null to null), property.properties.associateBy({ it.value }, { it.defaultValueProperty.value }))
  }
}