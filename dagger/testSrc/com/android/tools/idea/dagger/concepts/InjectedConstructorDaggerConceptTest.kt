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

import com.android.tools.idea.dagger.addDaggerAndHiltClasses
import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexPsiWrapper
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class InjectedConstructorDaggerConceptTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture
  private lateinit var myProject: Project

  @Before
  fun setup() {
    myFixture = projectRule.fixture
    myProject = myFixture.project
  }

  private fun runIndexer(wrapper: DaggerIndexMethodWrapper): Map<String, Set<IndexValue>> =
    mutableMapOf<String, MutableSet<IndexValue>>().also { indexEntries ->
      InjectedConstructorDaggerConcept.indexers.methodIndexers.forEach {
        it.addIndexEntries(wrapper, indexEntries)
      }
    }

  @Test
  fun indexer_nonInjectedConstructor() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      class Foo constructor(arg1: Bar, arg2: Baz, arg3: Baz) {
      }
      """
          .trimIndent()
      ) as KtFile

    val element: KtFunction = myFixture.findParentElement("construct|or")
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_injectedConstructor() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example
      import javax.inject.Inject

      class Foo @Inject constructor(arg1: Bar, arg2: Baz, arg3: Baz) {
      }
      """
          .trimIndent()
      ) as KtFile

    val element: KtFunction = myFixture.findParentElement("construct|or")
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries)
      .containsExactly(
        FOO_ID.asFqNameString(),
        setOf(InjectedConstructorIndexValue(FOO_ID)),
        "Bar",
        setOf(InjectedConstructorParameterIndexValue(FOO_ID, "arg1")),
        "Baz",
        setOf(
          InjectedConstructorParameterIndexValue(FOO_ID, "arg2"),
          InjectedConstructorParameterIndexValue(FOO_ID, "arg3")
        ),
      )
  }

  @Test
  fun indexer_wrongInjectAnnotation() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example
      import com.other.Inject

      class Foo @Inject constructor(arg1: Bar, arg2: Baz, arg3: Baz) {
      }
      """
          .trimIndent()
      ) as KtFile

    val element: KtFunction = myFixture.findParentElement("construct|or")
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_injectedClassFunctionNotConstructor() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example
      import javax.inject.Inject

      class Foo {
        @Inject
        fun someFunction(arg1: Bar, arg2: Baz, arg3: Baz)
      }
      """
          .trimIndent()
      ) as KtFile

    val element: KtFunction = myFixture.findParentElement("someFunc|tion")
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_injectedPackageFunctionNotConstructor() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example
      import javax.inject.Inject

      @Inject
      fun someFunction(arg1: Bar, arg2: Baz, arg3: Baz)
      """
          .trimIndent()
      ) as KtFile

    val element: KtFunction = myFixture.findParentElement("someFunc|tion")
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun injectedConstructorIndexValue_serialization() {
    val indexValue = InjectedConstructorIndexValue(FOO_ID)
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun injectedConstructorIndexValue_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example
      import javax.inject.Inject

      class ClassWithInjectedConstructor @Inject constructor() {
      }

      class ClassWithoutInjectedConstructor constructor() {
      }

      """
        .trimIndent()
    )

    val constructor1Element: KtConstructor<*> =
      myFixture.findParentElement("ClassWithInjectedConstructor @Inject cons|tructor")

    val indexValue1 = InjectedConstructorIndexValue(CLASS_WITH_INJECTED_CONSTRUCTOR_ID)
    val indexValue2 = InjectedConstructorIndexValue(CLASS_WITHOUT_INJECTED_CONSTRUCTOR_ID)

    assertThat(indexValue1.resolveToDaggerElements(myProject, myProject.projectScope()).single())
      .isEqualTo(ProviderDaggerElement(constructor1Element))

    assertThat(indexValue2.resolveToDaggerElements(myProject, myProject.projectScope())).isEmpty()
  }

  @Test
  fun injectedConstructorIndexValue_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
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

      """
        .trimIndent()
    )

    val constructor1Element: PsiMethod =
      myFixture.findParentElement("ClassWithInjectedConstru|ctor()")
    val indexValue1 = InjectedConstructorIndexValue(CLASS_WITH_INJECTED_CONSTRUCTOR_ID)
    val indexValue2 = InjectedConstructorIndexValue(CLASS_WITHOUT_INJECTED_CONSTRUCTOR_ID)

    assertThat(indexValue1.resolveToDaggerElements(myProject, myProject.projectScope()).single())
      .isEqualTo(ProviderDaggerElement(constructor1Element))

    assertThat(indexValue2.resolveToDaggerElements(myProject, myProject.projectScope())).isEmpty()
  }

  @Test
  fun injectedConstructorParameterIndexValue_serialization() {
    val indexValue = InjectedConstructorParameterIndexValue(FOO_ID, "b")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun injectedConstructorParameterIndexValue_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example
      import javax.inject.Inject

      class Bar {}

      class ClassWithInjectedConstructor @Inject constructor(bar: Bar) {
      }

      class ClassWithoutInjectedConstructor constructor(bar: Bar) {
      }

      """
        .trimIndent()
    )

    val parameter1Element: KtParameter =
      myFixture.findParentElement("ClassWithInjectedConstructor @Inject constructor(b|ar: Bar)")
    val indexValue1 =
      InjectedConstructorParameterIndexValue(CLASS_WITH_INJECTED_CONSTRUCTOR_ID, "bar")
    val indexValue2 =
      InjectedConstructorParameterIndexValue(CLASS_WITHOUT_INJECTED_CONSTRUCTOR_ID, "bar")

    assertThat(indexValue1.resolveToDaggerElements(myProject, myProject.projectScope()).single())
      .isEqualTo(ConsumerDaggerElement(parameter1Element))

    assertThat(indexValue2.resolveToDaggerElements(myProject, myProject.projectScope())).isEmpty()
  }

  @Test
  fun injectedConstructorParameterIndexValue_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
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

      """
        .trimIndent()
    )

    val parameter1Element: PsiParameter =
      myFixture.findParentElement("ClassWithInjectedConstructor(Bar ba|r)")
    val indexValue1 =
      InjectedConstructorParameterIndexValue(CLASS_WITH_INJECTED_CONSTRUCTOR_ID, "bar")

    val indexValue2 =
      InjectedConstructorParameterIndexValue(CLASS_WITHOUT_INJECTED_CONSTRUCTOR_ID, "bar")

    assertThat(indexValue1.resolveToDaggerElements(myProject, myProject.projectScope()).single())
      .isEqualTo(ConsumerDaggerElement(parameter1Element))

    assertThat(indexValue2.resolveToDaggerElements(myProject, myProject.projectScope())).isEmpty()
  }

  companion object {
    private val FOO_ID = ClassId.fromString("com/example/Foo")
    private val CLASS_WITH_INJECTED_CONSTRUCTOR_ID =
      ClassId.fromString("com/example/ClassWithInjectedConstructor")
    private val CLASS_WITHOUT_INJECTED_CONSTRUCTOR_ID =
      ClassId.fromString("com/example/ClassWithoutInjectedConstructor")
  }
}
