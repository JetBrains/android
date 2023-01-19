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
package com.android.tools.idea.dagger.index.psiwrappers

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerIndexClassWrapperTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun kotlinClassWithPackage() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo {}
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<KtClass>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun kotlinClassWithoutPackage() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      class Foo {}
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<KtClass>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun kotlinClassWithAnnotations() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      @Annotation1
      @Annotation2()
      @Annotation3(true)
      class Foo {}
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<KtClass>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation1")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation2")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation3")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation4")).isFalse()
  }

  @Test
  fun javaClassWithPackage() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      public class Foo {}
      """.trimIndent()) as PsiJavaFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<PsiClass>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun javaClassWithoutPackage() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      public class Foo {}
      """.trimIndent()) as PsiJavaFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<PsiClass>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun javaClassWithAnnotations() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      @Annotation1
      @Annotation2()
      @Annotation3(true)
      public class Foo {}
      """.trimIndent()) as PsiJavaFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<PsiClass>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation1")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation2")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation3")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation4")).isFalse()
  }
}
