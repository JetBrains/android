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
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
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
class BindsOptionalOfDaggerConceptTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture
  private lateinit var myProject: Project

  @Before
  fun setup() {
    myFixture = projectRule.fixture
    myProject = myFixture.project
  }

  @Test
  fun bindsOptionalOfIndexer() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import dagger.BindsOptionalOf
        import dagger.Module

        @BindsOptionalOf
        fun functionOutsideClass() {}

        @Module
        interface MyModule {
          @BindsOptionalOf
          fun functionInModule(): Foo

          @BindsOptionalOf
          fun functionInModuleWithParameters(p0: Int): Foo

          fun functionInModuleWithoutBindsOptionalOf(): Foo

          companion object {
            @BindsOptionalOf
            fun functionInModuleCompanion(): Foo {}

            fun functionInModuleCompanionWithoutBindsOptionalOf(): Foo {}
          }
        }

        interface NotAModule {
          @BindsOptionalOf
          fun functionInNotAModule(): Foo

          fun functionInNotAModuleWithoutBindsOptionalOf(): Foo

          companion object {
            @BindsOptionalOf
            fun functionInNotAModuleCompanion(): Foo {}

            fun functionInNotAModuleCompanionWithoutBindsOptionalOf(): Foo {}
          }
        }
        """
          .trimIndent()
      ) as KtFile

    val indexResults = BindsOptionalOfDaggerConcept.indexers.runIndexerOn(psiFile)

    assertThat(indexResults)
      .containsExactly(
        "Optional",
        setOf(
          BindsOptionalOfIndexValue("com.example.MyModule", "functionInModule"),
          BindsOptionalOfIndexValue("com.example.MyModule.Companion", "functionInModuleCompanion"),
        )
      )
  }

  @Test
  fun bindsOptionalOfIndexValue_serialization() {
    val indexValue = BindsOptionalOfIndexValue("abc", "def")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun resolveToDaggerElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.BindsOptionalOf
      import dagger.Module

      @Module
      interface MyModule {
        @BindsOptionalOf
        fun functionInModule(): Foo

        @BindsOptionalOf
        fun functionInModuleWithParameters(p0: Int): Foo

        fun functionInModuleWithoutBindsOptionalOf(): Foo

        companion object {
          @BindsOptionalOf
          fun functionInModuleCompanion(): Foo {}

          fun functionInModuleCompanionWithoutBindsOptionalOf(): Foo {}
        }
      }

      interface NotAModule {
        @BindsOptionalOf
        fun functionInNotAModule(): Foo

        fun functionInNotAModuleWithoutBindsOptionalOf(): Foo

        companion object {
          @BindsOptionalOf
          fun functionInNotAModuleCompanion(): Foo {}

          fun functionInNotAModuleCompanionWithoutBindsOptionalOf(): Foo {}
        }
      }

      class Foo
      """
        .trimIndent()
    )

    val functionInModuleDaggerElement =
      BindsOptionalOfProviderDaggerElement(
        myFixture.findParentElement<KtFunction>("fun function|InModule(): Foo")
      )

    // Expected to resolve
    assertThat(
        BindsOptionalOfIndexValue("com.example.MyModule", "functionInModule")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(functionInModuleDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        "com.example.MyModule" to "functionInModuleWithParameters",
        "com.example.MyModule" to "functionInModuleWithoutBindsOptionalOf",
        "com.example.MyModule.Companion" to "functionInModuleCompanion",
        "com.example.MyModule.Companion" to "functionInModuleCompanionWithoutBindsOptionalOf",
        "com.example.NotAModule" to "functionInNotAModule",
        "com.example.NotAModule" to "functionInNotAModuleWithoutBindsOptionalOf",
        "com.example.NotAModule.Companion" to "functionInNotAModuleCompanion",
        "com.example.NotAModule.Companion" to "functionInNotAModuleCompanionWithoutBindsOptionalOf",
      )

    for ((classFqName, methodName) in nonResolving) {
      assertWithMessage("Resolution for ($classFqName, $methodName)")
        .that(
          BindsOptionalOfIndexValue(classFqName, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope()),
        )
        .isEmpty()
    }
  }

  @Test
  fun resolveToDaggerElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.BindsOptionalOf;
      import dagger.Module;

      @Module
      abstract class MyModule {
        @BindsOptionalOf
        abstract Foo functionInModule();

        @BindsOptionalOf
        abstract Foo functionInModuleWithParameters(int p0);

        abstract Foo functionInModuleWithoutBindsOptionalOf();

        @BindsOptionalOf
        Foo functionInModuleNotAbstract() {}
      }

      interface NotAModule {
        @BindsOptionalOf
        Foo functionInNotAModule();
      }

      class Foo {}
      """
        .trimIndent()
    )

    val functionInModuleDaggerElement =
      BindsOptionalOfProviderDaggerElement(
        myFixture.findParentElement<PsiMethod>("Foo function|InModule();")
      )

    // Expected to resolve
    assertThat(
        BindsOptionalOfIndexValue("com.example.MyModule", "functionInModule")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(functionInModuleDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        "com.example.MyModule" to "functionInModuleWithParameters",
        "com.example.MyModule" to "functionInModuleWithoutBindsOptionalOf",
        "com.example.MyModule" to "functionInModuleNotAbstract",
        "com.example.NotAModule" to "functionInNotAModule",
      )

    for ((classFqName, methodName) in nonResolving) {
      assertWithMessage("Resolution for ($classFqName, $methodName)")
        .that(
          BindsOptionalOfIndexValue(classFqName, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope()),
        )
        .isEmpty()
    }
  }
}
