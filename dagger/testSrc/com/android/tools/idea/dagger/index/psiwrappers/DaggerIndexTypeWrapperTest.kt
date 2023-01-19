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
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeReference
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerIndexTypeWrapperTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun kotlinUnqualifiedType() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      class Foo {
        fun bar(): Baz {} 
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Ba|z {}").parentOfType<KtTypeReference>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun kotlinUnqualifiedTypeWithImport() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import com.other.Baz

      class Foo {
        fun bar(): Baz {} 
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Ba|z {}").parentOfType<KtTypeReference>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun kotlinUnqualifiedTypeWithImportAlias() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import com.other.Aliased as Baz

      class Foo {
        fun bar(): Baz {} 
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Ba|z {}").parentOfType<KtTypeReference>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Aliased")
  }

  @Test
  fun kotlinQualifiedType() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo {
        fun bar(): com.other.Baz {} 
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Ba|z {}").parentOfType<KtTypeReference>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun kotlinInnerClassType() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import com.other.Baz

      class Foo {
        fun bar(): Baz.InnerClass {} 
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Ba|z.InnerClass {}").parentOfType<KtTypeReference>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("InnerClass")
  }

  @Test
  fun kotlinAliasedInnerClassType() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import com.other.Aliased as Baz

      class Foo {
        fun bar(): Baz.InnerClass {} 
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Ba|z.InnerClass {}").parentOfType<KtTypeReference>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("InnerClass")
  }

  @Test
  fun kotlinStarImportType() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import com.other.*

      class Foo {
        fun bar(): Baz {} 
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("Ba|z {}").parentOfType<KtTypeReference>()!!
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun javaUnqualifiedType() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      public class Foo {
        public Baz bar() {}
      }
      """.trimIndent()) as PsiJavaFile

    val element = myFixture.moveCaret("Ba|z bar()").parentOfType<PsiTypeElement>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun javaUnqualifiedTypeWithImport() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import com.other.Baz;
      
      public class Foo {
        public Baz bar() {}
      }
      """.trimIndent()) as PsiJavaFile

    val element = myFixture.moveCaret("Ba|z bar()").parentOfType<PsiTypeElement>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun javaQualifiedType() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      public class Foo {
        public com.other.Baz bar() {}
      }
      """.trimIndent()) as PsiJavaFile

    val element = myFixture.moveCaret("Ba|z bar()").parentOfType<PsiTypeElement>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun javaInnerClassType() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import com.other.Baz;
      
      public class Foo {
        public Baz.InnerClass bar() {}
      }
      """.trimIndent()) as PsiJavaFile

    val element = myFixture.moveCaret("Ba|z.InnerClass bar()").parentOfType<PsiTypeElement>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("InnerClass")
  }

  @Test
  fun javaStarImportType() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import com.other.*;

      public class Foo {
        public Baz bar() {}
      }
      """.trimIndent()) as PsiJavaFile

    val element = myFixture.moveCaret("Ba|z bar()").parentOfType<PsiTypeElement>()!!
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }
}
