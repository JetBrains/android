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
import com.intellij.psi.PsiParameter
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class BindsInstanceDaggerConceptTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val myFixture by lazy { projectRule.fixture }
  private val myProject by lazy { myFixture.project }

  @Test
  fun bindsInstanceIndexer() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import dagger.BindsInstance
        import dagger.Component

        @Component
        interface MyComponent {
          @Component.Builder
          interface Builder {
            @BindsInstance fun foo(foo: Foo): Builder // Indexed
            @BindsInstance fun foo2(foo: Foo, foo: Foo): Builder // Not indexed - 2 params
            fun bar(bar: Bar): Builder  // Not indexed: no annotation
          }

          @Component.Factory
          interface Factory {
            fun newMyComponent(
              @BindsInstance foo: Foo,  // Indexed
              @BindsInstance foo2: Foo2 // Indexed
            ): MyComponent
            fun newMyComponent2(
              foo: Foo, // Not indexed: no annotation
              foo2: Foo2 // Not indexed: no annotation
            ): MyComponent
          }

          interface NotABuilderOrFactory {
            @BindsInstance fun foo(foo: Foo): Builder // Not indexed
            fun newMyComponent(
              @BindsInstance foo: Foo,
              @BindsInstance foo2: Foo2
            ): MyComponent // Not indexed
          }
        }
        """
          .trimIndent()
      ) as KtFile

    val indexResults = BindsInstanceDaggerConcept.indexers.runIndexerOn(psiFile)

    assertThat(indexResults)
      .containsExactly(
        "Foo",
        setOf(
          BindsInstanceBuilderMethodIndexValue(MY_COMPONENT_BUILDER_ID, "foo"),
          BindsInstanceFactoryMethodParameterIndexValue(
            MY_COMPONENT_FACTORY_ID,
            "newMyComponent",
            "foo"
          )
        ),
        "Foo2",
        setOf(
          BindsInstanceFactoryMethodParameterIndexValue(
            MY_COMPONENT_FACTORY_ID,
            "newMyComponent",
            "foo2"
          )
        ),
      )
  }

  @Test
  fun bindsInstanceBuilderMethodIndexValue_serialization() {
    val indexValue = BindsInstanceBuilderMethodIndexValue(MY_COMPONENT_FACTORY_ID, "def")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun bindsInstanceBuilderMethodIndexValue_resolveToDaggerElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.BindsInstance
      import dagger.Component

      @Component
      interface MyComponent {
        @Component.Builder
        interface Builder {
          @BindsInstance fun fooFunction(foo: Foo)

          @BindsInstance fun fooFunction2(foo: Foo, foo2: Foo)
          @BindsInstance fun fooFunction3()
          fun fooFunction4(foo: Foo)
        }

        @BindsInstance fun fooFunction5(foo: Foo)
      }

      class Foo
      """
        .trimIndent()
    )

    val fooProviderDaggerElement =
      ProviderDaggerElement(
        myFixture.findParentElement<KtParameter>("@BindsInstance fun fooFunction(fo|o: Foo)")
      )

    // Expected to resolve
    assertThat(
        BindsInstanceBuilderMethodIndexValue(MY_COMPONENT_BUILDER_ID, "fooFunction")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(fooProviderDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        MY_COMPONENT_BUILDER_ID to "fooFunction2",
        MY_COMPONENT_BUILDER_ID to "fooFunction3",
        MY_COMPONENT_BUILDER_ID to "fooFunction4",
        MY_COMPONENT_ID to "fooFunction5",
      )

    for ((classId, methodName) in nonResolving) {
      assertThat(
          BindsInstanceBuilderMethodIndexValue(classId, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope())
        )
        .isEmpty()
    }
  }

  @Test
  fun bindsInstanceBuilderMethodIndexValue_resolveToDaggerElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.BindsInstance;
      import dagger.Component;

      @Component
      interface MyComponent {
        @Component.Builder
        interface Builder {
          @BindsInstance void fooFunction(Foo foo);

          @BindsInstance void fooFunction2(Foo foo, Foo foo2);
          @BindsInstance void fooFunction3();
          void fooFunction4(Foo foo);
        }

        @BindsInstance void fooFunction5(Foo foo);
      }

      class Foo {}
      """
        .trimIndent()
    )

    val fooProviderDaggerElement =
      ProviderDaggerElement(
        myFixture.findParentElement<PsiParameter>("@BindsInstance void fooFunction(Foo f|oo);")
      )

    // Expected to resolve
    assertThat(
        BindsInstanceBuilderMethodIndexValue(MY_COMPONENT_BUILDER_ID, "fooFunction")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(fooProviderDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        MY_COMPONENT_BUILDER_ID to "fooFunction2",
        MY_COMPONENT_BUILDER_ID to "fooFunction3",
        MY_COMPONENT_BUILDER_ID to "fooFunction4",
        MY_COMPONENT_ID to "fooFunction5",
      )

    for ((classId, methodName) in nonResolving) {
      assertThat(
          BindsInstanceBuilderMethodIndexValue(classId, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope())
        )
        .isEmpty()
    }
  }

  @Test
  fun bindsInstanceFactoryMethodParameterIndexValue_serialization() {
    val indexValue =
      BindsInstanceFactoryMethodParameterIndexValue(MY_COMPONENT_FACTORY_ID, "def", "ghi")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun bindsInstanceFactoryMethodParameterIndexValue_resolveToDaggerElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.BindsInstance
      import dagger.Component

      @Component
      interface MyComponent {
        @Component.Factory
        interface Factory {
          fun newMyComponent(
            @BindsInstance foo: Foo,
            foo2: Foo): MyComponent
        }

        fun newMyComponent(
          @BindsInstance foo3: Foo,
          foo4: Foo): MyComponent
      }

      class Foo
      """
        .trimIndent()
    )

    val fooProviderDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtParameter>("f|oo: Foo"))

    // Expected to resolve
    assertThat(
        BindsInstanceFactoryMethodParameterIndexValue(
            MY_COMPONENT_FACTORY_ID,
            "newMyComponent",
            "foo"
          )
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(fooProviderDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        Triple(MY_COMPONENT_FACTORY_ID, "newMyComponent", "foo2"),
        Triple(MY_COMPONENT_ID, "newMyComponent", "foo3"),
        Triple(MY_COMPONENT_ID, "newMyComponent", "foo4"),
      )

    for ((classId, methodName, paramName) in nonResolving) {
      assertThat(
          BindsInstanceFactoryMethodParameterIndexValue(classId, methodName, paramName)
            .resolveToDaggerElements(myProject, myProject.projectScope())
        )
        .isEmpty()
    }
  }

  @Test
  fun bindsInstanceFactoryMethodParameterIndexValue_resolveToDaggerElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.BindsInstance;
      import dagger.Component;

      @Component
      interface MyComponent {
        @Component.Factory
        interface Factory {
          MyComponent newMyComponent(
            @BindsInstance Foo foo,
            Foo foo2);
        }

        MyComponent newMyComponent(
          @BindsInstance Foo foo3,
          Foo foo4);
      }

      class Foo {}
      """
        .trimIndent()
    )

    val fooProviderDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<PsiParameter>("Foo fo|o,"))

    // Expected to resolve
    assertThat(
        BindsInstanceFactoryMethodParameterIndexValue(
            MY_COMPONENT_FACTORY_ID,
            "newMyComponent",
            "foo"
          )
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(fooProviderDaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        Triple(MY_COMPONENT_FACTORY_ID, "newMyComponent", "foo2"),
        Triple(MY_COMPONENT_ID, "newMyComponent", "foo3"),
        Triple(MY_COMPONENT_ID, "newMyComponent", "foo4"),
      )

    for ((classId, methodName, paramName) in nonResolving) {
      assertThat(
          BindsInstanceFactoryMethodParameterIndexValue(classId, methodName, paramName)
            .resolveToDaggerElements(myProject, myProject.projectScope())
        )
        .isEmpty()
    }
  }

  companion object {
    private val MY_COMPONENT_ID = ClassId.fromString("com/example/MyComponent")
    private val MY_COMPONENT_BUILDER_ID = ClassId.fromString("com/example/MyComponent.Builder")
    private val MY_COMPONENT_FACTORY_ID = ClassId.fromString("com/example/MyComponent.Factory")
  }
}
