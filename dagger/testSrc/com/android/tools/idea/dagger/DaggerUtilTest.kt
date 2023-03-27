/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.dagger

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.findParentElement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiParameter
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import java.io.File
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.After
import org.junit.Before

class DaggerUtilBuiltInAnnotationSearchTest : DaggerUtilTest() {
  override val daggerBuiltInSearchEnabled = true
}

class DaggerUtilCustomAnnotationSearchTest : DaggerUtilTest() {
  override val daggerBuiltInSearchEnabled = false
}

abstract class DaggerUtilTest : DaggerTestCase() {

  abstract val daggerBuiltInSearchEnabled: Boolean

  @Before
  override fun setUp() {
    super.setUp()
    StudioFlags.DAGGER_BUILT_IN_SEARCH_ENABLED.override(daggerBuiltInSearchEnabled)
  }

  @After
  override fun tearDown() {
    StudioFlags.DAGGER_BUILT_IN_SEARCH_ENABLED.clearOverride()
    super.tearDown()
  }

  private fun getProvidersForInjectedField_kotlin(
    fieldType: String,
    qualifier: String = ""
  ): Collection<PsiNamedElement> {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import javax.inject.Inject

        class MyClass {
          @Inject
          $qualifier
          val injectedField:${fieldType}
        }
      """
        .trimIndent()
    )

    return getDaggerProvidersFor(myFixture.findParentElement<KtProperty>("injectedF|ield"))
      as Collection<PsiNamedElement>
  }

  private fun getProvidersForInjectedField(
    fieldType: String,
    qualifier: String = ""
  ): Collection<PsiNamedElement> {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        class MyClass {
          @Inject
          $qualifier
          ${fieldType} injectedField;
        }
      """
        .trimIndent()
    )
    return getDaggerProvidersFor(myFixture.findParentElement<PsiField>("injected|Field"))
      as Collection<PsiNamedElement>
  }

  fun testIsConsumerForInjectField() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        class MyClass {
          @Inject String injectedString;
          String notInjectedString;
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<PsiField>("injected|String").isDaggerConsumer).isTrue()
    assertThat(myFixture.findParentElement<PsiField>("notInjected|String").isDaggerConsumer)
      .isFalse()
  }

  fun testIsConsumerForInjectField_kotlin() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import javax.inject.Inject

        class MyClass {
          @Inject val injectedString:String
          val notInjectedString:String
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<KtProperty>("injected|String").isDaggerConsumer).isTrue()
    assertThat(myFixture.findParentElement<KtProperty>("notInjected|String").isDaggerConsumer)
      .isFalse()
  }

  fun testIsConsumer_injectedConstructorParam() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        public class MyClass {
          @Inject public MyClass(String consumer) {}
          public MyClass(int notConsumer) {}
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<PsiParameter>("consum|er").isDaggerConsumer).isTrue()
    assertThat(myFixture.findParentElement<PsiParameter>("notConsum|er").isDaggerConsumer).isFalse()

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import androidx.hilt.lifecycle.ViewModelInject;

        public class MyViewClass {
          @ViewModelInject public MyViewClass(String consumer) {}
          public MyViewClass(int notConsumer) {}
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<PsiParameter>("consum|er").isDaggerConsumer).isTrue()
    assertThat(myFixture.findParentElement<PsiParameter>("notConsum|er").isDaggerConsumer).isFalse()

    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import androidx.hilt.lifecycle.ViewModelInject

        class MyViewClassKt @ViewModelInject constructor(consumer: String)
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<KtParameter>("consum|er").isDaggerConsumer).isTrue()

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import androidx.hilt.work.WorkerInject;

        public class MyWorkerClass {
          @WorkerInject public MyWorkerClass(String consumer) {}
          public MyWorkerClass(int notConsumer) {}
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<PsiParameter>("consum|er").isDaggerConsumer).isTrue()
    assertThat(myFixture.findParentElement<PsiParameter>("notConsum|er").isDaggerConsumer).isFalse()
  }

  fun testIsConsumer_injectedConstructorParam_kotlin() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import javax.inject.Inject

        class MyClass @Inject constructor(consumer:String) {
          constructor(notConsumer: Int)
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<KtParameter>("consum|er").isDaggerConsumer).isTrue()
    assertThat(myFixture.findParentElement<KtParameter>("notConsum|er").isDaggerConsumer).isFalse()
  }

  fun testIsConsumer_isAssistedInjectedConstructor() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
      import dagger.assisted.Assisted;
      import dagger.assisted.AssistedInject;

      public class FooJava {
          private String id;

          @AssistedInject public FooJava(
              String repository,
              @Assisted String id
          ) {
              this.id = id;
          }

          public FooJava(String id) {
              this.id = id;
          }
      }
    """
        .trimIndent()
    )
    assertThat(myFixture.findParentElement<PsiParameter>("String reposi|tory,").isDaggerConsumer)
      .isTrue()
    assertThat(
        myFixture.findParentElement<PsiParameter>("public FooJava(String i|d) {").isDaggerConsumer
      )
      .isFalse()
  }

  fun testIsConsumer_isAssistedInjectedConstructor_kotlin() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject

      class Foo @AssistedInject constructor(
          repository: String,
          @Assisted val id: String
      ) {
          constructor(id: String, nothing: String) : this(id) {
              //Do Nothing
          }
      }
    """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<KtParameter>("repos|itory: String").isDaggerConsumer)
      .isTrue()
    assertThat(
        myFixture
          .findParentElement<KtParameter>("constructor(i|d: String, nothing: String)")
          .isDaggerConsumer
      )
      .isFalse()
    assertThat(
        myFixture
          .findParentElement<KtParameter>("constructor(id: String, noth|ing: String)")
          .isDaggerConsumer
      )
      .isFalse()
  }

  fun testIsProvider_providesMethod() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyClass {
          @Provides String provider() {}
          String notProvider() {}
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<PsiMethod>("provide|r").isDaggerProvider).isTrue()
    assertThat(myFixture.findParentElement<PsiMethod>("notProv|ider").isDaggerProvider).isFalse()
  }

  fun testIsProvider_kotlin_providesMethod() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides
        import dagger.Module

        @Module
        class MyClass {
          @Provides fun provider() {}
          fun notProvider() {}
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<KtFunction>("provide|r").isDaggerProvider).isTrue()
    assertThat(myFixture.findParentElement<KtFunction>("notProv|ider").isDaggerProvider).isFalse()
  }

  fun testIsProvider_injectedConstructor() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        public class MyClass {
          @Inject public MyClass() {}
          public MyClass(String s) {}
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<PsiMethod>("public MyCl|ass()").isDaggerProvider)
      .isTrue()
    assertThat(myFixture.findParentElement<PsiMethod>("public MyCl|ass(String s)").isDaggerProvider)
      .isFalse()
  }

  fun testIsProvider_kotlin_injectedConstructor() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import javax.inject.Inject

        class MyClass @Inject constructor()
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<KtFunction>("construc|tor").isDaggerProvider).isTrue()

    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import javax.inject.Inject

        class MyClass(s: String) {
          @Inject constructor()
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<KtFunction>("construc|tor").isDaggerProvider).isTrue()
  }

  fun testIsProvider_bindsMethod() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Binds;

        abstract class MyClass {
          @Binds abstract String bindsMethod() {}
          abstract String notBindsMethod() {}
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<PsiMethod>("bindsMet|hod").isDaggerProvider).isTrue()
    assertThat(myFixture.findParentElement<PsiMethod>("notBindsMet|hod").isDaggerProvider).isFalse()
  }

  fun testIsProvider_kotlin_bindsMethod() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Binds

        abstract class MyClass {
          @Binds abstract fun bindsMethod() {}
          fun notBindsMethod() {}
        }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<KtFunction>("bindsMet|hod").isDaggerProvider).isTrue()
    assertThat(myFixture.findParentElement<KtFunction>("notBindsMet|hod").isDaggerProvider)
      .isFalse()
  }

  fun testIsProvider_isDaggerAssistedFactory() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
      import dagger.assisted.AssistedFactory;

      @AssistedFactory
      public interface FooFactoryJava {
          Foo create(String id);
          void createNothing();
      }
      """
        .trimIndent()
    )

    assertThat(myFixture.findParentElement<PsiClass>("FooFac|toryJava").isDaggerProvider).isTrue()

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
      public interface NotFooFactoryJava {
          Foo create(String id);
          void createNothing();
      }
      """
        .trimIndent()
    )
    assertThat(myFixture.findParentElement<PsiClass>("NotFooFactor|yJava").isDaggerProvider)
      .isFalse()
  }

  fun testIsProvider_kotlin_isDaggerAssistedFactory() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
      import dagger.assisted.AssistedFactory

      @AssistedFactory
      interface FooFactory {
          // Is a factory method
          fun create(id: String): Foo

          // Is not a factory method (returns null)
          fun createNothing(id: String)
      }

      interface NotFactory {
          // Is not a factory method (NotFactory is not annotated with @AssistedFactory)
          fun create(): Foo
      }
    """
        .trimIndent()
    )

    assertThat(
        myFixture.findParentElement<KtClassOrObject>("interface Foo|Factory {").isDaggerProvider
      )
      .isTrue()
    assertThat(
        myFixture.findParentElement<KtClassOrObject>("interface Not|Factory {").isDaggerProvider
      )
      .isFalse()
  }

  fun testIsAssistedFactoryMethod() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
      import dagger.assisted.AssistedFactory;

      @AssistedFactory
      public interface FooFactoryJava {
          Foo create(String id);
          void createNothing();
      }
      """
        .trimIndent()
    )

    assertThat(
        myFixture.findParentElement<PsiMethod>("Foo cre|ate(String id);").isAssistedFactoryMethod
      )
      .isTrue()
    assertThat(
        myFixture.findParentElement<PsiMethod>("void cre|ateNothing();").isAssistedFactoryMethod
      )
      .isFalse()

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
      public interface NotFactory {
          Foo create(String id);
      }
      """
        .trimIndent()
    )
    assertThat(
        myFixture.findParentElement<PsiMethod>("Foo crea|te(String id);").isAssistedFactoryMethod
      )
      .isFalse()
  }

  fun testIsAssistedFactoryMethod_kotlin() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
      import dagger.assisted.AssistedFactory

      @AssistedFactory
      interface FooFactory {
          // Is a factory method
          fun create(id: String): Foo

          // Is not a factory method (returns null)
          fun createNothing(id: String)
      }

      interface NotFactory {
          // Is not a factory method (NotFactory is not annotated with @AssistedFactory)
          fun create(): Foo
      }
    """
        .trimIndent()
    )

    assertThat(
        myFixture
          .findParentElement<KtFunction>("fun cre|ate(id: String): Foo")
          .isAssistedFactoryMethod
      )
      .isTrue()
    assertThat(
        myFixture
          .findParentElement<KtFunction>("fun creat|eNothing(id: String)")
          .isAssistedFactoryMethod
      )
      .isFalse()
    assertThat(
        myFixture.findParentElement<KtFunction>("fun cre|ate(): Foo").isAssistedFactoryMethod
      )
      .isFalse()
  }

  fun testIsAssistedInjectedConstructor() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
      import dagger.assisted.Assisted;
      import dagger.assisted.AssistedInject;

      public class FooJava {
          private String id;

          @AssistedInject public FooJava(@Assisted String id) {
              this.id = id;
          }

          public FooJava() {
              this.id = "id";
          }
      }
    """
        .trimIndent()
    )

    assertThat(
        myFixture
          .findParentElement<PsiMethod>("@AssistedInject public FooJ|ava(@Assisted String id) {")
          .isAssistedInjectedConstructor
      )
      .isTrue()
    assertThat(
        myFixture.findParentElement<PsiMethod>("public FooJ|ava() {").isAssistedInjectedConstructor
      )
      .isFalse()
  }

  fun testIsAssistedInjectedConstructor_kotlin() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject

      class Foo @AssistedInject constructor(
          @Assisted val id: String
      ) {
          constructor(id: String, nothing: String) : this(id) {
              //Do Nothing
          }
      }
    """
        .trimIndent()
    )

    assertThat(
        myFixture
          .findParentElement<KtConstructor<*>>("class Foo @AssistedInject construc|tor(")
          .isAssistedInjectedConstructor
      )
      .isTrue()
    assertThat(
        myFixture
          .findParentElement<KtConstructor<*>>(
            "constr|uctor(id: String, nothing: String) : this(id) {"
          )
          .isAssistedInjectedConstructor
      )
      .isFalse()
  }

  fun testGetDaggerAssistedFactoryMethodForAssistedProvider() {
    myFixture.addFileToProject(
      "FooFactoryJava.java",
      // language=JAVA
      """
      import dagger.assisted.AssistedFactory;

      @AssistedFactory
      public interface FooFactoryJava {
          FooJava create(String id);
          void createNothing();
      }
      """
        .trimIndent()
    )
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=JAVA
      """
      import dagger.assisted.Assisted;
      import dagger.assisted.AssistedInject;

      public class FooJava {
          @AssistedInject public FooJava(@Assisted String id) {}
      }
    """
        .trimIndent()
    )
    val methodList =
      getDaggerAssistedFactoryMethodsForAssistedInjectedConstructor(
          myFixture.findParentElement<PsiMethod>(
            "@AssistedInject public Foo|Java(@Assisted String id)"
          )
        )
        .toList()

    assertThat(methodList).hasSize(1)
    assertThat(methodList[0].name).isEqualTo("create")
    assertThat(methodList[0].containingClass!!.name).isEqualTo("FooFactoryJava")
  }

  fun testGetDaggerAssistedFactoryMethodForAssistedProvider_kotlin() {
    myFixture.addFileToProject(
      "FooFactory.kt",
      // language=kotlin
      """
      import dagger.assisted.AssistedFactory

      @AssistedFactory
      interface FooFactory {
          // Is a factory method
          fun create(id: String): Foo

          // Is not a factory method (returns null)
          fun createNothing(id: String)
      }
    """
        .trimIndent()
    )
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject

      class Foo @AssistedInject constructor(
          @Assisted val id: String
      )
    """
        .trimIndent()
    )
    val methodList =
      getDaggerAssistedFactoryMethodsForAssistedInjectedConstructor(
          myFixture.findParentElement<KtConstructor<*>>("class Foo @AssistedInject cons|tructor")
        )
        .toList()

    assertThat(methodList).hasSize(1)
    assertThat(methodList[0].name).isEqualTo("create")
    assertThat(methodList[0].containingClass!!.name).isEqualTo("FooFactory")
  }

  fun testGetDaggerProviders_providesMethod() {
    // JAVA provider.
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyClass {
          @Provides String provider() {}
        }
      """
        .trimIndent()
    )

    val provider: PsiMethod = myFixture.findParentElement("provide|r")

    // Consumer in JAVA.
    var providers = getProvidersForInjectedField("String")
    assertThat(providers).hasSize(1)
    assertThat(providers.first()).isEqualTo(provider)

    // Consumer in kotlin.
    providers = getProvidersForInjectedField_kotlin("String")
    assertThat(providers).hasSize(1)
    assertThat(providers.first()).isEqualTo(provider)
  }

  fun testGetDaggerProviders_kotlin_providesMethod() {
    // Kotlin provider.
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides
        import dagger.Module

        @Module
        class MyClass {
          @Provides fun provider():String {}
        }
      """
        .trimIndent()
    )

    val provider = myFixture.findParentElement<KtFunction>("provid|er")?.toLightElements()?.first()

    assume().that(provider).isNotNull()
    // We will compare with string representation, because ide returns different instances of light
    // class.
    val tag =
      if (isK2Plugin()) "SymbolLightSimpleMethod" else "KtUltraLightMethodForSourceDeclaration"
    assume().that(provider.toString()).isEqualTo("$tag:provider")

    // Consumer in JAVA.
    var providers = getProvidersForInjectedField("String")
    assertThat(providers).hasSize(1)
    assertThat(providers.first().toString()).isEqualTo(provider.toString())

    // Consumer in kotlin.
    providers = getProvidersForInjectedField_kotlin("String")
    assertThat(providers).hasSize(1)
    assertThat(providers.first().toString()).isEqualTo(provider.toString())
  }

  fun testGetDaggerProviders_bindsMethod() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Binds;
        import dagger.Module;

        @Module
        abstract class MyClass {
          @Binds abstract String bindsMethod() {}
        }
      """
        .trimIndent()
    )

    val provider: PsiMethod = myFixture.findParentElement("bindsMet|hod")

    // Consumer in JAVA.
    var providers = getProvidersForInjectedField("String")
    assertThat(providers).hasSize(1)
    assertThat(providers.first()).isEqualTo(provider)

    // Consumer in kotlin.
    providers = getProvidersForInjectedField_kotlin("String")
    assertThat(providers).hasSize(1)
    assertThat(providers.first()).isEqualTo(provider)
  }

  fun testGetDaggerProviders_kotlin_bindsMethod() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Binds
        import dagger.Module

        @Module
        abstract class MyClass {
          @Binds abstract fun bindsMethod():String {}
          fun notBindsMethod():String {}
        }
      """
        .trimIndent()
    )

    val provider =
      myFixture.findParentElement<KtFunction>("bindsMeth|od")?.toLightElements()?.first()

    assume().that(provider).isNotNull()
    // We will compare with string representation, because ide returns different instances of light
    // class.
    assume().that(provider.toString()).isNotEmpty()

    // Consumer in JAVA.
    var providers = getProvidersForInjectedField("String")
    assertThat(providers).hasSize(1)
    assertThat(providers.first().toString()).isEqualTo(provider.toString())

    // Consumer in kotlin.
    providers = getProvidersForInjectedField_kotlin("String")
    assertThat(providers).hasSize(1)
    assertThat(providers.first().toString()).isEqualTo(provider.toString())
  }

  fun testGetDaggerProviders_injectedConstructor() {
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        public class MyClassWithInjectedConstructor {
          @Inject public MyClassWithInjectedConstructor() {}
        }
      """
        .trimIndent()
    )

    val provider: PsiMethod = myFixture.findParentElement("MyClassWithInjectedConstru|ctor()")

    // Consumer in JAVA.
    var providers = getProvidersForInjectedField("MyClassWithInjectedConstructor")
    assertThat(providers).hasSize(1)
    assertThat(providers.first()).isEqualTo(provider)

    // Consumer in kotlin.
    providers = getProvidersForInjectedField_kotlin("MyClassWithInjectedConstructor")
    assertThat(providers).hasSize(1)
    assertThat(providers.first()).isEqualTo(provider)
  }

  fun testGetDaggerProviders_kotlin_injectedConstructor() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import javax.inject.Inject

        class MyClassWithInjectedConstructor @Inject constructor()
      """
        .trimIndent()
    )

    val provider =
      myFixture.findParentElement<KtFunction>("construct|or()")?.toLightElements()?.first()

    assume().that(provider).isNotNull()
    // We will compare with string representation, because ide returns different instances of light
    // class.
    assume().that(provider.toString()).isNotEmpty()

    // Consumer in JAVA.
    var providers = getProvidersForInjectedField("MyClassWithInjectedConstructor")
    assertThat(providers).hasSize(1)
    assertThat(providers.first().toString()).isEqualTo(provider.toString())

    // Consumer in kotlin.
    providers = getProvidersForInjectedField_kotlin("MyClassWithInjectedConstructor")
    assertThat(providers).hasSize(1)
    assertThat(providers.first().toString()).isEqualTo(provider.toString())
  }

  fun testGetDaggerProviders_for_param() {
    // JAVA provider.
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyClass {
          @Provides String provider() {}
        }
      """
        .trimIndent()
    )

    val provider: PsiMethod = myFixture.findParentElement("provide|r")

    // Consumer in Kotlin.
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import javax.inject.Inject

        class MyClass @Inject constructor(consumer:String)
      """
        .trimIndent()
    )

    val providers = getDaggerProvidersFor(myFixture.findParentElement<KtParameter>("consum|er"))
    assertThat(providers).hasSize(1)
    assertThat(providers.first()).isEqualTo(provider)
  }

  fun testSimpleQualifier() {
    myFixture.addClass(
      // language=JAVA
      """
      package test;

      import javax.inject.Qualifier;

      @Qualifier
      public @interface MyQualifier {}
    """
        .trimIndent()
    )

    // JAVA providers.
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;
        import test.MyQualifier;

        @Module
        class MyClass {
          @Provides @MyQualifier String providerWithQualifier() {}
          @Provides String provider() {}
        }
      """
        .trimIndent()
    )

    // Kotlin providers.
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides
        import dagger.Module
        import test.MyQualifier

        @Module
        class MyClass {
          @Provides @MyQualifier fun providerWithQualifier_kotlin():String {}
          @Provides fun provider_kotlin():String {}
        }
      """
        .trimIndent()
    )

    val providersForJavaConsumer =
      getProvidersForInjectedField("String", "@test.MyQualifier").map { it.name }
    assertThat(providersForJavaConsumer)
      .containsExactly("providerWithQualifier", "providerWithQualifier_kotlin")

    val providersForKotlinConsumer =
      getProvidersForInjectedField_kotlin("String", "@test.MyQualifier").map { it.name }
    assertThat(providersForKotlinConsumer)
      .containsExactly("providerWithQualifier", "providerWithQualifier_kotlin")
  }

  fun testQualifier() {
    myFixture.addClass(
      // language=JAVA
      """
      package test;

      public enum MyEnum { ONE, TWO }
    """
        .trimIndent()
    )

    myFixture.addClass(
      // language=JAVA
      """
      package test;

      import javax.inject.Qualifier;

      @Qualifier
      public @interface ComplicatedQualifier {

        Class classAttr();
        String stringAttr();
        test.MyEnum enumAttr();
        Class[] classArrayAttr();
        int intAttr();
    }
    """
        .trimIndent()
    )

    val javaQualifier =
      """
      @test.ComplicatedQualifier(
        stringAttr = "value",
        // java.lang.String
        classAttr = String.class,
        enumAttr = test.MyEnum.ONE,
        classArrayAttr = {String.class},
        intAttr = 1
      )
    """
        .trimIndent()

    val kotlinQualifier =
      """
      @test.ComplicatedQualifier(
        stringAttr = "value",
        // kotlin.String
        classAttr = String::class,
        enumAttr = test.MyEnum.ONE,
        classArrayAttr = [String::class],
        intAttr = 1
      )
    """
        .trimIndent()

    // JAVA providers.
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;
        import test.MyQualifier;

        @Module
        class MyClass {
          @Provides $javaQualifier String providerWithQualifier() {}
          @Provides String provider() {}
        }
      """
        .trimIndent()
    )

    // Kotlin providers.
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides
        import dagger.Module
        import test.MyQualifier

        @Module
        class MyClass {
          @Provides $kotlinQualifier fun providerWithQualifier_kotlin():String {}
          @Provides fun provider_kotlin():String {}
        }
      """
        .trimIndent()
    )

    val providersForJavaConsumer =
      getProvidersForInjectedField("String", javaQualifier).map { it.name }
    assertThat(providersForJavaConsumer)
      .containsExactly("providerWithQualifier", "providerWithQualifier_kotlin")

    val providersForKotlinConsumer =
      getProvidersForInjectedField_kotlin("String", kotlinQualifier).map { it.name }
    assertThat(providersForKotlinConsumer)
      .containsExactly("providerWithQualifier", "providerWithQualifier_kotlin")
  }

  fun testDaggerComponentMethodsForProvider() {
    val classFile =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;

      import javax.inject.Inject;

      public class MyClass {
        @Inject public MyClass() {}
      }
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    myFixture.addClass(
      // language=JAVA
      """
      package test;

      import javax.inject.Qualifier;

      @Qualifier
      public @interface MyQualifier {}
    """
        .trimIndent()
    )

    val moduleFile =
      myFixture
        .addClass(
          // language=JAVA
          """
        package test;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides @MyQualifier MyClass providerWithQualifier() {}
        }
      """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {
        @MyQualifier MyClass getMyClassWithQualifier();
        MyClass getMyClass();
      }
    """
        .trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(classFile)
    val classProvider: PsiMethod = myFixture.findParentElement("@Inject public MyCla|ss")

    var methodsForProvider = getDaggerComponentMethodsForProvider(classProvider).map { it.name }
    assertThat(methodsForProvider).containsExactly("getMyClass")

    myFixture.configureFromExistingVirtualFile(moduleFile)
    val providerWithQualifier: PsiMethod =
      myFixture.findParentElement("@Provides @MyQualifier MyClass provider|WithQualifier")

    methodsForProvider = getDaggerComponentMethodsForProvider(providerWithQualifier).map { it.name }
    assertThat(methodsForProvider).containsExactly("getMyClassWithQualifier")
  }

  fun testGetComponentsForModule() {
    val moduleFile =
      myFixture
        .addClass(
          // language=JAVA
          """
        package test;
        import dagger.Module;

        @Module
        class MyModule {}
      """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    val kotlinModuleFile =
      myFixture
        .configureByText(
          "text/MyModuleKt.kt",
          // language=kotlin
          """
        package test
        import dagger.Module

        @Module
        class MyModuleKt
      """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    // Java Component
    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class, MyModuleKt.class })
      public interface MyComponent {}
    """
        .trimIndent()
    )

    // Kotlin Component
    myFixture.addFileToProject(
      "test/MyComponentKt.kt",
      // language=kotlin
      """
      package test
      import dagger.Component

      @Component(modules = [MyModule::class, MyModuleKt::class])
      interface MyComponentKt
    """
        .trimIndent()
    )

    // Kotlin Subcomponent
    myFixture.addFileToProject(
      "test/MySubcomponentKt.kt",
      // language=kotlin
      """
      package test
      import dagger.Subcomponent

      @Subcomponent(modules = [MyModule::class, MyModuleKt::class])
      interface MySubcomponentKt
    """
        .trimIndent()
    )

    // Java Module
    myFixture.addClass(
      // language=JAVA
      """
        package test;
        import dagger.Module;

        @Module(includes = { MyModule.class, MyModuleKt.class })
        class MyModule2 {}
      """
        .trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(moduleFile)
    var components =
      getUsagesForDaggerModule(myFixture.findParentElement<PsiClass>("class MyMod|ule {}"))
    assertThat(components).hasSize(4)
    assertThat(components.map { it.name })
      .containsAllOf("MyComponentKt", "MyComponent", "MySubcomponentKt", "MyModule2")

    myFixture.configureFromExistingVirtualFile(kotlinModuleFile)
    components =
      getUsagesForDaggerModule(
        myFixture.findParentElement<KtClass>("class MyMod|uleKt").toLightClass()!!
      )
    assertThat(components).hasSize(4)
    assertThat(components.map { it.name })
      .containsAllOf("MyComponentKt", "MyComponent", "MySubcomponentKt", "MyModule2")
  }

  fun testGetDependantComponentsForComponent() {
    // Java Component
    val componentFile =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;
      import dagger.Component;

      @Component
      public interface MyComponent {}
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    // Kotlin Component
    val kotlinComponentFile =
      myFixture
        .addFileToProject(
          "test/MyComponentKt.kt",
          // language=kotlin
          """
      package test
      import dagger.Component

      @Component
      interface MyComponentKt
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    // Kotlin Dependant Component
    myFixture.addFileToProject(
      "test/MyDependantComponent.kt",
      // language=kotlin
      """
      package test
      import dagger.Component

      @Component(dependencies = [MyComponent::class, MyComponentKt::class])
      interface MyDependantComponent
    """
        .trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(componentFile)
    var components =
      getDependantComponentsForComponent(myFixture.findParentElement("interface MyCompon|ent {}"))
    assertThat(components).hasSize(1)
    assertThat(components.map { it.name }).contains("MyDependantComponent")

    myFixture.configureFromExistingVirtualFile(kotlinComponentFile)
    components =
      getDependantComponentsForComponent(
        myFixture.findParentElement<KtClass>("interface MyCompon|entKt").toLightClass()!!
      )
    assertThat(components).hasSize(1)
    assertThat(components.map { it.name }).contains("MyDependantComponent")
  }

  fun testIsProvider_BindsInstance() {
    // Java Module
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=JAVA
      """
        package test;
        import dagger.Module;
        import dagger.Component;
        import dagger.BindsInstance;

        @Module
        class MyModule2 {
          @BindsInstance String bindsString();
        }
      """
        .trimIndent()
    )

    val bindsString: PsiMethod = myFixture.findParentElement("bindsSt|ring")
    assertThat(bindsString.isDaggerProvider).isTrue()

    // Java Component
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=JAVA
      """
        package test;
        import dagger.Component;
        import dagger.BindsInstance;

        @Component
        interface Component {
          String stringBuilder(@BindsInstance String string) {}
        }
      """
        .trimIndent()
    )

    val string: PsiParameter = myFixture.findParentElement("@BindsInstance String st|ring")
    assertThat(string.isDaggerProvider).isTrue()

    // Kotlin Module and Component.
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.BindsInstance
        import dagger.Component
        import dagger.Module

        @Module
        class MyClass {
          @BindsInstance fun bindsStringKt():String {}
        }

        @Component
        interface AppComponent {
          @Component.Factory
          interface Factory {
              fun create(@BindsInstance stringKt: String): AppComponent
          }
        }
      """
        .trimIndent()
    )

    val bindsStringKt: KtFunction = myFixture.findParentElement("bindsSt|ringKt")
    assertThat(bindsStringKt.isDaggerProvider).isTrue()

    val stringKt: KtParameter = myFixture.findParentElement("string|Kt")
    assertThat(stringKt.isDaggerProvider).isTrue()
  }

  fun testDaggerProviders_kotlin_importAlias() {
    myFixture.addClass(
      // language=JAVA
      """
      package test;

      public class MyClass{}

    """
        .trimIndent()
    )

    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides
        import dagger.Module
        import test.MyClass as alias

        @Module
        class MyClass {
          @Provides fun aliasProvider():alias {}
        }
      """
        .trimIndent()
    )

    val providers = getProvidersForInjectedField("test.MyClass")
    assertThat(providers.single().name).isEqualTo("aliasProvider")
  }

  fun testDaggerProviders_kotlin_arrays() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides
        import dagger.Module

        @Module
        class MyClass {
          @Provides fun primitiveArrayProvider():IntArray {}
          @Provides fun arrayProvider():Array<Int> {}
        }
      """
        .trimIndent()
    )

    var providers = getProvidersForInjectedField("int[]")
    assertThat(providers.single().name).isEqualTo("primitiveArrayProvider")

    providers = getProvidersForInjectedField("Integer[]")
    assertThat(providers.single().name).isEqualTo("arrayProvider")
  }

  fun testGetDaggerProviders_kotlin_bindsInstanceMethod() {
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.BindsInstance
        import dagger.Module

        @Module
        abstract class MyClass {
          @BindsInstance abstract fun bindsMethod():String {}
          fun builder(@BindsInstance str:String) {}
        }
      """
        .trimIndent()
    )

    // Consumer in JAVA.
    var providers = getProvidersForInjectedField("String")
    assertThat(providers).hasSize(2)
    assertThat(providers.map { it.name }).containsExactly("bindsMethod", "str")

    // Consumer in kotlin.
    providers = getProvidersForInjectedField_kotlin("String")
    assertThat(providers).hasSize(2)
    assertThat(providers.map { it.name }).containsExactly("bindsMethod", "str")
  }

  fun testGetDaggerProviders_bindsInstanceMethod() {
    // Java Module
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=JAVA
      """
        package test;
        import dagger.Module;
        import dagger.Component;
        import dagger.BindsInstance;

        @Module
        class MyModule2 {
          @BindsInstance String bindsString();
        }
      """
        .trimIndent()
    )

    // Java Component
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=JAVA
      """
        package test;
        import dagger.Component;
        import dagger.BindsInstance;

        @Component
        interface Component {
          String stringBuilder(@BindsInstance String string) {}
        }
      """
        .trimIndent()
    )

    // Consumer in JAVA.
    var providers = getProvidersForInjectedField("String")
    assertThat(providers).hasSize(2)
    assertThat(providers.map { it.name }).containsExactly("bindsString", "string")

    // Consumer in kotlin.
    providers = getProvidersForInjectedField_kotlin("String")
    assertThat(providers).hasSize(2)
    assertThat(providers.map { it.name }).containsExactly("bindsString", "string")
  }

  fun testUnboxTypes() {
    // JAVA provider.
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyClass {
          @Provides Integer provider() {}
          @Provides int provider2() {}
        }
      """
        .trimIndent()
    )
    // Kotlin provider.
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides
        import dagger.Module

        @Module
        class MyClassKt {
          @Provides fun provider():Int? {}
          @Provides fun provider2():Int {}
        }
      """
        .trimIndent()
    )

    // Consumer in JAVA.
    assertThat(getProvidersForInjectedField("int")).hasSize(4)
    assertThat(getProvidersForInjectedField("Integer")).hasSize(4)

    // Consumer in kotlin.
    assertThat(getProvidersForInjectedField_kotlin("Int")).hasSize(4)
    assertThat(getProvidersForInjectedField_kotlin("Int?")).hasSize(4)
  }

  fun test_annotations_imports() {
    // Kotlin provider.
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides as Provider2
        import dagger.Module

        @Module
        class MyClassKt {
          @dagger.Provides fun provider():Int? {}
          @Provider2 fun provider2():Int {}
        }
      """
        .trimIndent()
    )

    // Consumer in JAVA.
    assertThat(getProvidersForInjectedField("int")).hasSize(2)
  }

  fun testLazy() {
    myFixture.addClass(
      // language=JAVA
      """
        package test;
        import dagger.Module;
        import dagger.Provides;

        @Module
        class MyModule {
          @Provides String providesString();
        }
      """
        .trimIndent()
    )

    val providers = getProvidersForInjectedField_kotlin("dagger.Lazy<String>")
    assertThat(providers).isNotEmpty()
    assertThat(providers.map { it.name }).containsExactly("providesString")

    val consumers = getDaggerConsumersFor(providers.first()).first()
    assertThat(consumers.type.canonicalText).isEqualTo("dagger.Lazy<java.lang.String>")
  }

  fun testJavaxInjectProvider() {
    myFixture.addClass(
      // language=JAVA
      """
        package test;
        import dagger.Module;
        import dagger.Provides;

        @Module
        class MyModule {
          @Provides String providesString();
        }
      """
        .trimIndent()
    )

    val providers = getProvidersForInjectedField_kotlin("javax.inject.Provider<String>")
    assertThat(providers).isNotEmpty()
    assertThat(providers.map { it.name }).containsExactly("providesString")

    val consumers = getDaggerConsumersFor(providers.first()).first()
    assertThat(consumers.type.canonicalText).isEqualTo("javax.inject.Provider<java.lang.String>")
  }
}

class DaggerCrossModuleTest : UsefulTestCase() {

  private lateinit var myFixture: JavaCodeInsightTestFixture

  private lateinit var moduleA: Module
  private lateinit var moduleDependsOnModuleA: Module

  /** Set up with two modules where moduleDependsOnModuleA depends on moduleA. */
  override fun setUp() {
    super.setUp()

    val projectBuilder = JavaTestFixtureFactory.createFixtureBuilder(name)
    myFixture =
      JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)

    val daggerModuleFixture = newModule(projectBuilder, "DaggerCrossModuleTest_dagger")
    val moduleAFixture = newModule(projectBuilder, "DaggerCrossModuleTest_moduleA")
    val moduleDependsOnModuleAFixture =
      newModule(projectBuilder, "DaggerCrossModuleTest_moduleDependsOnModuleA")

    myFixture.setUp()

    // Make a dagger module actually dagger by adding dagger.Module/Provides annotations.
    myFixture.addFileToProject(
      "DaggerCrossModuleTest_dagger/src/dagger/DaggerInit.kt",
      // language=kotlin
      """
      package dagger

      annotation class Module
      annotation class Provides
      """
        .trimIndent()
    )
    moduleA = moduleAFixture.module
    moduleDependsOnModuleA = moduleDependsOnModuleAFixture.module

    ModuleRootModificationUtil.addDependency(moduleDependsOnModuleA, daggerModuleFixture.module)
    ModuleRootModificationUtil.addDependency(moduleA, daggerModuleFixture.module)
  }

  override fun tearDown() {
    try {
      myFixture.tearDown()
    } catch (t: Throwable) {
      addSuppressedException(t)
    } finally {
      super.tearDown()
    }
  }

  private fun newModule(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    contentRoot: String
  ): ModuleFixture {
    val firstProjectBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder::class.java)
    val tempDirPath = myFixture.tempDirPath

    // Create a new content root for each module, and create a directory for it manually
    val contentRootPath = "$tempDirPath/$contentRoot"
    File(contentRootPath).mkdir()

    // Call the builder
    return firstProjectBuilder.addContentRoot(contentRootPath).addSourceRoot("src").fixture
  }

  fun test() {
    val fileInModuleThatDependsOnModuleA =
      myFixture
        .addFileToProject(
          "DaggerCrossModuleTest_moduleDependsOnModuleA/src/test2/MyModule2.java",
          // language=JAVA
          """
      package test2;
      import dagger.Module;
      import dagger.Provides;

      @Module
      public class MyModule2 {
        @Provides String stringProvider() {}
      }
      """
            .trimIndent()
        )
        .virtualFile

    val fileInModuleA =
      myFixture
        .addFileToProject(
          "DaggerCrossModuleTest_moduleA/src/test/MyModule1.java",
          // language=JAVA
          """
      package test;
      import dagger.Module;
      import dagger.Provides;

      @Module
      public class MyModule1 {
        @Provides Integer intProvider(String consumer) {}
      }
      """
            .trimIndent()
        )
        .virtualFile

    myFixture.configureFromExistingVirtualFile(fileInModuleA)

    var providers = getDaggerProvidersFor(myFixture.findParentElement<PsiParameter>("consum|er"))

    assertThat(providers).hasSize(0)

    ModuleRootModificationUtil.addDependency(moduleDependsOnModuleA, moduleA)

    providers = getDaggerProvidersFor(myFixture.findParentElement<PsiParameter>("consum|er"))
    assertThat(providers).hasSize(1)
    assertThat((providers.single() as PsiNamedElement).name).isEqualTo("stringProvider")

    myFixture.configureFromExistingVirtualFile(fileInModuleThatDependsOnModuleA)
    val consumers = getDaggerConsumersFor(myFixture.findParentElement<PsiMethod>("stringProvid|er"))
    assertThat(consumers).hasSize(1)
    assertThat((consumers.single() as PsiNamedElement).name).isEqualTo("consumer")
  }
}
