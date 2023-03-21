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
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Ignore // TODO(b/265846405): Start running test when index is enabled
@RunWith(JUnit4::class)
@RunsInEdt
class ProviderDaggerElementTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun getRelatedDaggerElement() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import javax.inject.Inject

          class Foo @Inject constructor() {}

          class Bar @Inject constructor(foo: Foo) {}
          """
            .trimIndent()
        )
        .virtualFile
    )

    val providerPsiElement: KtConstructor<*> =
      myFixture.findParentElement("Foo @Inject const|ructor")
    val providerDaggerElement = ProviderDaggerElement(providerPsiElement)

    val consumerPsiElement: KtParameter = myFixture.findParentElement("fo|o: Foo")
    val consumerDaggerElement = ConsumerDaggerElement(consumerPsiElement)

    assertThat(providerDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(consumerDaggerElement, "Consumers"),
      )
  }

  @Test
  fun getRelatedDaggerElement_wrappingDaggerTypes() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import dagger.Lazy
          import javax.inject.Inject
          import javax.inject.Provider

          class Foo @Inject constructor() {}

          class Bar @Inject constructor(
            consumerOfLazyFoo: Lazy<Foo>,
            consumerOfProviderFoo: Provider<Foo>,
            consumerOfProviderLazyFoo: Provider<Lazy<Foo>>,
          ) {}
          """
            .trimIndent()
        )
        .virtualFile
    )

    val providerPsiElement: KtConstructor<*> =
      myFixture.findParentElement("Foo @Inject const|ructor")
    val providerDaggerElement = ProviderDaggerElement(providerPsiElement)

    val consumerOfLazyFooPsiElement: KtParameter = myFixture.findParentElement("consumerOf|LazyFoo")
    val consumerOfLazyFooDaggerElement = ConsumerDaggerElement(consumerOfLazyFooPsiElement)

    val consumerOfProviderFooPsiElement: KtParameter =
      myFixture.findParentElement("consumerOf|ProviderFoo")
    val consumerOfProviderFooDaggerElement = ConsumerDaggerElement(consumerOfProviderFooPsiElement)

    val consumerOfProviderLazyFooPsiElement: KtParameter =
      myFixture.findParentElement("consumerOf|ProviderLazyFoo")
    val consumerOfProviderLazyFooDaggerElement =
      ConsumerDaggerElement(consumerOfProviderLazyFooPsiElement)

    val relatedElements = providerDaggerElement.getRelatedDaggerElements()
    assertThat(relatedElements)
      .containsExactly(
        Pair(consumerOfLazyFooDaggerElement, "Consumers"),
        Pair(consumerOfProviderFooDaggerElement, "Consumers"),
        Pair(consumerOfProviderLazyFooDaggerElement, "Consumers"),
      )
  }

  @Test
  fun getRelatedDaggerElement_wrappingDaggerTypesWithAliases() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import dagger.Lazy
          import javax.inject.Inject
          import javax.inject.Provider

          typealias MyLazyFoo = Lazy<Foo>
          typealias MyProviderFoo = Provider<Foo>
          typealias MyProviderLazyFoo = Provider<Lazy<Foo>>

          class Foo @Inject constructor() {}

          class Bar @Inject constructor(
            consumerOfLazyFoo: MyLazyFoo,
            consumerOfProviderFoo: MyProviderFoo,
            consumerOfProviderLazyFoo: MyProviderLazyFoo,
          ) {}
          """
            .trimIndent()
        )
        .virtualFile
    )

    val providerPsiElement: KtConstructor<*> =
      myFixture.findParentElement("Foo @Inject const|ructor")
    val providerDaggerElement = ProviderDaggerElement(providerPsiElement)

    val consumerOfLazyFooPsiElement: KtParameter = myFixture.findParentElement("consumerOf|LazyFoo")
    val consumerOfLazyFooDaggerElement = ConsumerDaggerElement(consumerOfLazyFooPsiElement)

    val consumerOfProviderFooPsiElement: KtParameter =
      myFixture.findParentElement("consumerOf|ProviderFoo")
    val consumerOfProviderFooDaggerElement = ConsumerDaggerElement(consumerOfProviderFooPsiElement)

    val consumerOfProviderLazyFooPsiElement: KtParameter =
      myFixture.findParentElement("consumerOf|ProviderLazyFoo")
    val consumerOfProviderLazyFooDaggerElement =
      ConsumerDaggerElement(consumerOfProviderLazyFooPsiElement)

    val relatedElements = providerDaggerElement.getRelatedDaggerElements()
    assertThat(relatedElements)
      .containsExactly(
        Pair(consumerOfLazyFooDaggerElement, "Consumers"),
        Pair(consumerOfProviderFooDaggerElement, "Consumers"),
        Pair(consumerOfProviderLazyFooDaggerElement, "Consumers"),
      )
  }

  @Test
  fun getRelatedDaggerElement_nullableTypes() {
    addDaggerAndHiltClasses(myFixture)

    // This is technically not a valid set of consumers/providers, since there are multiple
    // providers for the same type and some providers don't work for all the consumers (eg, a
    // provider of `Foo?` can't provide for a consumer of `Foo`.) But for the purpose of navigation,
    // we want to show all the relationships regardless of nullability.
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import dagger.Module
          import dagger.Provides
          import javax.inject.Inject

          typealias MyNullableFoo = Foo?

          class Foo {}

          @Module
          interface MyModule {
            @Provides
            fun provideFoo(): Foo = Foo()

            @Provides
            fun provideNullableFoo(): Foo? = Foo()

            @Provides
            fun provideMyNullableFoo(): MyNullableFoo = Foo()
          }

          class Bar @Inject constructor(
            consumerOfFoo: Foo,
            consumerOfNullableFoo: Foo?,
            consumerOfMyNullableFoo: MyNullableFoo,
          ) {}
          """
            .trimIndent()
        )
        .virtualFile
    )

    val provideFooPsiElement: KtFunction = myFixture.findParentElement("provide|Foo")
    val provideFooDaggerElement = ProviderDaggerElement(provideFooPsiElement)

    val provideNullableFooPsiElement: KtFunction =
      myFixture.findParentElement("provide|NullableFoo")
    val provideNullableFooDaggerElement = ProviderDaggerElement(provideNullableFooPsiElement)

    val provideMyNullableFooPsiElement: KtFunction =
      myFixture.findParentElement("provide|MyNullableFoo")
    val provideMyNullableFooDaggerElement = ProviderDaggerElement(provideMyNullableFooPsiElement)

    val consumerOfFooPsiElement: KtParameter = myFixture.findParentElement("consumerOf|Foo")
    val consumerOfFooDaggerElement = ConsumerDaggerElement(consumerOfFooPsiElement)

    val consumerOfNullableFooPsiElement: KtParameter =
      myFixture.findParentElement("consumerOf|NullableFoo")
    val consumerOfNullableFooDaggerElement = ConsumerDaggerElement(consumerOfNullableFooPsiElement)

    val consumerOfMyNullableFooPsiElement: KtParameter =
      myFixture.findParentElement("consumerOf|MyNullableFoo")
    val consumerOfMyNullableFooDaggerElement =
      ConsumerDaggerElement(consumerOfMyNullableFooPsiElement)

    val provideFooRelatedElements = provideFooDaggerElement.getRelatedDaggerElements()
    assertThat(provideFooRelatedElements)
      .containsExactly(
        DaggerRelatedElement(consumerOfFooDaggerElement, "Consumers"),
        DaggerRelatedElement(consumerOfNullableFooDaggerElement, "Consumers"),
        DaggerRelatedElement(consumerOfMyNullableFooDaggerElement, "Consumers"),
      )

    assertThat(provideNullableFooDaggerElement.getRelatedDaggerElements())
      .containsExactlyElementsIn(provideFooRelatedElements)

    assertThat(provideMyNullableFooDaggerElement.getRelatedDaggerElements())
      .containsExactlyElementsIn(provideFooRelatedElements)
  }
}
