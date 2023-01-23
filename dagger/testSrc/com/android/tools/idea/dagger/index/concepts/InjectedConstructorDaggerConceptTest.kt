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

import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexPsiWrapper
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
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
}
