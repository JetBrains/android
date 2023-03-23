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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.dagger.addDaggerAndHiltClasses
import com.android.tools.idea.dagger.index.DaggerDataIndexer
import com.android.tools.idea.dagger.index.IndexValue
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
import com.intellij.util.indexing.FileContent
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
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
        }

        @Subcomponent
        interface MySubcomponent {
          fun provisionMethodSubcomponent(): Foo
          fun nonProvisionMethodSubcomponent(bar: Bar): Foo
          fun methodWithoutTypeSubcomponent() = { return "" }
        }

        interface NotAComponent {
          fun provisionMethodNotAComponent(): Foo
          fun nonProvisionMethodNotAComponent(bar: Bar): Foo
          fun methodWithoutTypeNotAComponent() = { return "" }
        }

        fun provisionMethodOutsideClass(): Foo
        """
          .trimIndent()
      ) as KtFile

    val indexResults = runIndexer(psiFile)

    assertThat(indexResults)
      .containsExactly(
        "Foo",
        setOf(
          ComponentProvisionMethodIndexValue("com.example.MyComponent", "provisionMethodComponent"),
          ComponentProvisionMethodIndexValue(
            "com.example.MySubcomponent",
            "provisionMethodSubcomponent"
          ),
        )
      )
  }

  private fun runIndexer(ktFile: KtFile): Map<String, Set<IndexValue>> {
    val dataIndexer = DaggerDataIndexer(ComponentProvisionMethodConcept.indexers)
    val fileContent: FileContent = mock()
    whenever(fileContent.psiFile).thenReturn(ktFile)
    whenever(fileContent.fileType).thenReturn(KotlinFileType.INSTANCE)
    return dataIndexer.map(fileContent)
  }

  @Test
  fun componentProvisionMethodIndexValue_serialization() {
    val indexValue = ComponentProvisionMethodIndexValue("abc", "def")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun componentProvisionMethod_resolveToPsiElements_kotlin() {
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
      }

      @Subcomponent
      interface MySubcomponent {
        fun provisionMethodSubcomponent(): Foo
        fun nonProvisionMethodSubcomponent(bar: Bar): Foo
        fun concreteMethodSubcomponent() = { return "" }
      }

      interface NotAComponent {
        fun provisionMethodNotAComponent(): Foo
        fun nonProvisionMethodNotAComponent(bar: Bar): Foo
        fun concreteMethodNotAComponent() = { return "" }
      }

      class Foo
      class Bar
      """
        .trimIndent()
    )

    val provisionMethodComponentDaggerElement =
      ComponentProvisionMethodDaggerElement(
        myFixture.findParentElement<KtFunction>("provisionMethod|Component")
      )
    val provisionMethodSubcomponentDaggerElement =
      ComponentProvisionMethodDaggerElement(
        myFixture.findParentElement<KtFunction>("provisionMethod|Subcomponent")
      )

    // Expected to resolve
    assertThat(
        ComponentProvisionMethodIndexValue("com.example.MyComponent", "provisionMethodComponent")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionMethodComponentDaggerElement)
    assertThat(
        ComponentProvisionMethodIndexValue(
            "com.example.MySubcomponent",
            "provisionMethodSubcomponent"
          )
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionMethodSubcomponentDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        "com.example.MyComponent" to "nonProvisionMethodComponent",
        "com.example.MyComponent" to "concreteMethodComponent",
        "com.example.MySubcomponent" to "nonProvisionMethodSubcomponent",
        "com.example.MySubcomponent" to "concreteMethodSubcomponent",
        "com.example.NotAComponent" to "provisionMethodNotAComponent",
        "com.example.NotAComponent" to "nonProvisionMethodNotAComponent",
        "com.example.NotAComponent" to "concreteMethodNotAComponent"
      )

    for ((classFqName, methodName) in nonResolving) {
      assertWithMessage("Resolution for ($classFqName, $methodName)")
        .that(
          ComponentProvisionMethodIndexValue(classFqName, methodName)
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
        ComponentProvisionMethodIndexValue("com.example.MyComponent", "provisionMethodComponent")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionMethodComponentDaggerElement)
    assertThat(
        ComponentProvisionMethodIndexValue(
            "com.example.MySubcomponent",
            "provisionMethodSubcomponent"
          )
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(provisionMethodSubcomponentDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        "com.example.MyComponent" to "nonProvisionMethodComponent",
        "com.example.MyComponent" to "concreteMethodComponent",
        "com.example.MySubcomponent" to "nonProvisionMethodSubcomponent",
        "com.example.MySubcomponent" to "concreteMethodSubcomponent",
        "com.example.NotAComponent" to "provisionMethodNotAComponent",
        "com.example.NotAComponent" to "nonProvisionMethodNotAComponent",
        "com.example.NotAComponent" to "concreteMethodNotAComponent"
      )

    for ((classFqName, methodName) in nonResolving) {
      assertWithMessage("Resolution for ($classFqName, $methodName)")
        .that(
          ComponentProvisionMethodIndexValue(classFqName, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope()),
        )
        .isEmpty()
    }
  }
}
