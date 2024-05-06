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
import com.android.tools.idea.kotlin.toPsiType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class ComponentProvisionMethodConceptTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture
  private lateinit var myProject: Project

  @Before
  fun setup() {
    myFixture = projectRule.fixture
    myProject = myFixture.project
  }

  @Test
  fun indexer_provisionMethod() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import dagger.Component
        import dagger.Subcomponent

        @Component
        interface MyComponent {
          fun provisionMethodComponent(): Foo
          fun nonProvisionMethodComponent(bar: Bar): Foo
          fun methodWithoutTypeComponent() = { return "" }
          val provisionPropertyComponent: Foo
          val provisionPropertyNotAbstractComponent = Foo()
        }

        @Subcomponent
        interface MySubcomponent {
          fun provisionMethodSubcomponent(): Foo
          fun nonProvisionMethodSubcomponent(bar: Bar): Foo
          fun methodWithoutTypeSubcomponent() = { return "" }
          val provisionPropertySubcomponent: Foo
          val provisionPropertyNotAbstractComponent = Foo()
        }

        interface NotAComponent {
          fun provisionMethodNotAComponent(): Foo
          fun nonProvisionMethodNotAComponent(bar: Bar): Foo
          fun methodWithoutTypeNotAComponent() = { return "" }
          val provisionPropertyNotAComponent: Foo
          val provisionPropertyNotAbstractNotAComponent = Foo()
        }

        fun provisionMethodOutsideClass(): Foo
        """
          .trimIndent()
      ) as KtFile

    val indexResults = ComponentProvisionMethodConcept.indexers.runIndexerOn(psiFile)

    assertThat(indexResults)
      .containsExactly(
        "Foo",
        setOf(
          ComponentProvisionMethodIndexValue(MY_COMPONENT_ID, "provisionMethodComponent"),
          ComponentProvisionMethodIndexValue(MY_SUBCOMPONENT_ID, "provisionMethodSubcomponent"),
          ComponentProvisionPropertyIndexValue(MY_COMPONENT_ID, "provisionPropertyComponent"),
          ComponentProvisionPropertyIndexValue(MY_SUBCOMPONENT_ID, "provisionPropertySubcomponent")
        )
      )
  }

  @Test
  fun componentProvisionMethodIndexValue_serialization() {
    val indexValue = ComponentProvisionMethodIndexValue(MY_COMPONENT_ID, "def")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun componentProvisionMethodAndProperty_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.Component
      import dagger.Subcomponent

      @Component
      interface MyComponent {
        fun provisionMethodComponent(): Foo
        fun nonProvisionMethodComponent(bar: Bar): Foo
        fun concreteMethodComponent() = { return "" }
        val fooPropertyComponent: Foo
        val fooPropertyConcreteComponent = Foo()
      }

      @Subcomponent
      interface MySubcomponent {
        fun provisionMethodSubcomponent(): Foo
        fun nonProvisionMethodSubcomponent(bar: Bar): Foo
        fun concreteMethodSubcomponent() = { return "" }
        val fooPropertySubcomponent: Foo
        val fooPropertyConcreteSubComponent = Foo()
      }

      interface NotAComponent {
        fun provisionMethodNotAComponent(): Foo
        fun nonProvisionMethodNotAComponent(bar: Bar): Foo
        fun concreteMethodNotAComponent() = { return "" }
        val fooPropertyNotAComponent: Foo
        val fooPropertyConcreteNotAComponent = Foo()
      }

      class Foo
      class Bar
      """
        .trimIndent()
    )

    val fooPsiType = myFixture.findParentElement<KtClass>("class F|oo").toPsiType()!!

    val provisionMethodComponentDaggerElement =
      ComponentProvisionMethodDaggerElement(
        myFixture.findParentElement<KtFunction>("provisionMethod|Component")
      )
    val provisionMethodSubcomponentDaggerElement =
      ComponentProvisionMethodDaggerElement(
        myFixture.findParentElement<KtFunction>("provisionMethod|Subcomponent")
      )
    val provisionPropertyComponentDaggerElement =
      ComponentProvisionMethodDaggerElement(
        myFixture.findParentElement<KtProperty>("fooProperty|Component"),
        fooPsiType
      )
    val provisionPropertySubcomponentDaggerElement =
      ComponentProvisionMethodDaggerElement(
        myFixture.findParentElement<KtProperty>("fooProperty|Subcomponent"),
        fooPsiType
      )

    // Expected to resolve
    assertThat(
        ComponentProvisionMethodIndexValue(MY_COMPONENT_ID, "provisionMethodComponent")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionMethodComponentDaggerElement)
    assertThat(
        ComponentProvisionPropertyIndexValue(MY_COMPONENT_ID, "fooPropertyComponent")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionPropertyComponentDaggerElement)

    assertThat(
        ComponentProvisionMethodIndexValue(MY_SUBCOMPONENT_ID, "provisionMethodSubcomponent")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionMethodSubcomponentDaggerElement)
    assertThat(
        ComponentProvisionPropertyIndexValue(MY_SUBCOMPONENT_ID, "fooPropertySubcomponent")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionPropertySubcomponentDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        MY_COMPONENT_ID to "nonProvisionMethodComponent",
        MY_COMPONENT_ID to "concreteMethodComponent",
        MY_COMPONENT_ID to "fooPropertyConcreteComponent",
        MY_SUBCOMPONENT_ID to "nonProvisionMethodSubcomponent",
        MY_SUBCOMPONENT_ID to "concreteMethodSubcomponent",
        MY_SUBCOMPONENT_ID to "fooPropertyConcreteSubComponent",
        NOT_A_COMPONENT_ID to "provisionMethodNotAComponent",
        NOT_A_COMPONENT_ID to "nonProvisionMethodNotAComponent",
        NOT_A_COMPONENT_ID to "concreteMethodNotAComponent",
        NOT_A_COMPONENT_ID to "fooPropertyNotAComponent",
        NOT_A_COMPONENT_ID to "fooPropertyConcreteNotAComponent",
      )

    for ((classId, methodName) in nonResolving) {
      assertWithMessage("Resolution for ($classId, $methodName)")
        .that(
          ComponentProvisionMethodIndexValue(classId, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope()),
        )
        .isEmpty()

      assertWithMessage("Resolution for ($classId, $methodName)")
        .that(
          ComponentProvisionPropertyIndexValue(classId, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope()),
        )
        .isEmpty()
    }
  }

  @Test
  fun componentProvisionMethod_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Component;
      import dagger.Subcomponent;

      @Component
      interface MyComponent {
        Foo provisionMethodComponent();
        Foo nonProvisionMethodComponent(Bar bar);
        Foo concreteMethodComponent() { return new Foo(); }
      }

      @Subcomponent
      interface MySubcomponent {
        Foo provisionMethodSubcomponent();
        Foo nonProvisionMethodSubcomponent(Bar bar);
        Foo concreteMethodSubcomponent() { return new Foo(); }
      }

      interface NotAComponent {
        Foo provisionMethodNotAComponent();
        Foo nonProvisionMethodNotAComponent(Bar bar);
        Foo concreteMethodNotAComponent() { return new Foo(); }
      }

      class Foo {}
      class Bar {}
      """
        .trimIndent()
    )

    val provisionMethodComponentDaggerElement =
      ComponentProvisionMethodDaggerElement(
        myFixture.findParentElement<PsiMethod>("provisionMethod|Component")
      )
    val provisionMethodSubcomponentDaggerElement =
      ComponentProvisionMethodDaggerElement(
        myFixture.findParentElement<PsiMethod>("provisionMethod|Subcomponent")
      )

    // Expected to resolve
    assertThat(
        ComponentProvisionMethodIndexValue(MY_COMPONENT_ID, "provisionMethodComponent")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionMethodComponentDaggerElement)
    assertThat(
        ComponentProvisionMethodIndexValue(MY_SUBCOMPONENT_ID, "provisionMethodSubcomponent")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionMethodSubcomponentDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        MY_COMPONENT_ID to "nonProvisionMethodComponent",
        MY_COMPONENT_ID to "concreteMethodComponent",
        MY_SUBCOMPONENT_ID to "nonProvisionMethodSubcomponent",
        MY_SUBCOMPONENT_ID to "concreteMethodSubcomponent",
        NOT_A_COMPONENT_ID to "provisionMethodNotAComponent",
        NOT_A_COMPONENT_ID to "nonProvisionMethodNotAComponent",
        NOT_A_COMPONENT_ID to "concreteMethodNotAComponent"
      )

    for ((classId, methodName) in nonResolving) {
      assertWithMessage("Resolution for ($classId, $methodName)")
        .that(
          ComponentProvisionMethodIndexValue(classId, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope()),
        )
        .isEmpty()
    }
  }

  @Test
  fun componentProvisionPropertyIndexValue_serialization() {
    val indexValue = ComponentProvisionPropertyIndexValue(MY_COMPONENT_ID, "def")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  companion object {
    private val MY_COMPONENT_ID = ClassId.fromString("com/example/MyComponent")
    private val MY_SUBCOMPONENT_ID = ClassId.fromString("com/example/MySubcomponent")
    private val NOT_A_COMPONENT_ID = ClassId.fromString("com/example/NotAComponent")
  }
}
