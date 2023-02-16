/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger

import com.android.tools.idea.dagger.DaggerRelatedItemLineMarkerProviderV2.Companion.canReceiveLineMarker
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerRelatedItemLineMarkerProviderV2Test {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun tooltipProvider() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      class Fo<caret>o
      """
        .trimIndent()
    )

    val tooltip = DaggerRelatedItemLineMarkerProviderV2.tooltipProvider(myFixture.elementAtCaret)
    assertThat(tooltip).isEqualTo("Dependency Related Files for Foo")
  }

  @Test
  fun canReceiveLineMarker_kotlin() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      class Foo constructor() {
        val property: Int = 0
      }
      """
        .trimIndent()
    )

    assertThat(myFixture.moveCaret("cla|ss Foo constructor").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("class Fo|o constructor").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture
          .moveCaret("class Fo|o constructor")
          .parentOfType<KtClass>()
          ?.canReceiveLineMarker()
      )
      .isFalse()
    assertThat(myFixture.moveCaret("class Foo constr|uctor").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture
          .moveCaret("class Foo constr|uctor")
          .parentOfType<KtFunction>()
          ?.canReceiveLineMarker()
      )
      .isFalse()

    assertThat(myFixture.moveCaret("va|l property: Int = 0").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("val prop|erty: Int = 0").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture
          .moveCaret("val prop|erty: Int = 0")
          .parentOfType<KtProperty>()
          ?.canReceiveLineMarker()
      )
      .isFalse()
    assertThat(myFixture.moveCaret("val property: In|t = 0").canReceiveLineMarker()).isTrue()
    assertThat(myFixture.moveCaret("val property: Int = |0").canReceiveLineMarker()).isFalse()
  }

  @Test
  fun canReceiveLineMarker_java() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      public class Foo {
        public Foo() {}

        private int property = 0;
      }
      """
        .trimIndent()
    )

    assertThat(myFixture.moveCaret("cla|ss Foo").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("class Fo|o").canReceiveLineMarker()).isTrue()
    assertThat(myFixture.moveCaret("class Fo|o").parentOfType<PsiClass>()?.canReceiveLineMarker())
      .isFalse()

    assertThat(myFixture.moveCaret("pub|lic Foo()").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("public Fo|o()").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture.moveCaret("public Fo|o()").parentOfType<PsiMethod>()?.canReceiveLineMarker()
      )
      .isFalse()

    assertThat(myFixture.moveCaret("pri|vate int property = 0;").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("private in|t property = 0;").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("private int pro|perty = 0;").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture
          .moveCaret("private int pro|perty = 0;")
          .parentOfType<PsiField>()
          ?.canReceiveLineMarker()
      )
      .isFalse()
    assertThat(myFixture.moveCaret("private int property = |0;").canReceiveLineMarker()).isFalse()
  }
}
