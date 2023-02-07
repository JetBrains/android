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
package com.android.tools.idea.dagger.concepts

import com.android.tools.idea.AndroidPsiUtils.toPsiType
import com.android.tools.idea.dagger.addDaggerAndHiltClasses
import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexPsiWrapper
import com.android.tools.idea.kotlin.toPsiType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class InjectedConstructorDaggerConceptTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  private fun runIndexer(wrapper: DaggerIndexMethodWrapper): Map<String, Set<IndexValue>> =
    mutableMapOf<String, MutableSet<IndexValue>>().also { indexEntries ->
      InjectedConstructorDaggerConcept.indexers.methodIndexers.forEach { it.addIndexEntries(wrapper, indexEntries) }
    }

  @Test
  fun indexer_nonInjectedConstructor() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class Foo constructor(arg1: Bar, arg2: Baz, arg3: Baz) {
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("construct|or").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_injectedConstructor() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import javax.inject.Inject

      class Foo @Inject constructor(arg1: Bar, arg2: Baz, arg3: Baz) {
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("construct|or").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).containsExactly(
      "com.example.Foo", setOf(InjectedConstructorIndexValue("com.example.Foo")),
      "Bar", setOf(InjectedConstructorParameterIndexValue("com.example.Foo", "arg1")),
      "Baz",
      setOf(InjectedConstructorParameterIndexValue("com.example.Foo", "arg2"),
            InjectedConstructorParameterIndexValue("com.example.Foo", "arg3")),
    )
  }

  @Test
  fun indexer_wrongInjectAnnotation() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import com.other.Inject

      class Foo @Inject constructor(arg1: Bar, arg2: Baz, arg3: Baz) {
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("construct|or").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_injectedClassFunctionNotConstructor() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import javax.inject.Inject

      class Foo {
        @Inject
        fun someFunction(arg1: Bar, arg2: Baz, arg3: Baz)
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("someFunc|tion").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_injectedPackageFunctionNotConstructor() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import javax.inject.Inject

      @Inject
      fun someFunction(arg1: Bar, arg2: Baz, arg3: Baz)
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("someFunc|tion").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun injectedConstructorIndexValue_serialization() {
    val indexValue = InjectedConstructorIndexValue("a")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun injectedConstructorIndexValue_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import javax.inject.Inject

      class ClassWithInjectedConstructor @Inject constructor() {
      }

      class ClassWithoutInjectedConstructor constructor() {
      }

      """.trimIndent())

    val constructor1Element = myFixture.moveCaret("ClassWithInjectedConstructor @Inject cons|tructor").parentOfType<KtConstructor<*>>()!!
    val indexValue1 = InjectedConstructorIndexValue("com.example.ClassWithInjectedConstructor")
    val expectedPsiType1 = constructor1Element.containingClass()?.toPsiType()!!

    val constructor2Element = myFixture.moveCaret("ClassWithoutInjectedConstructor cons|tructor").parentOfType<KtConstructor<*>>()!!
    val indexValue2 = InjectedConstructorIndexValue("com.example.ClassWithoutInjectedConstructor")
    val expectedPsiType2 = constructor2Element.containingClass()?.toPsiType()!!

    assertThat(indexValue1.resolveToDaggerElements(expectedPsiType1, myFixture.project, myFixture.project.projectScope())).containsExactly(
      DaggerElement(constructor1Element, DaggerElement.Type.PROVIDER))
    assertThat(indexValue1.resolveToDaggerElements(expectedPsiType2, myFixture.project, myFixture.project.projectScope())).isEmpty()

    assertThat(indexValue2.resolveToDaggerElements(expectedPsiType1, myFixture.project, myFixture.project.projectScope())).isEmpty()
    assertThat(indexValue2.resolveToDaggerElements(expectedPsiType2, myFixture.project, myFixture.project.projectScope())).isEmpty()
  }

  @Test
  fun injectedConstructorIndexValue_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class ClassWithInjectedConstructor {
        @Inject
        public ClassWithInjectedConstructor() {}
      }

      public class ClassWithoutInjectedConstructor {
        public ClassWithoutInjectedConstructor() {}
      }

      """.trimIndent())

    val constructor1Element = myFixture.moveCaret("ClassWithInjectedConstru|ctor()").parentOfType<PsiMethod>()!!
    val indexValue1 = InjectedConstructorIndexValue("com.example.ClassWithInjectedConstructor")
    val expectedPsiType1 = toPsiType(constructor1Element.containingClass!!)!!

    val constructor2Element = myFixture.moveCaret("ClassWithoutInjectedConstru|ctor()").parentOfType<PsiMethod>()!!
    val indexValue2 = InjectedConstructorIndexValue("com.example.ClassWithoutInjectedConstructor")
    val expectedPsiType2 = toPsiType(constructor2Element.containingClass!!)!!

    assertThat(indexValue1.resolveToDaggerElements(expectedPsiType1, myFixture.project, myFixture.project.projectScope())).containsExactly(
      DaggerElement(constructor1Element, DaggerElement.Type.PROVIDER))
    assertThat(indexValue1.resolveToDaggerElements(expectedPsiType2, myFixture.project, myFixture.project.projectScope())).isEmpty()

    assertThat(indexValue2.resolveToDaggerElements(expectedPsiType1, myFixture.project, myFixture.project.projectScope())).isEmpty()
    assertThat(indexValue2.resolveToDaggerElements(expectedPsiType2, myFixture.project, myFixture.project.projectScope())).isEmpty()
  }

  @Test
  fun injectedConstructorParameterIndexValue_serialization() {
    val indexValue = InjectedConstructorParameterIndexValue("a", "b")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun injectedConstructorParameterIndexValue_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import javax.inject.Inject

      class Bar {}

      class ClassWithInjectedConstructor @Inject constructor(bar: Bar) {
      }

      class ClassWithoutInjectedConstructor constructor(bar: Bar) {
      }

      """.trimIndent())

    val barPsiType = myFixture.moveCaret("class Ba|r").parentOfType<KtClass>()!!.toPsiType()!!
    val otherPsiType = myFixture.moveCaret("ClassWithoutInjectedConstructor cons|tructor").parentOfType<KtClass>()!!.toPsiType()!!

    val parameter1Element = myFixture.moveCaret("ClassWithInjectedConstructor @Inject constructor(b|ar: Bar)").parentOfType<KtParameter>()!!
    val indexValue1 = InjectedConstructorParameterIndexValue("com.example.ClassWithInjectedConstructor", "bar")

    val indexValue2 = InjectedConstructorParameterIndexValue("com.example.ClassWithoutInjectedConstructor", "bar")

    assertThat(indexValue1.resolveToDaggerElements(barPsiType, myFixture.project, myFixture.project.projectScope())).containsExactly(
      DaggerElement(parameter1Element, DaggerElement.Type.CONSUMER))
    assertThat(indexValue1.resolveToDaggerElements(otherPsiType, myFixture.project, myFixture.project.projectScope())).isEmpty()

    assertThat(indexValue2.resolveToDaggerElements(barPsiType, myFixture.project, myFixture.project.projectScope())).isEmpty()
    assertThat(indexValue2.resolveToDaggerElements(otherPsiType, myFixture.project, myFixture.project.projectScope())).isEmpty()
  }

  @Test
  fun injectedConstructorParameterIndexValue_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import javax.inject.Inject;

      public class Bar {}

      public class ClassWithInjectedConstructor {
        @Inject
        public ClassWithInjectedConstructor(Bar bar) {}
      }

      public class ClassWithoutInjectedConstructor {
        public ClassWithoutInjectedConstructor(Bar bar) {}
      }

      """.trimIndent())

    val barPsiType = toPsiType(myFixture.moveCaret("class Ba|r").parentOfType<PsiClass>()!!)!!
    val otherPsiType = toPsiType(myFixture.moveCaret("ClassWithoutInjectedCons|tructor").parentOfType<PsiClass>()!!)!!

    val parameter1Element = myFixture.moveCaret("ClassWithInjectedConstructor(Bar ba|r)").parentOfType<PsiParameter>()!!
    val indexValue1 = InjectedConstructorParameterIndexValue("com.example.ClassWithInjectedConstructor", "bar")

    val indexValue2 = InjectedConstructorParameterIndexValue("com.example.ClassWithoutInjectedConstructor", "bar")

    assertThat(indexValue1.resolveToDaggerElements(barPsiType, myFixture.project, myFixture.project.projectScope())).containsExactly(
      DaggerElement(parameter1Element, DaggerElement.Type.CONSUMER))
    assertThat(indexValue1.resolveToDaggerElements(otherPsiType, myFixture.project, myFixture.project.projectScope())).isEmpty()

    assertThat(indexValue2.resolveToDaggerElements(barPsiType, myFixture.project, myFixture.project.projectScope())).isEmpty()
    assertThat(indexValue2.resolveToDaggerElements(otherPsiType, myFixture.project, myFixture.project.projectScope())).isEmpty()
  }
}
