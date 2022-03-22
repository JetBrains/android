/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.preview.util

import com.android.tools.idea.compose.preview.calcComposeElementsAffinity
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.util.PreviewConfiguration
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.tools.idea.preview.matchElementsToModels
import org.junit.Test
import kotlin.test.assertEquals

class UtilsTest {
  private val sharedPreviewConfig = PreviewConfiguration(21, null, 100, 100, "", 20.0f, 0, "")

  @Test
  fun testMatchElementsToModels() {
    val model1 = Any()
    val model2 = Any()
    val model3 = Any()

    val element1 = SingleComposePreviewElementInstance(
      "foo.foo",
      PreviewDisplaySettings("foo", null, false, false, null),
      null,
      null,
      sharedPreviewConfig
    )

    val element11 = SingleComposePreviewElementInstance(
      "foo.foo",
      PreviewDisplaySettings("foo", null, false, false, null),
      null,
      null,
      sharedPreviewConfig
    )

    val element2 = SingleComposePreviewElementInstance(
      "foo.bar",
      PreviewDisplaySettings("bar", null, false, false, null),
      null,
      null,
      sharedPreviewConfig
    )

    val element3 = SingleComposePreviewElementInstance(
      "foo.baz",
      PreviewDisplaySettings("baz", null, false, false, null),
      null,
      null,
      sharedPreviewConfig
    )

    val element31 = SingleComposePreviewElementInstance(
      "foo.baz",
      PreviewDisplaySettings("baz", null, true, false, null),
      null,
      null,
      sharedPreviewConfig
    )

    val element4 = SingleComposePreviewElementInstance(
      "foo.qwe",
      PreviewDisplaySettings("qwe", null, false, false, null),
      null,
      null,
      sharedPreviewConfig
    )

    run {
      val modelsToElements = mapOf(
        model1 to element3,
        model2 to element1,
        model3 to element2,
      )

      val result = matchElementsToModels(
        listOf(model1, model2, model3),
        listOf(element1, element2, element3),
        modelsToElements::get,
        ::calcComposeElementsAffinity)

      assertEquals(listOf(1, 2, 0), result)
    }

    run {
      val modelsToElements = mapOf(
        model1 to element1,
        model2 to element2,
        model3 to element3,
      )

      val result = matchElementsToModels<ComposePreviewElementInstance, Any>(
        listOf(model2, model3),
        listOf(element1, element2, element3),
        modelsToElements::get,
        ::calcComposeElementsAffinity
      )

      assertEquals(listOf(-1, 0, 1), result)
    }

    run {
      val modelsToElements = mapOf(
        model1 to element1,
        model2 to element2,
        model3 to element3,
      )

      val result = matchElementsToModels(
        listOf(model1, model2, model3),
        listOf(element2, element3),
        modelsToElements::get,
        ::calcComposeElementsAffinity)

      assertEquals(listOf(1, 2), result)
    }

    run {
      val modelsToElements = mapOf(
        model1 to element4,
        model2 to element31,
        model3 to element11,
      )

      val result = matchElementsToModels(
        listOf(model1, model2, model3),
        listOf(element1, element2, element3),
        modelsToElements::get,
        ::calcComposeElementsAffinity)

      assertEquals(listOf(2, 0, 1), result)
    }

    run {
      val modelsToElements = mapOf(
        model1 to element1,
        model2 to null
      )

      val result = matchElementsToModels(
        listOf(model1, model2),
        listOf(element1, element2),
        modelsToElements::get,
        ::calcComposeElementsAffinity)

      assertEquals(listOf(0, 1), result)
    }

    run {
      val result = matchElementsToModels(
        listOf<Any>(),
        listOf(element1),
        { null },
        ::calcComposeElementsAffinity)

      assertEquals(listOf(-1), result)
    }
  }
}