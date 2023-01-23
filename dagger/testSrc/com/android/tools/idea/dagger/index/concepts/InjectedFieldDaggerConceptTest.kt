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
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexFieldWrapper
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
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class InjectedFieldDaggerConceptTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  private fun runIndexer(wrapper: DaggerIndexFieldWrapper): Map<String, Set<IndexValue>> =
    mutableMapOf<String, MutableSet<IndexValue>>().also { indexEntries ->
      InjectedFieldDaggerConcept.indexers.fieldIndexers.forEach { it.addIndexEntries(wrapper, indexEntries) }
    }

  @Test
  fun indexer_injectedField() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      import javax.inject.Inject

      class CoffeeMaker() {
        @Inject lateinit var heater: Heater
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("hea|ter").parentOfType<KtProperty>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).containsExactly(
      "Heater", setOf(InjectedFieldIndexValue("com.example.CoffeeMaker", "heater"))
    )
  }

  @Test
  fun indexer_nonInjectedField() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      class CoffeeMaker() {
        lateinit var heater: Heater
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("hea|ter").parentOfType<KtProperty>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_wrongInjectAnnotation() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      import com.other.Inject

      class CoffeeMaker() {
        @Inject lateinit var heater: Heater
      }
      """.trimIndent()) as KtFile

    val element = myFixture.moveCaret("hea|ter").parentOfType<KtProperty>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }
}
