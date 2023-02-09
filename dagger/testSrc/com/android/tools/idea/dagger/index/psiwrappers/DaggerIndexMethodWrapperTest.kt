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
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerIndexMethodWrapperTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun kotlinClassFunction() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      class Foo {
        fun bar(arg1: Integer, arg2: Bat = Bat()): Baz {} 
      }
      """.trimIndent()
      ) as
        KtFile

    val element = myFixture.moveCaret("b|ar").parentOfType<KtFunction>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("bar")
    assertThat(wrapper.getReturnType()?.getSimpleName()).isEqualTo("Baz")
    assertThat(wrapper.getParameters().map { it.getSimpleName() }).containsExactly("arg1", "arg2")
    assertThat(wrapper.getIsConstructor()).isFalse()
    assertThat(wrapper.getContainingClass()?.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun kotlinClassFunctionWithNoReturnType() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      class Foo {
        fun bar(arg1: Integer, arg2: Bat) = resultOfSomeFunction
      }
      """.trimIndent()
      ) as
        KtFile

    val element = myFixture.moveCaret("b|ar").parentOfType<KtFunction>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getReturnType()).isNull()
  }

  @Test
  fun kotlinClassFunctionWithAnnotations() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      class Foo {
        @Annotation1
        @Annotation2()
        @Annotation3(true)
        fun bar(arg1: Integer, arg2: Bat = Bat()): Baz {} 
      }
      """.trimIndent()
      ) as
        KtFile

    val element = myFixture.moveCaret("b|ar").parentOfType<KtFunction>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation1")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation2")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation3")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation4")).isFalse()
  }

  @Test
  fun kotlinPackageLevelFunction() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      fun bar(arg1: Integer, arg2: Bat): Baz {}
      """.trimIndent()
      ) as
        KtFile

    val element = myFixture.moveCaret("b|ar").parentOfType<KtFunction>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getContainingClass()).isNull()
  }

  @Test
  fun kotlinPrimaryConstructor() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      class Foo(arg1: Integer, arg2: Bat) {}
      """.trimIndent()
      ) as
        KtFile

    val element = myFixture.moveCaret("Foo(a|rg1").parentOfType<KtPrimaryConstructor>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Foo")
    assertThat(wrapper.getReturnType()).isNull()
    assertThat(wrapper.getParameters().map { it.getSimpleName() }).containsExactly("arg1", "arg2")
    assertThat(wrapper.getIsConstructor()).isTrue()
    assertThat(wrapper.getContainingClass()?.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun kotlinSecondaryConstructor() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      class Foo(arg1: Integer, arg2: Bat) {
        constructor(arg1: Integer)
      }
      """.trimIndent()
      ) as
        KtFile

    val element = myFixture.moveCaret("constr|uctor").parentOfType<KtSecondaryConstructor>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Foo")
    assertThat(wrapper.getReturnType()).isNull()
    assertThat(wrapper.getParameters().map { it.getSimpleName() }).containsExactly("arg1")
    assertThat(wrapper.getIsConstructor()).isTrue()
    assertThat(wrapper.getContainingClass()?.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun javaClassMethod() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;
      public class Foo {
        public Baz bar(int arg1, Bat arg2) {}
      }
      """.trimIndent()
      ) as
        PsiJavaFile

    val element = myFixture.moveCaret("b|ar").parentOfType<PsiMethod>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("bar")
    assertThat(wrapper.getReturnType()?.getSimpleName()).isEqualTo("Baz")
    assertThat(wrapper.getParameters().map { it.getSimpleName() }).containsExactly("arg1", "arg2")
    assertThat(wrapper.getIsConstructor()).isFalse()
    assertThat(wrapper.getContainingClass()?.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun javaClassMethodWithAnnotations() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;
      public class Foo {
        @Annotation1
        @Annotation2()
        @Annotation3(true)
        public Baz bar(int arg1, Bat arg2) {}
      }
      """.trimIndent()
      ) as
        PsiJavaFile

    val element = myFixture.moveCaret("b|ar").parentOfType<PsiMethod>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation1")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation2")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation3")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation4")).isFalse()
  }

  @Test
  fun javaConstructor() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;
      public class Foo {
        public Foo(int arg1, Bat arg2) {}
      }
      """.trimIndent()
      ) as
        PsiJavaFile

    val element = myFixture.moveCaret("Fo|o(int").parentOfType<PsiMethod>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Foo")
    assertThat(wrapper.getReturnType()).isNull()
    assertThat(wrapper.getParameters().map { it.getSimpleName() }).containsExactly("arg1", "arg2")
    assertThat(wrapper.getIsConstructor()).isTrue()
    assertThat(wrapper.getContainingClass()?.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }
}
