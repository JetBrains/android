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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class AssistedInjectConstructorDaggerConceptTest {

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

        import dagger.assisted.Assisted
        import dagger.assisted.AssistedInject

        class MyAssistedInjectClass @AssistedInject constructor(
          @Assisted dep1: Dep1,
          @Assisted dep2: Dep2,
          dep3: Dep3,
        )

        class MyNotAssistedInjectClass constructor(
          @Assisted dep1: Dep1,
          @Assisted dep2: Dep2,
          dep3: Dep3,
        )
        """
          .trimIndent(),
      ) as KtFile

    val indexResults = AssistedInjectConstructorDaggerConcept.indexers.runIndexerOn(psiFile)

    assertThat(indexResults)
      .containsExactly(
        MY_ASSISTED_INJECT_CLASS_ID.asFqNameString(),
        setOf(AssistedInjectConstructorIndexValue(MY_ASSISTED_INJECT_CLASS_ID)),
        "Dep1",
        setOf(
          AssistedInjectConstructorUnassistedParameterIndexValue(
            MY_ASSISTED_INJECT_CLASS_ID,
            "dep1",
          )
        ),
        "Dep2",
        setOf(
          AssistedInjectConstructorUnassistedParameterIndexValue(
            MY_ASSISTED_INJECT_CLASS_ID,
            "dep2",
          )
        ),
        "Dep3",
        setOf(
          AssistedInjectConstructorUnassistedParameterIndexValue(
            MY_ASSISTED_INJECT_CLASS_ID,
            "dep3",
          )
        ),
      )
  }

  @Test
  fun assistedInjectConstructorIndexValue_serialization() {
    val indexValue = AssistedInjectConstructorIndexValue(MY_ASSISTED_INJECT_CLASS_ID)
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun assistedInjectConstructorIndexValue_resolveToDaggerElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject

      class MyAssistedInjectClass @AssistedInject constructor(
        @Assisted dep1: Dep1,
        @Assisted dep2: Dep2,
        dep3: Dep3,
      )

      class MyNotAssistedInjectClass constructor(
        @Assisted dep1: Dep1,
        @Assisted dep2: Dep2,
        dep3: Dep3,
      )

      class Dep1
      class Dep2
      class Dep3
      """
        .trimIndent(),
    )

    val assistedInjectConstructorDaggerElement =
      AssistedInjectConstructorDaggerElement(
        myFixture.findParentElement<KtConstructor<*>>(
          "class MyAssistedInjectClass @AssistedInject constru|ctor"
        )
      )

    assertThat(
        AssistedInjectConstructorIndexValue(MY_ASSISTED_INJECT_CLASS_ID)
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .containsExactly(assistedInjectConstructorDaggerElement)

    assertThat(
        AssistedInjectConstructorIndexValue(MY_NOT_ASSISTED_INJECT_CLASS_ID)
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .isEmpty()
  }

  @Test
  fun assistedInjectConstructorIndexValue_resolveToDaggerElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.assisted.Assisted;
      import dagger.assisted.AssistedInject;

      class MyAssistedInjectClass {
        @AssistedInject MyAssistedInjectClass(
          @Assisted Dep1 dep1,
          @Assisted Dep2 dep2,
          Dep3 dep3
          ) {}
      }

      class MyNotAssistedInjectClass {
        MyNotAssistedInjectClass(
          @Assisted Dep1 dep1,
          @Assisted Dep2 dep2,
          Dep3 dep3
          ) {}
      }

      class Dep1 {}
      class Dep2 {}
      class Dep3 {}
      """
        .trimIndent(),
    )

    val assistedInjectConstructorDaggerElement =
      AssistedInjectConstructorDaggerElement(
        myFixture.findParentElement<PsiMethod>("MyAssisted|InjectClass(")
      )

    assertThat(
        AssistedInjectConstructorIndexValue(MY_ASSISTED_INJECT_CLASS_ID)
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .containsExactly(assistedInjectConstructorDaggerElement)

    assertThat(
        AssistedInjectConstructorIndexValue(MY_NOT_ASSISTED_INJECT_CLASS_ID)
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .isEmpty()
  }

  @Test
  fun assistedInjectConstructorUnassistedParameterIndexValue_serialization() {
    val indexValue =
      AssistedInjectConstructorUnassistedParameterIndexValue(MY_ASSISTED_INJECT_CLASS_ID, "def")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun assistedInjectConstructorUnassistedParameterIndexValue_resolveToDaggerElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject

      class MyAssistedInjectClass @AssistedInject constructor(
        @Assisted dep1: Dep1,
        @Assisted dep2: Dep2,
        dep3: Dep3,
      )

      class MyNotAssistedInjectClass constructor(
        @Assisted notDep1: Dep1,
        @Assisted notDep2: Dep2,
        dep3: notDep3,
      )

      class Dep1
      class Dep2
      class Dep3
      """
        .trimIndent(),
    )

    val parameterDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("de|p3: Dep3"))

    // Expected to resolve
    assertThat(
        AssistedInjectConstructorUnassistedParameterIndexValue(MY_ASSISTED_INJECT_CLASS_ID, "dep3")
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .containsExactly(parameterDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        MY_ASSISTED_INJECT_CLASS_ID to "dep1",
        MY_ASSISTED_INJECT_CLASS_ID to "dep2",
        MY_NOT_ASSISTED_INJECT_CLASS_ID to "notDep1",
        MY_NOT_ASSISTED_INJECT_CLASS_ID to "notDep2",
        MY_NOT_ASSISTED_INJECT_CLASS_ID to "notDep3",
      )

    for ((classId, parameterName) in nonResolving) {
      assertThat(
          AssistedInjectConstructorUnassistedParameterIndexValue(classId, parameterName)
            .resolveToDaggerElements(myProject, myProject.projectScope())
            .toList()
        )
        .isEmpty()
    }
  }

  @Test
  fun assistedInjectConstructorUnassistedParameterIndexValue_resolveToDaggerElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.assisted.Assisted;
      import dagger.assisted.AssistedInject;

      class MyAssistedInjectClass {
        @AssistedInject MyAssistedInjectClass(
          @Assisted Dep1 dep1,
          @Assisted Dep2 dep2,
          Dep3 dep3
        ) {}
      }

      class MyNotAssistedInjectClass {
        MyNotAssistedInjectClass(
          @Assisted Dep1 notDep1,
          @Assisted Dep2 notDep2,
          notDep3 dep3
        ) {}
      }

      class Dep1 {}
      class Dep2 {}
      class Dep3 {}
      """
        .trimIndent(),
    )

    val parameterDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<PsiParameter>("Dep3 de|p3"))

    // Expected to resolve
    assertThat(
        AssistedInjectConstructorUnassistedParameterIndexValue(MY_ASSISTED_INJECT_CLASS_ID, "dep3")
          .resolveToDaggerElements(myProject, myProject.projectScope())
          .toList()
      )
      .containsExactly(parameterDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        MY_ASSISTED_INJECT_CLASS_ID to "dep1",
        MY_ASSISTED_INJECT_CLASS_ID to "dep2",
        MY_NOT_ASSISTED_INJECT_CLASS_ID to "notDep1",
        MY_NOT_ASSISTED_INJECT_CLASS_ID to "notDep2",
        MY_NOT_ASSISTED_INJECT_CLASS_ID to "notDep3",
      )

    for ((classId, parameterName) in nonResolving) {
      assertThat(
          AssistedInjectConstructorUnassistedParameterIndexValue(classId, parameterName)
            .resolveToDaggerElements(myProject, myProject.projectScope())
            .toList()
        )
        .isEmpty()
    }
  }

  @Test
  fun assistedInjectConstructorDaggerElement_getRelatedDaggerElements() {
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

    val assistedInjectConstructorDaggerElement =
      AssistedInjectConstructorDaggerElement(
        myFixture.findParentElement<KtConstructor<*>>("CreatedObject @AssistedInject const|ructor")
      )

    val assistedFactoryMethodDaggerElement =
      AssistedFactoryMethodDaggerElement(myFixture.findParentElement<KtFunction>("fun cre|ate"))

    assertThat(assistedInjectConstructorDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          assistedFactoryMethodDaggerElement,
          "AssistedFactory methods",
          "navigate.to.assisted.factory",
          "create",
        )
      )
  }

  companion object {
    private val MY_ASSISTED_INJECT_CLASS_ID =
      ClassId.fromString("com/example/MyAssistedInjectClass")
    private val MY_NOT_ASSISTED_INJECT_CLASS_ID =
      ClassId.fromString("com/example/MyNotAssistedInjectClass")
  }
}
