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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class AssistedFactoryDaggerConceptTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val myFixture by lazy { projectRule.fixture }
  private val myProject by lazy { myFixture.project }

  @Test
  fun indexers() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import dagger.assisted.AssistedFactory

        @AssistedFactory
        interface MyAssistedFactory {
          fun create1(dep1: Dep1, dep2: Dep2): CreatedObject1
          fun createWithoutType(dep1: Dep1, dep2: Dep2)
        }

        interface NotAnAssistedFactory {
          fun create2(dep3: Dep3, dep4: Dep4): CreatedObject2
        }
        """
          .trimIndent(),
      ) as KtFile

    val indexResults = AssistedFactoryDaggerConcept.indexers.runIndexerOn(psiFile)

    assertThat(indexResults)
      .containsExactly(
        MY_ASSISTED_FACTORY_ID.asFqNameString(),
        setOf(AssistedFactoryClassIndexValue(MY_ASSISTED_FACTORY_ID)),
        "CreatedObject1",
        setOf(AssistedFactoryMethodIndexValue(MY_ASSISTED_FACTORY_ID, "create1")),
      )
  }

  @Test
  fun assistedFactoryClassIndexValue_serialization() {
    val indexValue = AssistedFactoryClassIndexValue(MY_ASSISTED_FACTORY_ID)
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun assistedFactoryClassIndexValue_resolveToDaggerElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.assisted.AssistedFactory

      @AssistedFactory
      interface MyAssistedFactory

      interface NotAnAssistedFactory
      """
        .trimIndent(),
    )

    val myAssistedFactoryDaggerElement =
      ProviderDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("interface MyAssisted|Factory")
      )

    assertThat(
        AssistedFactoryClassIndexValue(MY_ASSISTED_FACTORY_ID)
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .containsExactly(myAssistedFactoryDaggerElement)

    assertThat(
        AssistedFactoryClassIndexValue(NOT_AN_ASSISTED_FACTORY_ID)
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .isEmpty()
  }

  @Test
  fun assistedFactoryClassIndexValue_resolveToDaggerElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.assisted.AssistedFactory;

      @AssistedFactory
      interface MyAssistedFactory {}

      interface NotAnAssistedFactory {}
      """
        .trimIndent(),
    )

    val myAssistedFactoryDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<PsiClass>("interface MyAssisted|Factory"))

    assertThat(
        AssistedFactoryClassIndexValue(MY_ASSISTED_FACTORY_ID)
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .containsExactly(myAssistedFactoryDaggerElement)

    assertThat(
        AssistedFactoryClassIndexValue(NOT_AN_ASSISTED_FACTORY_ID)
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .isEmpty()
  }

  @Test
  fun assistedFactoryMethodIndexValue_serialization() {
    val indexValue = AssistedFactoryMethodIndexValue(MY_ASSISTED_FACTORY_ID, "def")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun assistedFactoryMethodIndexValue_resolveToDaggerElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.assisted.AssistedFactory

      @AssistedFactory
      interface MyAssistedFactory {
        fun create1(dep1: Dep1, dep2: Dep2): CreatedObject
      }

      interface NotAnAssistedFactory {
        fun create2(dep1: Dep1, dep2: Dep2): CreatedObject
      }

      class Dep1
      class Dep2
      class CreatedObject
      """
        .trimIndent(),
    )

    val myAssistedFactoryMethodDaggerElement =
      AssistedFactoryMethodDaggerElement(myFixture.findParentElement<KtFunction>("fun creat|e1"))

    // Expected to resolve
    assertThat(
        AssistedFactoryMethodIndexValue(MY_ASSISTED_FACTORY_ID, "create1")
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .containsExactly(myAssistedFactoryMethodDaggerElement)

    // Expected to not resolve
    assertThat(
        AssistedFactoryMethodIndexValue(NOT_AN_ASSISTED_FACTORY_ID, "create2")
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .isEmpty()
  }

  @Test
  fun assistedFactoryMethodIndexValue_resolveToDaggerElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.assisted.AssistedFactory;

      @AssistedFactory
      interface MyAssistedFactory {
        CreatedObject create1(Dep1 dep1, Dep2 dep2);
      }

      interface NotAnAssistedFactory {
        CreatedObject create2(Dep1 dep1, Dep2 dep2);
      }

      class Dep1 {}
      class Dep2 {}
      class CreatedObject {}
      """
        .trimIndent(),
    )

    val myAssistedFactoryMethodDaggerElement =
      AssistedFactoryMethodDaggerElement(
        myFixture.findParentElement<PsiMethod>("CreatedObject crea|te1")
      )

    // Expected to resolve
    assertThat(
        AssistedFactoryMethodIndexValue(MY_ASSISTED_FACTORY_ID, "create1")
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .containsExactly(myAssistedFactoryMethodDaggerElement)

    // Expected to not resolve
    assertThat(
        AssistedFactoryMethodIndexValue(NOT_AN_ASSISTED_FACTORY_ID, "create2")
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .isEmpty()
  }

  @Test
  fun assistedFactoryMethodDaggerElement_getRelatedDaggerElements() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      import javax.inject.Inject

      @AssistedFactory
      interface MyAssistedFactory {
        fun create(dep1: Dep1, dep2: Dep2): CreatedObject
      }

      class Dep1
      class Dep2
      class Dep3 @Inject constructor()

      class CreatedObject @AssistedInject constructor(@Assisted dep1: Dep1, @Assisted  dep2: Dep2, dep3: Dep3)
      """
        .trimIndent(),
    )

    val assistedFactoryMethodDaggerElement =
      AssistedFactoryMethodDaggerElement(myFixture.findParentElement<KtFunction>("fun cre|ate"))

    val assistedInjectConstructorDaggerElement =
      AssistedInjectConstructorDaggerElement(
        myFixture.findParentElement<KtConstructor<*>>("CreatedObject @AssistedInject const|ructor")
      )

    assertThat(assistedFactoryMethodDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          assistedInjectConstructorDaggerElement,
          "AssistedInject constructors",
          "navigate.to.assisted.inject",
          "CreatedObject",
        )
      )
  }

  companion object {
    private val MY_ASSISTED_FACTORY_ID = ClassId.fromString("com/example/MyAssistedFactory")
    private val NOT_AN_ASSISTED_FACTORY_ID = ClassId.fromString("com/example/NotAnAssistedFactory")
  }
}
