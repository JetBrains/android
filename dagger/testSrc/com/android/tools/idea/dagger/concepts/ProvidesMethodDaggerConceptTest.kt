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
class ProvidesMethodDaggerConceptTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  private fun runIndexer(wrapper: DaggerIndexMethodWrapper): Map<String, Set<IndexValue>> =
    mutableMapOf<String, MutableSet<IndexValue>>().also { indexEntries ->
      ProvidesMethodDaggerConcept.indexers.methodIndexers.forEach {
        it.addIndexEntries(wrapper, indexEntries)
      }
    }

  @Test
  fun indexer_moduleMethodWithProvides() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import dagger.Module

      @Module
      interface HeaterModule {
        fun provideHeater(electricHeater: ElectricHeater) : Heater {}
      }
      """
          .trimIndent()
      ) as KtFile

    val element = myFixture.moveCaret("provide|Heater").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_providesMethodWithModule() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import dagger.Provides

      interface HeaterModule {
        @Provides
        fun provideHeater(electricHeater: ElectricHeater) : Heater {}
      }
      """
          .trimIndent()
      ) as KtFile

    val element = myFixture.moveCaret("provide|Heater").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_providesMethodOutsideClass() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import dagger.Provides

      @Provides
      fun provideHeater(electricHeater: ElectricHeater) : Heater {}
      """
          .trimIndent()
      ) as KtFile

    val element = myFixture.moveCaret("provide|Heater").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_providesMethod() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import dagger.Module
      import dagger.Provides

      @Module
      interface HeaterModule {
        @Provides
        fun provideHeater(electricHeater: ElectricHeater, electricHeater2: ElectricHeater) : Heater {}
      }
      """
          .trimIndent()
      ) as KtFile

    val element = myFixture.moveCaret("provide|Heater").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries)
      .containsExactly(
        "Heater",
        setOf(ProvidesMethodIndexValue("com.example.HeaterModule", "provideHeater")),
        "ElectricHeater",
        setOf(
          ProvidesMethodParameterIndexValue(
            "com.example.HeaterModule",
            "provideHeater",
            "electricHeater"
          ),
          ProvidesMethodParameterIndexValue(
            "com.example.HeaterModule",
            "provideHeater",
            "electricHeater2"
          )
        ),
      )
  }

  @Test
  fun indexer_wrongProvidesAnnotation() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import dagger.Module
      import com.other.Provides

      @Module
      interface HeaterModule {
        @Provides
        fun provideHeater(electricHeater: ElectricHeater, electricHeater2: ElectricHeater) : Heater {}
      }
      """
          .trimIndent()
      ) as KtFile

    val element = myFixture.moveCaret("provide|Heater").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_wrongModuleAnnotation() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import com.other.Module
      import dagger.Provides

      @Module
      interface HeaterModule {
        @Provides
        fun provideHeater(electricHeater: ElectricHeater, electricHeater2: ElectricHeater) : Heater {}
      }
      """
          .trimIndent()
      ) as KtFile

    val element = myFixture.moveCaret("provide|Heater").parentOfType<KtFunction>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun providesMethodIndexValue_serialization() {
    val indexValue = ProvidesMethodIndexValue("a", "b")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun providesMethodIndexValue_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.Module
      import dagger.Provides

      interface Heater {}
      class ElectricHeater : Heater {}

      @Module
      interface HeaterModule {
        @Provides
        fun provideHeater(electricHeater: ElectricHeater) : Heater {}

        fun dontProvideHeater(electricHeater: ElectricHeater) : Heater {}
      }
      """
        .trimIndent()
    )

    val heaterPsiType =
      myFixture.moveCaret("interface Heat|er {}").parentOfType<KtClass>()!!.toPsiType()!!
    val otherPsiType =
      myFixture.moveCaret("interface HeaterM|odule").parentOfType<KtClass>()!!.toPsiType()!!

    val indexValue1 = ProvidesMethodIndexValue("com.example.HeaterModule", "provideHeater")
    val indexValue2 = ProvidesMethodIndexValue("com.example.HeaterModule", "dontProvideHeater")

    val provideHeaterFunction =
      myFixture.moveCaret("fun provideHe|ater").parentOfType<KtFunction>()!!

    val resolvedIndexValue1 =
      indexValue1
        .resolveToDaggerElements(heaterPsiType, myFixture.project, myFixture.project.projectScope())
        .single()
    assertThat(resolvedIndexValue1.psiElement).isEqualTo(provideHeaterFunction)
    assertThat(resolvedIndexValue1.daggerType).isEqualTo(DaggerElement.Type.PROVIDER)

    assertThat(
        indexValue1.resolveToDaggerElements(
          otherPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()

    assertThat(
        indexValue2.resolveToDaggerElements(
          heaterPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
    assertThat(
        indexValue2.resolveToDaggerElements(
          otherPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
  }

  @Test
  fun providesMethodIndexValue_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Module;
      import dagger.Provides;

      public interface Heater {}
      public class ElectricHeater implements Heater {}

      @Module
      public interface HeaterModule {
        @Provides
        Heater provideHeater(ElectricHeater electricHeater) {}

        Heater dontProvideHeater(ElectricHeater electricHeater) {}
      }
      """
        .trimIndent()
    )

    val heaterPsiType =
      toPsiType(myFixture.moveCaret("interface Heat|er {}").parentOfType<PsiClass>()!!)!!
    val otherPsiType =
      toPsiType(myFixture.moveCaret("interface HeaterM|odule").parentOfType<PsiClass>()!!)!!

    val indexValue1 = ProvidesMethodIndexValue("com.example.HeaterModule", "provideHeater")
    val indexValue2 = ProvidesMethodIndexValue("com.example.HeaterModule", "dontProvideHeater")

    val provideHeaterFunction =
      myFixture.moveCaret("Heater provideHe|ater").parentOfType<PsiMethod>()!!

    val resolvedIndexValue1 =
      indexValue1
        .resolveToDaggerElements(heaterPsiType, myFixture.project, myFixture.project.projectScope())
        .single()
    assertThat(resolvedIndexValue1.psiElement).isEqualTo(provideHeaterFunction)
    assertThat(resolvedIndexValue1.daggerType).isEqualTo(DaggerElement.Type.PROVIDER)

    assertThat(
        indexValue1.resolveToDaggerElements(
          otherPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()

    assertThat(
        indexValue2.resolveToDaggerElements(
          heaterPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
    assertThat(
        indexValue2.resolveToDaggerElements(
          otherPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
  }

  @Test
  fun providesMethodParameterIndexValue_serialization() {
    val indexValue = ProvidesMethodParameterIndexValue("a", "b", "c")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun providesMethodParameterIndexValue_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.Module
      import dagger.Provides

      interface Heater {}
      class ElectricHeater : Heater {}

      @Module
      interface HeaterModule {
        @Provides
        fun provideHeater(electricHeater: ElectricHeater) : Heater {}

        fun dontProvideHeater(electricHeater: ElectricHeater) : Heater {}
      }
      """
        .trimIndent()
    )

    val electricHeaterPsiType =
      myFixture.moveCaret("class ElectricHea|ter").parentOfType<KtClass>()!!.toPsiType()!!
    val otherPsiType =
      myFixture.moveCaret("interface HeaterM|odule").parentOfType<KtClass>()!!.toPsiType()!!

    val indexValue1 =
      ProvidesMethodParameterIndexValue(
        "com.example.HeaterModule",
        "provideHeater",
        "electricHeater"
      )
    val indexValue2 =
      ProvidesMethodParameterIndexValue(
        "com.example.HeaterModule",
        "dontProvideHeater",
        "electricHeater"
      )

    val electricHeaterParameter =
      myFixture
        .moveCaret("provideHeater(elect|ricHeater: ElectricHeater")
        .parentOfType<KtParameter>()!!

    val resolvedIndexValue1 =
      indexValue1
        .resolveToDaggerElements(
          electricHeaterPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
        .single()
    assertThat(resolvedIndexValue1.psiElement).isEqualTo(electricHeaterParameter)
    assertThat(resolvedIndexValue1.daggerType).isEqualTo(DaggerElement.Type.CONSUMER)

    assertThat(
        indexValue1.resolveToDaggerElements(
          otherPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()

    assertThat(
        indexValue2.resolveToDaggerElements(
          electricHeaterPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
    assertThat(
        indexValue2.resolveToDaggerElements(
          otherPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
  }

  @Test
  fun providesMethodParameterIndexValue_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Module;
      import dagger.Provides;

      public interface Heater {}
      public class ElectricHeater implements Heater {}

      @Module
      public interface HeaterModule {
        @Provides
        Heater provideHeater(ElectricHeater electricHeater) {}

        Heater dontProvideHeater(ElectricHeater electricHeater) {}
      }
      """
        .trimIndent()
    )

    val electricHeaterPsiType =
      toPsiType(myFixture.moveCaret("class ElectricHea|ter").parentOfType<PsiClass>()!!)!!
    val otherPsiType =
      toPsiType(myFixture.moveCaret("interface HeaterM|odule").parentOfType<PsiClass>()!!)!!

    val indexValue1 =
      ProvidesMethodParameterIndexValue(
        "com.example.HeaterModule",
        "provideHeater",
        "electricHeater"
      )
    val indexValue2 =
      ProvidesMethodParameterIndexValue(
        "com.example.HeaterModule",
        "dontProvideHeater",
        "electricHeater"
      )

    val electricHeaterParameter =
      myFixture
        .moveCaret("provideHeater(ElectricHeater elec|tricHeater")
        .parentOfType<PsiParameter>()!!

    val resolvedIndexValue1 =
      indexValue1
        .resolveToDaggerElements(
          electricHeaterPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
        .single()
    assertThat(resolvedIndexValue1.psiElement).isEqualTo(electricHeaterParameter)
    assertThat(resolvedIndexValue1.daggerType).isEqualTo(DaggerElement.Type.CONSUMER)

    assertThat(
        indexValue1.resolveToDaggerElements(
          otherPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()

    assertThat(
        indexValue2.resolveToDaggerElements(
          electricHeaterPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
    assertThat(
        indexValue2.resolveToDaggerElements(
          otherPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
  }
}
