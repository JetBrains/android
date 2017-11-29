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

import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavigationTestCase

class NavComponentTypePropertyTest : NavigationTestCase() {

  fun testValue() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent("f1")
                .unboundedChildren(actionComponent("a1").withDestinationAttribute("f1")),
            fragmentComponent("f2"),
            navigationComponent("nested").unboundedChildren(
                activityComponent("activity"))))
        .build()

    assertEquals("Fragment", NavComponentTypeProperty(listOf(model.find("f1")!!)).value)
    assertEquals("Action", NavComponentTypeProperty(listOf(model.find("a1")!!)).value)
    assertEquals("Fragment", NavComponentTypeProperty(listOf(model.find("f1")!!, model.find("f2")!!)).value)
    assertEquals("Multiple", NavComponentTypeProperty(listOf(model.find("f1")!!, model.find("activity")!!)).value)
    assertEquals("Multiple", NavComponentTypeProperty(listOf(model.find("f1")!!, model.find("a1")!!)).value)
    assertEquals("Root Graph", NavComponentTypeProperty(listOf(model.find("root")!!)).value)
    assertEquals("Nested Graph", NavComponentTypeProperty(listOf(model.find("nested")!!)).value)
  }
}
