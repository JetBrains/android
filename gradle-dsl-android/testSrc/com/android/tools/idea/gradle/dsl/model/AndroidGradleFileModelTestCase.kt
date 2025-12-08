/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.api.android.AndroidGradleDeclarativeBuildModel
import com.android.tools.idea.gradle.dsl.api.android.FlavorTypeModel.TypeNameValueElement

abstract class AndroidGradleFileModelTestCase: GradleFileModelTestCase("tools/adt/idea/gradle-dsl-android/testData/parser") {
  override fun getGradleDeclarativeBuildModel(): AndroidGradleDeclarativeBuildModel {
    return super.getGradleDeclarativeBuildModel() as AndroidGradleDeclarativeBuildModel
  }

  fun verifyFlavorType(message: String, expected: List<List<Any?>>, elements: List<TypeNameValueElement>?) {
    assertEquals(message, expected.size, elements!!.size)
    for (i in expected.indices) {
      val list = expected.get(i)
      val element: TypeNameValueElement = elements.get(i)
      GradleFileModelTestCase.assertEquals(message, list, element.getModel())
    }
  }
}