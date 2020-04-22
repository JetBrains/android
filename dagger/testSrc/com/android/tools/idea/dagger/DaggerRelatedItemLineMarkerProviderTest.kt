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

    assertThat(provider.group).isEqualTo("Provider(s)")
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

    assertThat(provider.group).isEqualTo("Provider(s)")
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
    assertThat(method.group).isEqualTo("Exposed by component(s)")
    assertThat(method.element?.text).isEqualTo("String getString();")
  }


  fun testComponentForModules() {
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
    assertThat(result).containsAllOf("Included in component(s): MyComponent", "Included in module(s): MyModule2")
  }

  fun testDependantComponentsForComponent() {
    // Java Component
    val componentFile = myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Component;

      @Component
      public interface MyComponent {}
    """.trimIndent()
    ).containingFile.virtualFile

    // Kotlin Component
    myFixture.addFileToProject(
      "test/MyDependantComponent.kt",
      //language=kotlin
      """
      package test
      import dagger.Component

      @Component(dependencies = [MyComponent::class])
      interface MyDependantComponent
    """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(componentFile)
    myFixture.moveCaret("public interface MyComp|onent {}")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    val method = gotoRelatedItems.first()
    assertThat(method.group).isEqualTo("Parent component(s)")
    assertThat((method.element as PsiClass).name).isEqualTo("MyDependantComponent")
  }

  fun testParentsForSubcomponent() {
    // Java Subomponent
    val subcomponentFile = myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent
      public interface MySubcomponent {
        @Subcomponent.Builder
          interface Builder {}
      }
    """.trimIndent()
    ).containingFile.virtualFile

    myFixture.addClass(
      //language=JAVA
      """
        package test;

        import dagger.Module;

        @Module(subcomponents = { MySubcomponent.class })
        class MyModule { }
      """.trimIndent()
    )

    // Java Component
    myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {}
    """.trimIndent()
    )

    // Java Component
    myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Component;

      @Component
      public interface MyComponentWithBuilder {
        MySubcomponent.Builder componentBuilder();
      }
    """.trimIndent()
    )

    // Kotlin Component
    myFixture.addFileToProject(
      "test/MyComponentKt.kt",
      //language=kotlin
      """
      package test
      import dagger.Component

      @Component
      interface MyComponentKt {
         fun getSubcomponent():MySubcomponent
      }
    """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(subcomponentFile)
    myFixture.moveCaret("MySubcompon|ent")
    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files" }!!)
    assertThat(gotoRelatedItems).hasSize(3)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiClass).name}" }
    assertThat(result).containsAllOf("Parent component(s): MyComponent",
                                     "Parent component(s): MyComponentWithBuilder",
                                     "Parent component(s): MyComponentKt")
  }

  fun testSubcomponentForComponent() {
    // Java Subcomponent
    myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent
      public interface MySubcomponent {
        @Subcomponent.Builder
          interface Builder {}
      }
    """.trimIndent()
    )

    // Kotlin Subcomponent
    myFixture.addFileToProject(
      "test/MySubcomponent2.kt",
      //language=kotlin
      """
      package test
      import dagger.Subcomponent

      @Subcomponent
      interface MySubcomponent2
    """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
        package test;

        import dagger.Module;

        @Module(subcomponents = { MySubcomponent.class })
        class MyModule { }
      """.trimIndent()
    )

    // Java Component
    val file = myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {
        MySubcomponent2 getSubcomponent();
      }
    """.trimIndent()
    ).containingFile.virtualFile

    myFixture.configureFromExistingVirtualFile(file)
    myFixture.moveCaret("MyCompon|ent")
    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files" }!!)
    assertThat(gotoRelatedItems).hasSize(2)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiClass).name}" }
    assertThat(result).containsAllOf("Subcomponent(s): MySubcomponent2",
                                     "Subcomponent(s): MySubcomponent")
  }

  fun testObjectClassInKotlin() {
    val moduleFile = myFixture.addFileToProject("test/MyModule.kt",
      //language=kotlin
                                                """
        package test
        import dagger.Module

        @Module
        object MyModule
      """.trimIndent()
    ).virtualFile

    // Enum shouldn't be treated as dagger entity.
    val notModuleFile = myFixture.addFileToProject("test/NotMyModule.kt",
      //language=kotlin
                                                   """
        package test
        import dagger.Module

        enum class NotMyModule {
          @Module
          ONE
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

    myFixture.configureFromExistingVirtualFile(moduleFile)
    myFixture.moveCaret("MyMod|ule")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiClass).name}" }
    assertThat(result).containsExactly("Included in component(s): MyComponent")

    myFixture.configureFromExistingVirtualFile(notModuleFile)
    myFixture.moveCaret("ON|E")

    assertThat(myFixture.findGuttersAtCaret()).isEmpty()
  }
}