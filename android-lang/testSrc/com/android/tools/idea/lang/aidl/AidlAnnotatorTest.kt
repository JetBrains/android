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
package com.android.tools.idea.lang.aidl

import com.android.tools.idea.lang.aidl.highlight.AidlAnnotator
import com.android.tools.idea.lang.aidl.psi.AidlAnnotationElement
import com.android.tools.idea.lang.aidl.psi.AidlNamedElement
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.psi.util.findParentOfType
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase

class AidlAnnotatorTest : JavaCodeInsightFixtureAdtTestCase() {
  fun testAnnotator() {
    myFixture.configureByText(
      AidlFileType.INSTANCE,
      """
      package test.pkg;
      import android.os.IInterface;
      enum Test {
          FOO = 100,
          BAR = "test"
      }
      parcelable MyParcelable {
          IEmptyInterface iface;
      }
      interface IMyAidlInterface {
          const int TEST_CONSTANT = 4;
          void otherTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
                  @nullable double aDouble, String aString, inout List<String> list, String test);
      }
      """.trimIndent()
    )

    var element = myFixture.moveCaret("IMy|AidlInterface")
    var annotations = CodeInsightTestUtil.testAnnotator(AidlAnnotator(), element)
    assertThat(annotations).isEmpty()
    element = element.findParentOfType<AidlNamedElement>(false)!!
    annotations = CodeInsightTestUtil.testAnnotator(AidlAnnotator(), element)
    assertThat(annotations).hasSize(1)
    assertThat(annotations[0].textAttributes).isEqualTo(DefaultLanguageHighlighterColors.CLASS_NAME)

    // numbers
    element = myFixture.moveCaret("TEST_|CONSTANT")
    annotations = CodeInsightTestUtil.testAnnotator(AidlAnnotator(), element.parent)
    assertThat(annotations).hasSize(1)
    assertThat(annotations[0].textAttributes).isEqualTo(DefaultLanguageHighlighterColors.CONSTANT)

    // annotations
    element = myFixture.moveCaret("@nu|llable double aDouble")
    annotations = CodeInsightTestUtil.testAnnotator(AidlAnnotator(), element.findParentOfType<AidlAnnotationElement>()!!)
    assertThat(annotations).hasSize(1)
    assertThat(annotations[0].textAttributes).isEqualTo(DefaultLanguageHighlighterColors.METADATA)

    // method names
    element = myFixture.moveCaret("other|Types")
    annotations = CodeInsightTestUtil.testAnnotator(AidlAnnotator(), element.findParentOfType<AidlNamedElement>(false)!!)
    assertThat(annotations).hasSize(1)
    assertThat(annotations[0].textAttributes).isEqualTo(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)

    // enumerator names
    element = myFixture.moveCaret("F|OO")
    annotations = CodeInsightTestUtil.testAnnotator(AidlAnnotator(), element.findParentOfType<AidlNamedElement>(false)!!)
    assertThat(annotations).hasSize(1)
    assertThat(annotations[0].textAttributes).isEqualTo(DefaultLanguageHighlighterColors.INSTANCE_FIELD)

    // variable names
    element = myFixture.moveCaret("i|face")
    annotations = CodeInsightTestUtil.testAnnotator(AidlAnnotator(), element.findParentOfType<AidlNamedElement>(false)!!)
    assertThat(annotations).hasSize(1)
    assertThat(annotations[0].textAttributes).isEqualTo(DefaultLanguageHighlighterColors.INSTANCE_FIELD)

    // class name
    element = myFixture.moveCaret("My|Parcelable")
    annotations = CodeInsightTestUtil.testAnnotator(AidlAnnotator(), element.findParentOfType<AidlNamedElement>(false)!!)
    assertThat(annotations).hasSize(1)
    assertThat(annotations[0].textAttributes).isEqualTo(DefaultLanguageHighlighterColors.CLASS_NAME)
  }
}