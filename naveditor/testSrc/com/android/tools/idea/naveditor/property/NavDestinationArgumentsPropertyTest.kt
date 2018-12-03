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

class NavDestinationArgumentsPropertyTest : NavTestCase() {
  private lateinit var model: SyncNlModel

  override fun setUp() {
    super.setUp()
    model = model("nav.xml") {
      navigation {
        fragment("f1") {
          argument("arg1", type = "boolean", value = "true")
          argument("arg2", type = "custom.Parcelable", nullable = true, value = "@null")
        }
        fragment("f2") {
          argument("arg3")
        }
        fragment("f3")
      }
    }
  }

  fun testMultipleArguments() {
    val property = NavDestinationArgumentsProperty(listOf(model.find("f1")!!))
    property.refreshList()
    val arg1 = "arg1: boolean (true)"
    val arg2 = "arg2: custom.Parcelable? (@null)"
    assertSameElements(property.properties.keys(), listOf(arg1, arg2))
    assertSameElements(property.properties.values().map { it.name }, listOf(arg1, arg2))
  }

  fun testNoArguments() {
    val property = NavDestinationArgumentsProperty(listOf(model.find("f3")!!))
    assertTrue(property.properties.isEmpty)
  }

  fun testModify() {
    val fragment = model.find("f3")!!
    val property = NavDestinationArgumentsProperty(listOf(fragment))
    val argument = model.find { it.getAndroidAttribute(SdkConstants.ATTR_NAME) == "arg1" }!!
    fragment.addChild(argument)
    property.refreshList()
    assertEquals(argument, property.properties.values().first().components[0])
    fragment.removeChild(argument)
    property.refreshList()
    assertTrue(property.properties.isEmpty)
  }
}