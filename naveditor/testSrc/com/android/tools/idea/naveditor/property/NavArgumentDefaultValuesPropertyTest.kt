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
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.testFramework.UsefulTestCase
import org.mockito.Mockito

class NavArgumentDefaultValuesPropertyTest : NavTestCase() {
  private lateinit var model: SyncNlModel

  override fun setUp() {
    super.setUp()
    model("included.xml") {
      navigation("includedRoot", startDestination = "includedStart") {
        fragment("notStart") {
          argument("foo")
        }
        fragment("includedStart") {
          argument("bar")
          argument("baz", type="integer")
        }
      }
    }
    model = model("nav.xml") {
      navigation("root") {
        fragment("f1") {
          argument("arg1", value = "val1")
          argument("arg2", value = "val2")
        }
        fragment("f3") {
          action("a1", destination = "f1") {
            argument("arg2", value = "actionval2")
          }
          action("a3", destination = "f3")
          action("a4", destination = "subnav")
        }
        navigation("subnav", startDestination = "f4") {
          fragment("f4") {
            argument("subarg", "string")
            action("toInclude", destination = "includedRoot")
          }
          include("included")
        }
      }
    }
  }

  fun testMultipleArguments() {
    val property = NavArgumentDefaultValuesProperty(listOf(model.find("a1")!!), Mockito.mock(NavPropertiesManager::class.java))
    assertEquals(mapOf("arg1" to null, "arg2" to "actionval2"),
        property.properties.associateBy({ it.argName }, { it.value }))
  }

  fun testNoArguments() {
    val property = NavArgumentDefaultValuesProperty(listOf(model.find("a3")!!), Mockito.mock(NavPropertiesManager::class.java))
    assertEmpty(property.properties)
  }

  fun testModify() {
    val fragment = model.find("f3")!!
    val property = NavArgumentDefaultValuesProperty(listOf(model.find("a3")!!), Mockito.mock(NavPropertiesManager::class.java))
    val argument = model.find("f1")!!.getChild(0)!!
    fragment.addChild(argument)
    property.refreshList()
    assertEquals("arg1", property.properties[0].argName)
    assertEquals(null, property.properties[0].value)
    fragment.removeChild(argument)
    property.refreshList()
    UsefulTestCase.assertEmpty(property.properties)
  }

  fun testActionToSubnav() {
    val property = NavArgumentDefaultValuesProperty(listOf(model.find("a4")!!), Mockito.mock(NavPropertiesManager::class.java))
    assertEquals(listOf("subarg"), property.properties.map { it.argName })
  }

  fun testSubnavArgs() {
    val property = NavArgumentDefaultValuesProperty(listOf(model.find("subnav")!!), Mockito.mock(NavPropertiesManager::class.java))
    assertEquals(listOf("subarg"), property.properties.map { it.argName })
  }

  fun testInclude() {
    val property = NavArgumentDefaultValuesProperty(listOf(model.find("toInclude")!!), Mockito.mock(NavPropertiesManager::class.java))
    assertEquals(listOf("bar" to null, "baz" to "integer"), property.properties.map { it.argName to it.type })
  }
}