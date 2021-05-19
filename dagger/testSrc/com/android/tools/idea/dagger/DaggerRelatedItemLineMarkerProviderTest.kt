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
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.navigation.GotoRelatedItem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.registerServiceInstance
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import java.awt.event.MouseEvent
import javax.swing.JLabel

class DaggerRelatedItemLineMarkerProviderTest : DaggerTestCase() {
  private lateinit var trackerService: TestDaggerAnalyticsTracker

  override fun setUp() {
    super.setUp()
    trackerService = TestDaggerAnalyticsTracker()
    project.registerServiceInstance(DaggerAnalyticsTracker::class.java, trackerService)
  }

  private fun getGotoElements(icon: GutterMark): Collection<GotoRelatedItem> {
    return ((icon as LineMarkerInfo.LineMarkerGutterIconRenderer<*>).lineMarkerInfo as RelatedItemLineMarkerInfo).createGotoRelatedItems()
  }

  private fun clickOnIcon(icon: LineMarkerInfo.LineMarkerGutterIconRenderer<*>) {
    try {
      (icon.lineMarkerInfo.navigationHandler!! as GutterIconNavigationHandler<PsiElement>)
        .navigate(
          MouseEvent(JLabel(), 0, 0, 0, 0, 0, 0, false),
          icon.lineMarkerInfo.element
        )
    }
    catch (e: java.awt.HeadlessException) {
      // This error appears when AS tries to open a popup after the click in Headless environment.
    }
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

    var gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "injectedString consumes provider()" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    var provider = gotoRelatedItems.first()

    assertThat(provider.group).isEqualTo("Providers")
    assertThat(provider.element).isEqualTo(providerMethod)

    // Kotlin consumer
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
          import javax.inject.Inject

          class MyClass {
            @Inject val <caret>injectedStringKt:String
          }
        """.trimIndent()
    )

    val consumerKtField = myFixture.elementAtCaret

    // Icons in kotlin-consumer file.
    icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "injectedStringKt consumes provider()" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    provider = gotoRelatedItems.first()

    assertThat(provider.group).isEqualTo("Providers")
    assertThat(provider.element).isEqualTo(providerMethod)

    // Kotlin consumer as function parameter
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
          import dagger.Module
          import dagger.Provides

          @Module
          class MyModule {
            @Provides fun providerToDisplayAsConsumer(consumer:String)
          }
        """.trimIndent()
    )

    // Kotlin consumer as parameter
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
          import javax.inject.Inject

          class MyClass2 @Inject constructor(injectedString:String)
        """.trimIndent()
    )

    val parameter = myFixture.moveCaret("inj|ectedString").parentOfType<KtParameter>()!!

    // Icons in provider file.
    myFixture.configureFromExistingVirtualFile(providerFile.virtualFile)
    myFixture.moveCaret(" @Provides String pr|ovider()")
    icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon = icons.find { it.tooltipText == "Dependency Related Files for provider()" }!!
      as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(4)
    val consumerElements = gotoRelatedItems.map { it.element }

    assertThat(consumerElements).containsAllOf(
      consumerField,
      LightClassUtil.getLightClassBackingField(consumerKtField as KtDeclaration),
      parameter.toLightElements()[0]
    )

    val displayedNames = gotoRelatedItems.map { it.customName }
    assertThat(displayedNames).containsAllOf(
      "MyClass2", "MyClass", "MyClass", "providerToDisplayAsConsumer"
    )

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(1)
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackClickOnGutter PROVIDER")
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
          @Provides String provider() {
            return "fff";
          }
        }
      """.trimIndent()
    ).virtualFile

    val componentFile = myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {
        String getString();
      }
    """.trimIndent()
    ).containingFile.virtualFile

    myFixture.configureFromExistingVirtualFile(componentFile)
    myFixture.moveCaret("getStr|ing")

    val iconForComponentMethod = myFixture.findGuttersAtCaret().find { it.tooltipText == "getString() exposes provider()" }!!
      as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItemForComponentMethod = getGotoElements(iconForComponentMethod).first()

    assertThat(gotoRelatedItemForComponentMethod.group).isEqualTo("Providers")
    assertThat((gotoRelatedItemForComponentMethod.element as PsiNamedElement).name).isEqualTo("provider")

    myFixture.configureFromExistingVirtualFile(providerFile)
    myFixture.moveCaret("@Provides String pr|ovider")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon = icons.find { it.tooltipText == "provider() exposed in MyComponent" }!!
      as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(1)
    val method = gotoRelatedItems.first()
    assertThat(method.group).isEqualTo("Exposed by components")
    assertThat(method.element?.text).isEqualTo("String getString();")
    assertThat(method.customName).isEqualTo("MyComponent")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(2)
    assertThat(trackerService.calledMethods.first()).isEqualTo("trackClickOnGutter PROVIDER")
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackNavigation CONTEXT_GUTTER PROVIDER COMPONENT_METHOD")
  }

  fun testComponentMethodsForProvider_kotlin() {
    // Provider
    val providerFile = myFixture.addFileToProject("src/test/MyModule.java",
      //language=JAVA
                                                  """
        package test;
        import dagger.Provides;
        import dagger.Module;

        @Module
        public class MyModule {
          @Provides String provider() {
            return "fff";
          }
        }
      """.trimIndent()
    ).virtualFile

    myFixture.configureByText(
      //language=kotlin
      KotlinFileType.INSTANCE,
      """
      import test.MyModule
      import dagger.Component

      @Component(modules = { MyModule::class })
      interface MyComponent {
        val str:String
      }
    """.trimIndent()
    )

    myFixture.moveCaret("st|r").parent as KtProperty

    val iconForComponentMethod = myFixture.findGuttersAtCaret().find { it.tooltipText == "str exposes provider()" }!!
      as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItemForComponentMethod = getGotoElements(iconForComponentMethod).first()

    assertThat(gotoRelatedItemForComponentMethod.group).isEqualTo("Providers")
    assertThat((gotoRelatedItemForComponentMethod.element as PsiNamedElement).name).isEqualTo("provider")

    myFixture.configureFromExistingVirtualFile(providerFile)
    myFixture.moveCaret("@Provides String pr|ovider")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon = icons.find { it.tooltipText == "provider() exposed in MyComponent" }!!
      as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(1)
    val method = gotoRelatedItems.first()
    assertThat(method.group).isEqualTo("Exposed by components")
    assertThat(method.element?.text).isEqualTo("val str:String")
    assertThat(method.customName).isEqualTo("MyComponent")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(2)
    assertThat(trackerService.calledMethods.first()).isEqualTo("trackClickOnGutter PROVIDER")
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackNavigation CONTEXT_GUTTER PROVIDER COMPONENT_METHOD")
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

    val icon = icons.find { it.tooltipText == "Dependency Related Files for MyModule" }!! as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(2)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiClass).name}" }
    assertThat(result).containsAllOf("Included in components: MyComponent", "Included in modules: MyModule2")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(1)
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackClickOnGutter MODULE")

    gotoRelatedItems.find { it.group == "Included in components" }!!.navigate()
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackNavigation CONTEXT_GUTTER MODULE COMPONENT")
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
    val icon = icons.find { it.tooltipText == "MyDependantComponent is parent of MyComponent" }!!
      as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(1)
    val method = gotoRelatedItems.first()
    assertThat(method.group).isEqualTo("Parent components")
    assertThat((method.element as PsiClass).name).isEqualTo("MyDependantComponent")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(2)
    assertThat(trackerService.calledMethods.first()).isEqualTo("trackClickOnGutter COMPONENT")
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackNavigation CONTEXT_GUTTER COMPONENT COMPONENT")
  }

  fun testParentsForSubcomponent() {
    // Java Subomponent
    val subcomponentFile = myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent
      public interface MySubcomponent {}
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

    // Kotlin Component
    myFixture.addFileToProject(
      "test/MyComponentKt.kt",
      //language=kotlin
      """
      package test
      import dagger.Component

      @Component(modules = [ MyModule::class])
      interface MyComponentKt
    """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(subcomponentFile)
    myFixture.moveCaret("MySubcompon|ent")
    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files for MySubcomponent" }!!)
    assertThat(gotoRelatedItems).hasSize(2)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiClass).name}" }
    assertThat(result).containsAllOf("Parent components: MyComponent",
                                     "Parent components: MyComponentKt")
  }

  fun testSubcomponentsAndModulesForComponent() {
    // Java Subcomponent
    myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent
      public interface MySubcomponent {}
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

        @Module(subcomponents = { MySubcomponent.class, MySubcomponent2.class })
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
      public interface MyComponent {}
    """.trimIndent()
    ).containingFile.virtualFile

    myFixture.configureFromExistingVirtualFile(file)
    myFixture.moveCaret("MyCompon|ent")
    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "Dependency Related Files for MyComponent" }!!)
    assertThat(gotoRelatedItems).hasSize(3)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiClass).name}" }
    assertThat(result).containsAllOf("Subcomponents: MySubcomponent2",
                                     "Subcomponents: MySubcomponent",
                                     "Modules included: MyModule")
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

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == "MyModule is included in component MyComponent" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiClass).name}" }
    assertThat(result).containsExactly("Included in components: MyComponent")

    myFixture.configureFromExistingVirtualFile(notModuleFile)
    myFixture.moveCaret("ON|E")

    assertThat(myFixture.findGuttersAtCaret()).isEmpty()
  }

  fun testBindsInstance() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      import javax.inject.Qualifier;

      @Qualifier
      public @interface MyQualifier {}
    """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
      package test;
      import javax.inject.Inject;

      class MyClass {
        @Inject String injectedString;
        @Inject @MyQualifier String injectedStringWithQualifier;
      }

    """.trimIndent())

    myFixture.loadNewFile("test/MyModule.kt",
      //language=kotlin
                          """
        package test
        import dagger.Module
        import dagger.BindsInstance
        import test.MyQualifier

        @Module
        class MyModule {
           fun bindString(@BindsInstance @MyQualifier str:String):String
        }
      """.trimIndent()
    )

    myFixture.moveCaret("st|r")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon = icons.find { it.tooltipText == "str provides for MyClass" }!! as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(1)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiNamedElement).name}" }
    assertThat(result).containsExactly("Consumers: injectedStringWithQualifier")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(2)
    assertThat(trackerService.calledMethods.first()).isEqualTo("trackClickOnGutter PROVIDER")
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackNavigation CONTEXT_GUTTER PROVIDER CONSUMER")
  }

  fun testModulesForSubcomponent() {
    myFixture.addClass(
      //language=JAVA
      """
        package test;

        import dagger.Module;

        @Module
        class MyModule { }
      """.trimIndent()
    )
    // Java Subomponent
    val file = myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent(modules = { MyModule.class })
      public interface MySubcomponent {}
    """.trimIndent()
    ).containingFile.virtualFile

    myFixture.configureFromExistingVirtualFile(file)
    myFixture.moveCaret("MySubcompon|ent")
    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(
      icons.find { it.tooltipText == "MySubcomponent includes module MyModule" }!!
    )
    assertThat(gotoRelatedItems).hasSize(1)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiClass).name}" }
    assertThat(result).containsExactly("Modules included: MyModule")
  }

  fun testSubcomponentsForSubcomponent() {
    // Java Subcomponent
    myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent
      public interface MySubcomponent {}
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

    // Java parent Subcomponent
    val file = myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent(modules = { MyModule.class })
      public interface MyParentSubcomponent {}
    """.trimIndent()
    ).containingFile.virtualFile


    myFixture.configureFromExistingVirtualFile(file)
    myFixture.moveCaret("MyParent|Subcomponent")
    val icons = myFixture.findGuttersAtCaret()
    val icon = icons.find { it.tooltipText == "Dependency Related Files for MyParentSubcomponent" }!!
      as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiNamedElement).name}" }
    assertThat(result).contains("Subcomponents: MySubcomponent")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackClickOnGutter SUBCOMPONENT")
    gotoRelatedItems.find { it.group == "Subcomponents" }!!.navigate()
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackNavigation CONTEXT_GUTTER SUBCOMPONENT SUBCOMPONENT")
  }

  fun testEntryPointMethodsForProvider() {
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

    val entryPointFile = myFixture.addClass(
      //language=JAVA
      """
      package test;
      import dagger.hilt.EntryPoint;

      @EntryPoint
      public interface MyEntryPoint {
        String getStringInEntryPoint();
      }
    """.trimIndent()
    ).containingFile.virtualFile

    myFixture.configureFromExistingVirtualFile(entryPointFile)
    myFixture.moveCaret("getStringInEntr|yPoint")

    val iconForComponentMethod = myFixture.findGuttersAtCaret().find { it.tooltipText == "getStringInEntryPoint() exposes provider()" }!!
      as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItemForComponentMethod = getGotoElements(iconForComponentMethod).first()

    assertThat(gotoRelatedItemForComponentMethod.group).isEqualTo("Providers")
    assertThat((gotoRelatedItemForComponentMethod.element as PsiNamedElement).name).isEqualTo("provider")

    myFixture.configureFromExistingVirtualFile(providerFile)
    myFixture.moveCaret("@Provides String pr|ovider")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon = icons.find { it.tooltipText == "Dependency Related Files for provider()" }!!
      as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(2)
    val method = gotoRelatedItems.find { it.group == "Exposed by entry points" }!!
    assertThat(method.element?.text).isEqualTo("String getStringInEntryPoint();")
    assertThat(method.customName).isEqualTo("MyEntryPoint")

    method.navigate()
    assertThat(trackerService.calledMethods.single()).isEqualTo("trackNavigation CONTEXT_GUTTER PROVIDER ENTRY_POINT_METHOD")
  }
}