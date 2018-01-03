/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.ext

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.*
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.apache.commons.io.FileUtils
import kotlin.test.assertNotEquals

class EmptyPropertyModelTest : GradleFileModelTestCase() {
  fun testEmptyProperty() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val model = extModel.findProperty("prop")
    assertEquals(NONE, model.valueType)
    assertEquals(REGULAR, model.propertyType)
    assertEquals(null, model.getValue(STRING_TYPE))
    assertEquals(null, model.getValue(BOOLEAN_TYPE))
    assertEquals(null, model.getValue(INTEGER_TYPE))
    assertEquals(null, model.getValue(MAP_TYPE))
    assertEquals(null, model.getValue(LIST_TYPE))
    assertEquals("prop", model.name)
    assertEquals("ext.prop", model.fullyQualifiedName)
    assertEquals(buildModel.virtualFile, model.gradleFile)

    assertEquals(null, model.getUnresolvedValue(STRING_TYPE))
    assertSize(0, model.dependencies)
  }

  fun testSetEmptyProperty() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val model = extModel.findProperty("prop")
    model.setValue(54)
    assertEquals(INTEGER, model.valueType)
    assertEquals(REGULAR, model.propertyType)
    assertEquals(54, model.getValue(INTEGER_TYPE))
    assertSize(0, model.dependencies)

    run {
      // Make sure we can change the value once
      val newModel = extModel.findProperty("prop1")
      newModel.setValue(true)
      assertEquals(BOOLEAN, newModel.valueType)
      assertEquals(REGULAR, newModel.propertyType)
      assertEquals(true, newModel.getValue(BOOLEAN_TYPE))
      assertSize(0, model.dependencies)

      // Check string injections still work correctly and make sure we can change the value twice
      newModel.setValue("\"Hello my value is ${'$'}{prop}\"")
      assertEquals(STRING, newModel.valueType)
      assertEquals(REGULAR, newModel.propertyType)
      assertEquals("Hello my value is 54", newModel.getValue(STRING_TYPE))
      assertEquals("Hello my value is ${'$'}{prop}", newModel.getUnresolvedValue(STRING_TYPE))
      assertSize(1, newModel.dependencies)
      assertEquals(model, newModel.dependencies[0])
    }

    // Check everything is still correct after a reparse
    applyChangesAndReparse(buildModel)

    val newExtModel = gradleBuildModel.ext()
    val reparsedModel = newExtModel.findProperty("prop1")
    assertEquals(STRING, reparsedModel.valueType)
    assertEquals(REGULAR, reparsedModel.propertyType)
    assertEquals("Hello my value is 54", reparsedModel.getValue(STRING_TYPE))
    assertEquals("Hello my value is ${'$'}{prop}", reparsedModel.getUnresolvedValue(STRING_TYPE))
    assertSize(1, reparsedModel.dependencies)
    assertEquals(newExtModel.findProperty("prop"), reparsedModel.dependencies[0])
  }

  fun testEmptyPropertyEquality() {
    val text = ""
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()
    val propModel = extModel.findProperty("prop")
    assertEquals(propModel, extModel.findProperty("prop"))
    assertNotEquals(propModel, extModel.findProperty("prop1"))
  }
}