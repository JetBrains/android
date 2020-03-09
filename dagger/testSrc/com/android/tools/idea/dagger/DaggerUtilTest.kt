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

import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

class DaggerUtilTest : DaggerTestCase() {

  private fun getProvidersForInjectedField_kotlin(fieldType: String): Collection<PsiMethod> {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import javax.inject.Inject

        class MyClass {
          @Inject val injectedField:${fieldType}
        }
      """.trimIndent()
    )

    return getDaggerProvidersFor(myFixture.moveCaret("injectedF|ield").parentOfType<KtProperty>()!!)
  }

  private fun getProvidersForInjectedField(fieldType: String): Collection<PsiMethod> {
    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        class MyClass {
          @Inject ${fieldType} injectedField;
        }
      """.trimIndent()
    )
    return getDaggerProvidersFor(myFixture.moveCaret("injected|Field").parentOfType<PsiField>()!!)
  }

  fun testIsConsumerForInjectField() {
    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        class MyClass {
          @Inject String injectedString;
          String notInjectedString;
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("injected|String").parentOfType<PsiField>().isDaggerConsumer).isTrue()
    assertThat(myFixture.moveCaret("notInjected|String").parentOfType<PsiField>().isDaggerConsumer).isFalse()
  }

  fun testIsConsumerForInjectField_kotlin() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import javax.inject.Inject

        class MyClass {
          @Inject val injectedString:String
          val notInjectedString:String
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("injected|String").parentOfType<KtProperty>().isDaggerConsumer).isTrue()
    assertThat(myFixture.moveCaret("notInjected|String").parentOfType<KtProperty>().isDaggerConsumer).isFalse()
  }

  fun testIsConsumer_injectedConstructorParam() {
    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        public class MyClass {
          @Inject public MyClass(String consumer) {}
          public MyClass(int notConsumer) {}
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("consum|er").parentOfType<PsiParameter>().isDaggerConsumer).isTrue()
    assertThat(myFixture.moveCaret("notConsum|er").parentOfType<PsiParameter>().isDaggerConsumer).isFalse()
  }

  fun testIsConsumer_injectedConstructorParam_kotlin() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import javax.inject.Inject

        class MyClass @Inject constructor(consumer:String) {
          constructor(notConsumer: Int)
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("consum|er").parentOfType<KtParameter>().isDaggerConsumer).isTrue()
    assertThat(myFixture.moveCaret("notConsum|er").parentOfType<KtParameter>().isDaggerConsumer).isFalse()
  }

  fun testIsProvider_providesMethod() {
    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyClass {
          @Provides String provider() {}
          String notProvider() {}
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("provide|r").parentOfType<PsiMethod>().isDaggerProvider).isTrue()
    assertThat(myFixture.moveCaret("notProv|ider").parentOfType<PsiMethod>().isDaggerProvider).isFalse()
  }

  fun testIsProvider_kotlin_providesMethod() {
    myFixture.configureByText(
      //language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides
        import dagger.Module

        @Module
        class MyClass {
          @Provides fun provider() {}
          fun notProvider() {}
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("provide|r").parentOfType<KtFunction>().isDaggerProvider).isTrue()
    assertThat(myFixture.moveCaret("notProv|ider").parentOfType<KtFunction>().isDaggerProvider).isFalse()
  }

  fun testIsProvider_injectedConstructor() {
    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        public class MyClass {
          @Inject public MyClass() {}
          public MyClass(String s) {}
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("public MyCl|ass()").parentOfType<PsiMethod>().isDaggerProvider).isTrue()
    assertThat(myFixture.moveCaret("public MyCl|ass(String s)").parentOfType<PsiMethod>().isDaggerProvider).isFalse()
  }

  fun testIsProvider_kotlin_injectedConstructor() {
    myFixture.configureByText(
      //language=kotlin
      KotlinFileType.INSTANCE,
      """
        import javax.inject.Inject

        class MyClass @Inject constructor()
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("construc|tor").parentOfType<KtFunction>().isDaggerProvider).isTrue()


    myFixture.configureByText(
      //language=kotlin
      KotlinFileType.INSTANCE,
      """
        import javax.inject.Inject

        class MyClass(s: String) {
          @Inject constructor()
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("construc|tor").parentOfType<KtFunction>().isDaggerProvider).isTrue()
  }

  fun testIsProvider_bindsMethod() {
    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Binds;

        abstract class MyClass {
          @Binds abstract String bindsMethod() {}
          abstract String notBindsMethod() {}
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("bindsMet|hod").parentOfType<PsiMethod>().isDaggerProvider).isTrue()
    assertThat(myFixture.moveCaret("notBindsMet|hod").parentOfType<PsiMethod>().isDaggerProvider).isFalse()
  }

  fun testIsProvider_kotlin_bindsMethod() {
    myFixture.configureByText(
      //language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Binds

        abstract class MyClass {
          @Binds abstract fun bindsMethod() {}
          fun notBindsMethod() {}
        }
      """.trimIndent()
    )

    assertThat(myFixture.moveCaret("bindsMet|hod").parentOfType<KtFunction>().isDaggerProvider).isTrue()
    assertThat(myFixture.moveCaret("notBindsMet|hod").parentOfType<PsiMethod>().isDaggerProvider).isFalse()
  }

  fun testGetDaggerProviders_providesMethod() {
    // JAVA provider.
    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyClass {
          @Provides String provider() {}
        }
      """.trimIndent()
    )

    val provider = myFixture.moveCaret("provide|r").parentOfType<PsiMethod>()

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
      //language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Provides
        import dagger.Module

        @Module
        class MyClass {
          @Provides fun provider():String {}
        }
      """.trimIndent()
    )

    val provider = myFixture.moveCaret("provid|er").parentOfType<KtFunction>()?.toLightElements()?.first()

    assume().that(provider).isNotNull()
    // We will compare with string representation, because ide returns different instances of light class.
    assume().that(provider.toString()).isEqualTo("KtUltraLightMethodForSourceDeclaration:provider")

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
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Binds;
        import dagger.Module;

        @Module
        abstract class MyClass {
          @Binds abstract String bindsMethod() {}
        }
      """.trimIndent()
    )

    val provider = myFixture.moveCaret("bindsMet|hod").parentOfType<PsiMethod>()

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
      //language=kotlin
      KotlinFileType.INSTANCE,
      """
        import dagger.Binds
        import dagger.Module

        @Module
        abstract class MyClass {
          @Binds abstract fun bindsMethod():String {}
          fun notBindsMethod():String {}
        }
      """.trimIndent()
    )

    val provider = myFixture.moveCaret("bindsMeth|od").parentOfType<KtFunction>()?.toLightElements()?.first()

    assume().that(provider).isNotNull()
    // We will compare with string representation, because ide returns different instances of light class.
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
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        public class MyClassWithInjectedConstructor {
          @Inject public MyClassWithInjectedConstructor() {}
        }
      """.trimIndent()
    )

    val provider = myFixture.moveCaret("MyClassWithInjectedConstru|ctor()").parentOfType<PsiMethod>()

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
      //language=kotlin
      KotlinFileType.INSTANCE,
      """
        import javax.inject.Inject

        class MyClassWithInjectedConstructor @Inject constructor()
      """.trimIndent()
    )

    val provider = myFixture.moveCaret("construct|or()").parentOfType<KtFunction>()?.toLightElements()?.first()

    assume().that(provider).isNotNull()
    // We will compare with string representation, because ide returns different instances of light class.
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
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyClass {
          @Provides String provider() {}
        }
      """.trimIndent()
    )

    val provider = myFixture.moveCaret("provide|r").parentOfType<PsiMethod>()

    // Consumer in Kotlin.
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import javax.inject.Inject

        class MyClass @Inject constructor(consumer:String)
      """.trimIndent()
    )

    val providers = getDaggerProvidersFor(myFixture.moveCaret("consum|er").parentOfType<KtParameter>()!!)
    assertThat(providers).hasSize(1)
    assertThat(providers.first()).isEqualTo(provider)
  }
}