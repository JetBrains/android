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
package com.android.tools.idea.dagger.index.concepts

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerElementTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  private val mockKtConstructorIdentifier: DaggerElementIdentifier<KtConstructor<*>> = mock()
  private val mockKtFunctionIdentifier: DaggerElementIdentifier<KtFunction> = mock()
  private val mockKtParameterIdentifier: DaggerElementIdentifier<KtParameter> = mock()
  private val mockKtPropertyIdentifier: DaggerElementIdentifier<KtProperty> = mock()
  private val mockPsiFieldIdentifier: DaggerElementIdentifier<PsiField> = mock()
  private val mockPsiMethodIdentifier: DaggerElementIdentifier<PsiMethod> = mock()
  private val mockPsiParameterIdentifier: DaggerElementIdentifier<PsiParameter> = mock()

  private val mockIdentifiers = DaggerElementIdentifiers(
    listOf(mockKtConstructorIdentifier),
    listOf(mockKtFunctionIdentifier),
    listOf(mockKtParameterIdentifier),
    listOf(mockKtPropertyIdentifier),
    listOf(mockPsiFieldIdentifier),
    listOf(mockPsiMethodIdentifier),
    listOf(mockPsiParameterIdentifier),
  )

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun getPsiType_ktClass() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Fo<caret>o
      """.trimIndent())

    val psiType = myFixture.elementAtCaret.parentOfType<KtClass>(/* withSelf = */ true)!!.getPsiType()
    assertThat(psiType.canonicalText).isEqualTo("com.example.Foo")
  }

  @Test
  fun getPsiType_ktFunction() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo

      fun someFun<caret>ction() = Foo()
      """.trimIndent())

    val psiType = myFixture.elementAtCaret.parentOfType<KtFunction>(/* withSelf = */ true)!!.getPsiType()
    assertThat(psiType.canonicalText).isEqualTo("com.example.Foo")
  }

  @Test
  fun getPsiType_ktConstructor() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo constructo<caret>r() {}
      """.trimIndent())

    val psiType = myFixture.elementAtCaret.parentOfType<KtConstructor<*>>(/* withSelf = */ true)!!.getPsiType()
    assertThat(psiType.canonicalText).isEqualTo("com.example.Foo")
  }

  @Test
  fun getPsiType_ktParameter() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo

      fun someFunction(fo<caret>o: Foo) {}
      """.trimIndent())

    val psiType = myFixture.elementAtCaret.parentOfType<KtParameter>(/* withSelf = */ true)!!.getPsiType()
    assertThat(psiType.canonicalText).isEqualTo("com.example.Foo")
  }

  @Test
  fun getPsiType_ktProperty() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo

      class Bar {
        val f<caret>oo: Foo
      }
      """.trimIndent())

    val psiType = myFixture.elementAtCaret.parentOfType<KtProperty>(/* withSelf = */ true)!!.getPsiType()
    assertThat(psiType.canonicalText).isEqualTo("com.example.Foo")
  }

  @Test
  fun getPsiType_psiClass() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      public class Fo<caret>o {}
      """.trimIndent())

    val psiType = myFixture.elementAtCaret.parentOfType<PsiClass>(/* withSelf = */ true)!!.getPsiType()
    assertThat(psiType.canonicalText).isEqualTo("com.example.Foo")
  }

  @Test
  fun getPsiType_psiField() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      public class Foo {}

      public class Bar {
        public Foo fo<caret>o;
      }
      """.trimIndent())

    val psiType = myFixture.elementAtCaret.parentOfType<PsiField>(/* withSelf = */ true)!!.getPsiType()
    assertThat(psiType.canonicalText).isEqualTo("com.example.Foo")
  }

  @Test
  fun getPsiType_psiMethod() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      public class Foo {}

      public class Bar {
        public Foo fo<caret>o() { return new Foo(); }
      }
      """.trimIndent())

    val psiType = myFixture.elementAtCaret.parentOfType<PsiMethod>(/* withSelf = */ true)!!.getPsiType()
    assertThat(psiType.canonicalText).isEqualTo("com.example.Foo")
  }

  @Test
  fun getPsiType_psiParameter() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      public class Foo {}

      public class Bar {
        public void foo(Foo f<caret>oo) {}
      }
      """.trimIndent())

    val psiType = myFixture.elementAtCaret.parentOfType<PsiParameter>(/* withSelf = */ true)!!.getPsiType()
    assertThat(psiType.canonicalText).isEqualTo("com.example.Foo")
  }

  @Test
  fun getPsiType_unknownType() {
    val psiFile: PsiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      public class Foo {}
      """.trimIndent())

    assertThrows(IllegalArgumentException::class.java) { psiFile.getPsiType() }
  }

  @Test
  fun getDaggerElement_ktFunction() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo

      fun someFun<caret>ction() = Foo()
      """.trimIndent())

    val psiElement = myFixture.elementAtCaret.parentOfType<KtFunction>(/* withSelf = */ true)!!

    val mockDaggerElement: DaggerElement = mock()
    whenever(mockKtFunctionIdentifier.getDaggerElement(any())).thenReturn(mockDaggerElement)

    assertThat(mockIdentifiers.getDaggerElement(psiElement)).isSameAs(mockDaggerElement)

    verify(mockKtConstructorIdentifier, never()).getDaggerElement(any())
    verify(mockKtFunctionIdentifier, times(1)).getDaggerElement(any())
    verify(mockKtParameterIdentifier, never()).getDaggerElement(any())
    verify(mockKtPropertyIdentifier, never()).getDaggerElement(any())
    verify(mockPsiFieldIdentifier, never()).getDaggerElement(any())
    verify(mockPsiMethodIdentifier, never()).getDaggerElement(any())
    verify(mockPsiParameterIdentifier, never()).getDaggerElement(any())
  }

  @Test
  fun getDaggerElement_ktConstructorWithConstructorHandler() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo construct<caret>or() {}
      """.trimIndent())

    val psiElement = myFixture.elementAtCaret.parentOfType<KtConstructor<*>>(/* withSelf = */ true)!!

    val mockDaggerElement: DaggerElement = mock()
    whenever(mockKtConstructorIdentifier.getDaggerElement(any())).thenReturn(mockDaggerElement)

    assertThat(mockIdentifiers.getDaggerElement(psiElement)).isSameAs(mockDaggerElement)

    verify(mockKtConstructorIdentifier, times(1)).getDaggerElement(any())
    verify(mockKtFunctionIdentifier, never()).getDaggerElement(any())
    verify(mockKtParameterIdentifier, never()).getDaggerElement(any())
    verify(mockKtPropertyIdentifier, never()).getDaggerElement(any())
    verify(mockPsiFieldIdentifier, never()).getDaggerElement(any())
    verify(mockPsiMethodIdentifier, never()).getDaggerElement(any())
    verify(mockPsiParameterIdentifier, never()).getDaggerElement(any())
  }

  @Test
  fun getDaggerElement_ktConstructorWithFunctionHandler() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo construct<caret>or() {}
      """.trimIndent())

    val psiElement = myFixture.elementAtCaret.parentOfType<KtConstructor<*>>(/* withSelf = */ true)!!

    val mockDaggerElement: DaggerElement = mock()
    whenever(mockKtFunctionIdentifier.getDaggerElement(any())).thenReturn(mockDaggerElement)

    assertThat(mockIdentifiers.getDaggerElement(psiElement)).isSameAs(mockDaggerElement)

    verify(mockKtConstructorIdentifier, times(1)).getDaggerElement(any())
    verify(mockKtFunctionIdentifier, times(1)).getDaggerElement(any())
    verify(mockKtParameterIdentifier, never()).getDaggerElement(any())
    verify(mockKtPropertyIdentifier, never()).getDaggerElement(any())
    verify(mockPsiFieldIdentifier, never()).getDaggerElement(any())
    verify(mockPsiMethodIdentifier, never()).getDaggerElement(any())
    verify(mockPsiParameterIdentifier, never()).getDaggerElement(any())
  }

  @Test
  fun getDaggerElement_ktParameter() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo

      fun someFunction(fo<caret>o: Foo) {}
      """.trimIndent())

    val psiElement = myFixture.elementAtCaret.parentOfType<KtParameter>(/* withSelf = */ true)!!

    val mockDaggerElement: DaggerElement = mock()
    whenever(mockKtParameterIdentifier.getDaggerElement(any())).thenReturn(mockDaggerElement)

    assertThat(mockIdentifiers.getDaggerElement(psiElement)).isSameAs(mockDaggerElement)

    verify(mockKtConstructorIdentifier, never()).getDaggerElement(any())
    verify(mockKtFunctionIdentifier, never()).getDaggerElement(any())
    verify(mockKtParameterIdentifier, times(1)).getDaggerElement(any())
    verify(mockKtPropertyIdentifier, never()).getDaggerElement(any())
    verify(mockPsiFieldIdentifier, never()).getDaggerElement(any())
    verify(mockPsiMethodIdentifier, never()).getDaggerElement(any())
    verify(mockPsiParameterIdentifier, never()).getDaggerElement(any())
  }

  @Test
  fun getDaggerElement_ktProperty() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo

      class Bar {
        val f<caret>oo: Foo
      }
      """.trimIndent())

    val psiElement = myFixture.elementAtCaret.parentOfType<KtProperty>(/* withSelf = */ true)!!

    val mockDaggerElement: DaggerElement = mock()
    whenever(mockKtPropertyIdentifier.getDaggerElement(any())).thenReturn(mockDaggerElement)

    assertThat(mockIdentifiers.getDaggerElement(psiElement)).isSameAs(mockDaggerElement)

    verify(mockKtConstructorIdentifier, never()).getDaggerElement(any())
    verify(mockKtFunctionIdentifier, never()).getDaggerElement(any())
    verify(mockKtParameterIdentifier, never()).getDaggerElement(any())
    verify(mockKtPropertyIdentifier, times(1)).getDaggerElement(any())
    verify(mockPsiFieldIdentifier, never()).getDaggerElement(any())
    verify(mockPsiMethodIdentifier, never()).getDaggerElement(any())
    verify(mockPsiParameterIdentifier, never()).getDaggerElement(any())
  }

  @Test
  fun getDaggerElement_psiField() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      public class Foo {}

      public class Bar {
        public Foo fo<caret>o;
      }
      """.trimIndent())

    val psiElement = myFixture.elementAtCaret.parentOfType<PsiField>(/* withSelf = */ true)!!

    val mockDaggerElement: DaggerElement = mock()
    whenever(mockPsiFieldIdentifier.getDaggerElement(any())).thenReturn(mockDaggerElement)

    assertThat(mockIdentifiers.getDaggerElement(psiElement)).isSameAs(mockDaggerElement)

    verify(mockKtConstructorIdentifier, never()).getDaggerElement(any())
    verify(mockKtFunctionIdentifier, never()).getDaggerElement(any())
    verify(mockKtParameterIdentifier, never()).getDaggerElement(any())
    verify(mockKtPropertyIdentifier, never()).getDaggerElement(any())
    verify(mockPsiFieldIdentifier, times(1)).getDaggerElement(any())
    verify(mockPsiMethodIdentifier, never()).getDaggerElement(any())
    verify(mockPsiParameterIdentifier, never()).getDaggerElement(any())
  }

  @Test
  fun getDaggerElement_psiMethod() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      public class Foo {}

      public class Bar {
        public Foo fo<caret>o() { return new Foo(); }
      }
      """.trimIndent())

    val psiElement = myFixture.elementAtCaret.parentOfType<PsiMethod>(/* withSelf = */ true)!!

    val mockDaggerElement: DaggerElement = mock()
    whenever(mockPsiMethodIdentifier.getDaggerElement(any())).thenReturn(mockDaggerElement)

    assertThat(mockIdentifiers.getDaggerElement(psiElement)).isSameAs(mockDaggerElement)

    verify(mockKtConstructorIdentifier, never()).getDaggerElement(any())
    verify(mockKtFunctionIdentifier, never()).getDaggerElement(any())
    verify(mockKtParameterIdentifier, never()).getDaggerElement(any())
    verify(mockKtPropertyIdentifier, never()).getDaggerElement(any())
    verify(mockPsiFieldIdentifier, never()).getDaggerElement(any())
    verify(mockPsiMethodIdentifier, times(1)).getDaggerElement(any())
    verify(mockPsiParameterIdentifier, never()).getDaggerElement(any())
  }

  @Test
  fun getDaggerElement_psiParameter() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      public class Foo {}

      public class Bar {
        public void foo(Foo f<caret>oo) {}
      }
      """.trimIndent())

    val psiElement = myFixture.elementAtCaret.parentOfType<PsiParameter>(/* withSelf = */ true)!!

    val mockDaggerElement: DaggerElement = mock()
    whenever(mockPsiParameterIdentifier.getDaggerElement(any())).thenReturn(mockDaggerElement)

    assertThat(mockIdentifiers.getDaggerElement(psiElement)).isSameAs(mockDaggerElement)

    verify(mockKtConstructorIdentifier, never()).getDaggerElement(any())
    verify(mockKtFunctionIdentifier, never()).getDaggerElement(any())
    verify(mockKtParameterIdentifier, never()).getDaggerElement(any())
    verify(mockKtPropertyIdentifier, never()).getDaggerElement(any())
    verify(mockPsiFieldIdentifier, never()).getDaggerElement(any())
    verify(mockPsiMethodIdentifier, never()).getDaggerElement(any())
    verify(mockPsiParameterIdentifier, times(1)).getDaggerElement(any())
  }

  @Test
  fun getDaggerElement_unknownType() {
    val psiFile: PsiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;

      public class Foo {}
      """.trimIndent())

    assertThat(mockIdentifiers.getDaggerElement(psiFile)).isNull()

    verify(mockKtConstructorIdentifier, never()).getDaggerElement(any())
    verify(mockKtFunctionIdentifier, never()).getDaggerElement(any())
    verify(mockKtParameterIdentifier, never()).getDaggerElement(any())
    verify(mockKtPropertyIdentifier, never()).getDaggerElement(any())
    verify(mockPsiFieldIdentifier, never()).getDaggerElement(any())
    verify(mockPsiMethodIdentifier, never()).getDaggerElement(any())
    verify(mockPsiParameterIdentifier, never()).getDaggerElement(any())
  }
}