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

  @Test
  fun getRelatedDaggerElement_optionalTypes() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.addFileToProject(
      "java/util/Optional.java",
      // language=java
      """
      package java.util;
      public class Optional<T> {
        public static <T> Optional<T> empty() { return null; }
      }
      """
        .trimIndent()
    )

    myFixture.addFileToProject(
      "com/google/common/base/Optional.java",
      // language=java
      """
      package com.google.common.base;
      public class Optional<T> {
        public static <T> Optional<T> absent() { return null; }
      }
      """
        .trimIndent()
    )

    // This is not a realistic Dagger file and would not compile, since there are multiple
    // conflicting provides/binds methods. But that doesn't matter for navigation purposes, and this
    // allows us to quickly validate both positive and negative cases.
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import dagger.BindsOptionalOf
          import dagger.Lazy
          import dagger.Module
          import dagger.Provides
          import javax.inject.Inject

          typealias JavaOptional<T> = java.util.Optional<T>
          typealias GuavaOptional<T> = com.google.common.base.Optional <T>

          class Foo {}

          class Optional<T> {}

          @Module
          interface MyModule {
            @BindsOptionalOf
            fun bindOptionalFoo(): Foo

            @Provides
            fun provideJavaOptionalFoo(): JavaOptional<Foo> = JavaOptional.empty()

            @Provides
            fun provideGuavaOptionalFoo(): GuavaOptional<Foo> = GuavaOptional.absent()

            @Provides
            fun provideMyOptionalFoo(): com.example.Optional<Foo> = com.example.Optional()

            @Provides
            fun provideFoo(): Foo = Foo()
          }

          class Bar @Inject constructor(
            consumerOfJavaOptionalFoo: JavaOptional<Foo>,
            consumerOfGuavaOptionalFoo: GuavaOptional<Foo>,
            consumerOfMyOptionalFoo: com.example.Optional<Foo>,
            consumerOfFoo: Foo,
            consumerOfJavaOptionalLazyFoo: JavaOptional<Lazy<Foo>>,
          ) {}
          """
            .trimIndent()
        )
        .virtualFile
    )

    val bindOptionalFooDaggerElement =
      BindsOptionalOfProviderDaggerElement(
        myFixture.findParentElement<KtFunction>("bindOptional|Foo")
      )
    val provideJavaOptionalFooDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provideJavaOptional|Foo"))
    val provideGuavaOptionalFooDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provideGuavaOptional|Foo"))
    val provideMyOptionalFooDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provideMyOptional|Foo"))
    val provideFooDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provide|Foo"))

    val consumerOfJavaOptionalFooDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("consumerOf|JavaOptionalFoo"))
    val consumerOfGuavaOptionalFooDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("consumerOf|GuavaOptionalFoo"))
    val consumerOfMyOptionalFooDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("consumerOf|MyOptionalFoo"))
    val consumerOfFooDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("consumerOf|Foo"))
    val consumerOfJavaOptionalLazyFooDaggerElement =
      ConsumerDaggerElement(
        myFixture.findParentElement<KtParameter>("consumerOfJavaOptionalLazy|Foo")
      )

    assertThat(bindOptionalFooDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(consumerOfJavaOptionalFooDaggerElement, "Consumers"),
        DaggerRelatedElement(consumerOfGuavaOptionalFooDaggerElement, "Consumers"),
        DaggerRelatedElement(consumerOfJavaOptionalLazyFooDaggerElement, "Consumers"),
      )

    assertThat(provideJavaOptionalFooDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(consumerOfJavaOptionalFooDaggerElement, "Consumers"),
      )

    assertThat(provideGuavaOptionalFooDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(consumerOfGuavaOptionalFooDaggerElement, "Consumers"),
      )

    assertThat(provideMyOptionalFooDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(consumerOfMyOptionalFooDaggerElement, "Consumers"),
      )

    assertThat(provideFooDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(consumerOfFooDaggerElement, "Consumers"),
      )
  }

  @Test
  fun getRelatedDaggerElement_qualifiers() {
    addDaggerAndHiltClasses(myFixture)

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
          import javax.inject.Qualifier

          @Qualifier
          @Retention(value = AnnotationRetention.RUNTIME)
          annotation class Named(val value: String)

          class Foo @Inject constructor(
            unqualifiedIntConsumer: Int,
            @Named("Bert") bertIntConsumer: Int,
            @Named("Ernie") ernieIntConsumer: Int,
            unqualifiedBarConsumer: Bar,
            @Named("Bert") bertBarConsumer: Bar,
            @Named("Ernie") ernieBarConsumer: Bar,
          )

          class Bar

          @Module
          class MyModule {
            companion object {
              @Provides
              fun provideUnqualifiedInt(): Int = 0

              @Provides
              @Named("Bert")
              fun provideBertInt(): Int = 0

              @Provides
              @Named("Ernie")
              fun provideErnieInt(): Int = 0

              @Provides
              fun provideUnqualifiedBar(): Bar = Bar()

              @Provides
              @Named("Bert")
              fun provideBertBar(): Bar = Bar()

              @Provides
              @Named("Ernie")
              fun provideErnieBar(): Bar = Bar()
            }
          }
          """
            .trimIndent()
        )
        .virtualFile
    )

    val unqualifiedIntConsumerDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("unqualifiedInt|Consumer"))
    val bertIntConsumerDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("bertInt|Consumer"))
    val ernieIntConsumerDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("ernieInt|Consumer"))
    val unqualifiedBarConsumerDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("unqualifiedBar|Consumer"))
    val bertBarConsumerDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("bertBar|Consumer"))
    val ernieBarConsumerDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("ernieBar|Consumer"))

    val provideUnqualifiedIntDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provide|UnqualifiedInt"))
    val providerBertIntDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provide|BertInt"))
    val provideErnieIntDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provide|ErnieInt"))
    val provideUnqualifiedBarDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provide|UnqualifiedBar"))
    val provideBertBarDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provide|BertBar"))
    val provideErnieBarDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<KtFunction>("provide|ErnieBar"))

    assertThat(provideUnqualifiedIntDaggerElement.getRelatedDaggerElements())
      .containsExactly(DaggerRelatedElement(unqualifiedIntConsumerDaggerElement, "Consumers"))

    assertThat(providerBertIntDaggerElement.getRelatedDaggerElements())
      .containsExactly(DaggerRelatedElement(bertIntConsumerDaggerElement, "Consumers"))

    assertThat(provideErnieIntDaggerElement.getRelatedDaggerElements())
      .containsExactly(DaggerRelatedElement(ernieIntConsumerDaggerElement, "Consumers"))

    assertThat(provideUnqualifiedBarDaggerElement.getRelatedDaggerElements())
      .containsExactly(DaggerRelatedElement(unqualifiedBarConsumerDaggerElement, "Consumers"))

    assertThat(provideBertBarDaggerElement.getRelatedDaggerElements())
      .containsExactly(DaggerRelatedElement(bertBarConsumerDaggerElement, "Consumers"))

    assertThat(provideErnieBarDaggerElement.getRelatedDaggerElements())
      .containsExactly(DaggerRelatedElement(ernieBarConsumerDaggerElement, "Consumers"))
  }

  @Test
  fun canProvideForConsumer_normalType() {
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

          class Bar @Inject constructor(
            consumerOfFoo: Foo,
            consumerOfInt: Int
          ) {}
          """
            .trimIndent()
        )
        .virtualFile
    )

    val fooProviderDaggerElement =
      ProviderDaggerElement(
        myFixture.findParentElement<KtConstructor<*>>("class Foo @Inject construc|tor")
      )

    val consumerOfFooDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("consumerOf|Foo"))
    val consumerOfIntDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("consumerOf|Int"))

    assertThat(fooProviderDaggerElement.canProvideFor(consumerOfFooDaggerElement)).isTrue()
    assertThat(fooProviderDaggerElement.canProvideFor(consumerOfIntDaggerElement)).isFalse()
  }

  @Test
  fun canProvideForConsumer_wrappedType() {
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
            lazyFoo: Lazy<Foo>,
            providerFoo: Provider<Foo>,
            providerLazyFoo: Provider<Lazy<Foo>>,
          ) {}
          """
            .trimIndent()
        )
        .virtualFile
    )

    val fooProviderDaggerElement =
      ProviderDaggerElement(
        myFixture.findParentElement<KtConstructor<*>>("class Foo @Inject construc|tor")
      )

    val lazyFooDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("lazy|Foo"))
    val providerFooDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("provider|Foo"))
    val providerLazyFooDaggerElement =
      ConsumerDaggerElement(myFixture.findParentElement<KtParameter>("providerLazy|Foo"))

    assertThat(fooProviderDaggerElement.canProvideFor(lazyFooDaggerElement)).isTrue()
    assertThat(fooProviderDaggerElement.canProvideFor(providerFooDaggerElement)).isTrue()
    assertThat(fooProviderDaggerElement.canProvideFor(providerLazyFooDaggerElement)).isTrue()
  }
}
