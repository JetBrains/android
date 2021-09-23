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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.CodeInsightTestUtil

class ProguardR8AnnotatorTest : ProguardR8TestCase() {
  fun testAnnotator() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class class.interface.myClass {
          int foo;
          int void;
        }
      """.trimIndent()
    )
    var element = myFixture.moveCaret("interf|ace")

    val annotations = CodeInsightTestUtil.testAnnotator(ProguardR8Annotator(), element)
    assertThat(annotations).hasSize(0)

    element = myFixture.moveCaret("in|t foo")
    val annotations2 = CodeInsightTestUtil.testAnnotator(ProguardR8Annotator(), element)
    assertThat(annotations2).hasSize(1)
    val attr = annotations2[0]
    assertThat(annotations2[0].enforcedTextAttributes).isEqualTo(ProguardR8TextAttributes.KEYWORD.key.defaultAttributes)

    element = myFixture.moveCaret("int vo|id")
    val annotations3 = CodeInsightTestUtil.testAnnotator(ProguardR8Annotator(), element)
    assertThat(annotations3).hasSize(1)
    assertThat(annotations3[0]).isEqualTo(attr)
  }
}