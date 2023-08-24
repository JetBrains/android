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
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerIndexAnnotatedWrapperTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  private fun DaggerIndexAnnotatedWrapper.getAnnotationByNameTestHelper(
    annotation: DaggerAnnotation
  ): List<String> = getAnnotations(annotation).toList().map { it.getAnnotationNameInSource() }

  @Test
  fun kotlinAnnotation() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import dagger.*

      @Module
      @javax.inject.Inject
      class Foo {}
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isTrue()
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.INJECT)).isTrue()
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.COMPONENT)).isFalse()

    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.MODULE))
      .containsExactly("Module")
    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.INJECT))
      .containsExactly("javax.inject.Inject")
    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.COMPONENT)).isEmpty()
  }

  @Test
  fun kotlinAnnotationWithAlias() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import dagger.Module as Bar

      @Bar
      class Foo {}
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isTrue()
    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.MODULE))
      .containsExactly("Bar")
  }

  @Test
  fun kotlinMultipleAnnotations() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import dagger.Module

      @Module
      @dagger.Module
      class Foo {}
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isTrue()

    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.MODULE))
      .containsExactly("Module", "dagger.Module")
  }

  @Test
  fun kotlinInnerAnnotation() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import dagger.*
        import dagger.Component.Builder

        @dagger.Component.Builder
        @Component.Builder
        @Builder
        interface Foo {}
        """
          .trimIndent()
      ) as KtFile

    val element: KtClassOrObject = myFixture.findParentElement("interface Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.COMPONENT_BUILDER)).isTrue()
    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.COMPONENT_BUILDER))
      .containsExactly("dagger.Component.Builder", "Component.Builder", "Builder")
  }

  @Test
  fun javaAnnotation() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;

      import dagger.*;

      @Module
      class Foo {}
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isTrue()

    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.MODULE))
      .containsExactly("Module")
  }

  @Test
  fun javaFullyQualifiedAnnotation() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;

      @dagger.Module
      class Foo {}
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isTrue()

    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.MODULE))
      .containsExactly("dagger.Module")
  }

  @Test
  fun javaMultipleAnnotations() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;

      import dagger.Module;

      @Module
      @dagger.Module
      class Foo {}
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isTrue()

    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.MODULE))
      .containsExactly("Module", "dagger.Module")
  }

  @Test
  fun javaInnerAnnotation() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        import dagger.*;
        import dagger.Component.Builder;

        @Builder
        @Component.Builder
        @dagger.Component.Builder
        interface Foo {}
        """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("interface Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.COMPONENT_BUILDER)).isTrue()
    assertThat(wrapper.getAnnotationByNameTestHelper(DaggerAnnotation.COMPONENT_BUILDER))
      .containsExactly("Builder", "Component.Builder", "dagger.Component.Builder")
  }
}
