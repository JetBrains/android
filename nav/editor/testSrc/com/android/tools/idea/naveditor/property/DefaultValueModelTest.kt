/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.argumentName
import com.android.tools.idea.naveditor.property.ui.DefaultValueModel

class DefaultValueModelTest : NavTestCase() {
  fun testDefaultValueModelInherit() {
    val fragment1 = model.treeReader.find("fragment1")!!
    val action1 = model.treeReader.find("action1")!!
    val argument1 = fragment1.children.first { it.argumentName == "argument1" }

    val defaultValueModel = DefaultValueModel(argument1, action1)
    assertEquals(defaultValueModel, "argument1", "string", "")

    defaultValueModel.defaultValue = "bar"
    assertEquals(action1.children.size, 2)
    assertNotNull(action1.children.firstOrNull { it.argumentName == "argument1" })

    assertEquals(defaultValueModel, "argument1", "string", "bar")
  }

  fun testDefaultValueModelOverride() {
    val fragment1 = model.treeReader.find("fragment1")!!
    val action1 = model.treeReader.find("action1")!!
    val argument2 = fragment1.children.first { it.argumentName == "argument2" }

    val defaultValueModel = DefaultValueModel(argument2, action1)
    assertEquals(defaultValueModel, "argument2", "int", "15")

    defaultValueModel.defaultValue = "20"
    assertEquals(action1.children.size, 1)

    assertEquals(defaultValueModel, "argument2", "int", "20")
  }

  private val model by lazy {
    model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          argument("argument1", "string", value = "foo")
          argument("argument2", "int", value = "10")
        }
        action("action1", "fragment1") {
          argument("argument2", value = "15")
        }
      }
    }
  }

  private fun assertEquals(defaultValueModel: DefaultValueModel, name: String, type: String, defaultValue: String) {
    assertEquals(defaultValueModel.name, name)
    assertEquals(defaultValueModel.type, type)
    assertEquals(defaultValueModel.defaultValue, defaultValue)
  }
}
