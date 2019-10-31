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
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.AnnotationSession

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
    val annotationHolder = AnnotationHolderImpl(AnnotationSession(myFixture.file))
    ProguardR8Annotator().annotate(element, annotationHolder)
    assertThat(annotationHolder).hasSize(0)

    element = myFixture.moveCaret("in|t foo")
    ProguardR8Annotator().annotate(element, annotationHolder)
    assertThat(annotationHolder).hasSize(1)
    val attr = annotationHolder[0]
    assertThat(annotationHolder[0].enforcedTextAttributes).isEqualTo(ProguardR8TextAttributes.KEYWORD.key.defaultAttributes)

    element = myFixture.moveCaret("int vo|id")
    ProguardR8Annotator().annotate(element, annotationHolder)
    assertThat(annotationHolder).hasSize(1)
    assertThat(annotationHolder[0]).isEqualTo(attr)
  }
}