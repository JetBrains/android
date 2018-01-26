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
package com.android.tools.idea.gradle.dsl.model.util

import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.util.removeListValue
import com.android.tools.idea.gradle.dsl.api.util.replaceListValue
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase

class GradlePropertyUtilTest : GradleFileModelTestCase() {
  fun testReplaceListValue() {
    val text = """
               ext {
                 prop1 = [1, 2, 2, 4]
                 prop2 = ["a", 'b', "b", 'd']
                 prop3 = [true, true, false, true]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(firstModel, listOf(1, 2, 2, 4), REGULAR, 0)

      firstModel.replaceListValue(2, 3)
      firstModel.replaceListValue(2, 3)
      firstModel.replaceListValue(4, 7)

      verifyListProperty(firstModel, listOf(1, 3, 3, 7), REGULAR, 0)

      val secondModel = buildModel.ext().findProperty("prop2")
      verifyListProperty(secondModel, listOf("a", "b", "b", "d"), REGULAR, 0)

      secondModel.replaceListValue("d", "a")

      verifyListProperty(secondModel, listOf("a", "b", "b", "a"), REGULAR, 0)

      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyListProperty(thirdModel, listOf(true, true, false, true), REGULAR, 0)

      thirdModel.replaceListValue(false, true)

      verifyListProperty(thirdModel, listOf(true, true, true, true), REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(firstModel, listOf(1, 3, 3, 7), REGULAR, 0)
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyListProperty(secondModel, listOf("a", "b", "b", "a"), REGULAR, 0)
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyListProperty(thirdModel, listOf(true, true, true, true), REGULAR, 0)
    }
  }

  fun testReplaceListValueOnNoneList() {
    val text = """
               ext {
                 prop1 = [key1: 'value1', key2: false, key3: 17]
                 prop2 = "hello"
                 prop3 = prop1 // Should only work for resolved properties.
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      try {
        firstModel.replaceListValue("value1", "newValue")
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }

      val secondModel = buildModel.ext().findProperty("prop2")
      try {
        secondModel.replaceListValue("hello", "goodbye")
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }

      val thirdModel = buildModel.ext().findProperty("prop3")
      try {
        thirdModel.replaceListValue(1, 0)
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }
    }
  }

  fun testRemoveListValues() {
    val text = """
               ext {
                 prop1 = [1, 2]
                 prop2 = ["a", 'b', "b", 'd']
                 prop3 = [true, true, false, true]
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      firstModel.removeListValue(1)
      firstModel.removeListValue(2)

      val secondModel = buildModel.ext().findProperty("prop2")
      secondModel.removeListValue("b")

      val thirdModel = buildModel.ext().findProperty("prop3")
      thirdModel.removeListValue(true)
      thirdModel.removeListValue(true)

      verifyListProperty(firstModel, listOf(), REGULAR, 0)
      verifyListProperty(secondModel, listOf("a", "b", "d"), REGULAR, 0)
      verifyListProperty(thirdModel, listOf(false, true), REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      val secondModel = buildModel.ext().findProperty("prop2")
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyListProperty(firstModel, listOf(), REGULAR, 0)
      verifyListProperty(secondModel, listOf("a", "b", "d"), REGULAR, 0)
      verifyListProperty(thirdModel, listOf(false, true), REGULAR, 0)
    }
  }
}