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

import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.navigation.GotoRelatedItem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtDeclaration

class DaggerRelatedItemLineMarkerProviderTest : DaggerTestCase() {

  private fun getGotoElements(icon: GutterMark): Collection<GotoRelatedItem> {
    return ((icon as LineMarkerInfo.LineMarkerGutterIconRenderer<*>).lineMarkerInfo as RelatedItemLineMarkerInfo).createGotoRelatedItems()
  }

  fun testGutterIcons() {
    // Provider
    val providerFile = myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides String provider() {}
        }
      """.trimIndent()
    )

    val providerMethod = myFixture.moveCaret("provi|der()").parentOfType<PsiMethod>()

    // Consumer
    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        class MyClass {
          @Inject String ${caret}injectedString;
        }
      """.trimIndent()
    )

    val consumerField = myFixture.elementAtCaret

    // Icons in consumer file.
    var icons = myFixture.findAllGutters()
    assertThat(icons).isNotEmpty()

    var gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    var provider = gotoRelatedItems.first()

    assertThat(provider.group).isEqualTo("Dependency provider(s)")
    assertThat(provider.element).isEqualTo(providerMethod)

    // Kotlin consumer
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
          import javax.inject.Inject

          class MyClass {
            @Inject val <caret>injectedString:String
          }
        """.trimIndent()
    )

    val consumerKtField = myFixture.elementAtCaret

    // Icons in kotlin-consumer file.
    icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    provider = gotoRelatedItems.first()

    assertThat(provider.group).isEqualTo("Dependency provider(s)")
    assertThat(provider.element).isEqualTo(providerMethod)

    // Icons in provider file.
    myFixture.configureFromExistingVirtualFile(providerFile.virtualFile)
    myFixture.moveCaret(" @Provides String pr|ovider()")
    icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files" }!!)
    assertThat(gotoRelatedItems).hasSize(2)
    val consumerElements = gotoRelatedItems.map { it.element }

    assertThat(consumerElements).containsAllOf(consumerField, LightClassUtil.getLightClassBackingField(consumerKtField as KtDeclaration))
  }

  fun testComponentMethodsForProvider() {
    // Provider
    val providerFile = myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        package test;
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides String provider() {}
        }
      """.trimIndent()
    ).virtualFile

    myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {
        String getString();
      }
    """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(providerFile)
    myFixture.moveCaret("@Provides String pr|ovider")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    val method = gotoRelatedItems.first()
    assertThat(method.group).isEqualTo("Dependency components method(s)")
    assertThat(method.element?.text).isEqualTo("String getString();")
  }


  fun testComponentForModules() {
    // Provider
    val moduleFile = myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        package test;
        import dagger.Module;

        @Module
        class MyModule {
        }
      """.trimIndent()
    ).virtualFile

    myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {
      }
    """.trimIndent()
    )

    // Java Module
    myFixture.addClass(
      //language=JAVA
      """
        package test;
        import dagger.Module;

        @Module(includes = { MyModule.class })
        class MyModule2 {}
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(moduleFile)
    myFixture.moveCaret("class MyMod|ule")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files" }!!)
    assertThat(gotoRelatedItems).hasSize(2)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiClass).name}" }
    assertThat(result).containsAllOf("Dependency component(s): MyComponent", "Dependency modules(s): MyModule2")
  }
}