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
@file:Suppress("Since15")

package com.android.tools.idea.dagger

import com.android.testutils.MockitoKt
import com.android.tools.idea.dagger.DaggerRelatedItemLineMarkerProvider.Companion.canReceiveLineMarker
import com.android.tools.idea.dagger.DaggerRelatedItemLineMarkerProvider.Companion.getGotoItems
import com.android.tools.idea.dagger.concepts.AssistedFactoryMethodDaggerElement
import com.android.tools.idea.dagger.concepts.DaggerElement
import com.android.tools.idea.dagger.concepts.DaggerRelatedElement
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.registerServiceInstance
import icons.StudioIcons.Misc.DEPENDENCY_CONSUMER
import icons.StudioIcons.Misc.DEPENDENCY_PROVIDER
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JLabel

class DaggerRelatedItemLineMarkerProviderDaggerTest : DaggerTestCase() {
  private lateinit var trackerService: TestDaggerAnalyticsTracker

  override fun setUp() {
    super.setUp()
    trackerService = TestDaggerAnalyticsTracker()
    project.registerServiceInstance(DaggerAnalyticsTracker::class.java, trackerService)
  }

  override fun tearDown() {
    // Close the popups created by clickOnIcon(), otherwise they leak the test project.
    IdeEventQueue.getInstance().popupManager.closeAllPopups()
    super.tearDown()
  }

  private fun getGotoElements(icon: GutterMark): Collection<GotoRelatedItem> {
    return ((icon as LineMarkerInfo.LineMarkerGutterIconRenderer<*>).lineMarkerInfo
        as RelatedItemLineMarkerInfo)
      .createGotoRelatedItems()
  }

  private fun clickOnIcon(icon: LineMarkerInfo.LineMarkerGutterIconRenderer<*>) {
    try {
      @Suppress("UNCHECKED_CAST")
      (icon.lineMarkerInfo.navigationHandler!! as GutterIconNavigationHandler<PsiElement>).navigate(
        MouseEvent(JLabel(), 0, 0, 0, 0, 0, 0, false),
        icon.lineMarkerInfo.element
      )
    } catch (e: java.awt.HeadlessException) {
      // This error appears when AS tries to open a popup after the click in Headless environment.
    }
  }

  fun testGetName() {
    val provider = DaggerRelatedItemLineMarkerProvider()
    assertThat(provider.name).isEqualTo("Dagger related items")
  }

  fun testGetId() {
    // The value of the id doesn't really matter, but it needs to be present in order for the icons
    // available for disabling in settings.
    val provider = DaggerRelatedItemLineMarkerProvider()
    assertThat(provider.id).isNotEmpty()
  }

  fun testIsEnabledByDefault() {
    val provider = DaggerRelatedItemLineMarkerProvider()

    // This system property used to indicate whether the provider was on by default, but should no
    // longer have any effect.
    System.setProperty("disable.dagger.relateditems.gutter.icons", "false")
    assertThat(provider.isEnabledByDefault).isTrue()

    System.setProperty("disable.dagger.relateditems.gutter.icons", "true")
    assertThat(provider.isEnabledByDefault).isTrue()

    // Make sure to clear the property so that it doesn't pollute other tests.
    System.clearProperty("disable.dagger.relateditems.gutter.icons")
  }

  fun testGutterIcons() {
    // Provider
    val providerFile =
      myFixture.configureByText(
        // language=JAVA
        JavaFileType.INSTANCE,
        """
        package com.example.java;
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides String provider() {}
        }
        """
          .trimIndent()
      )

    val providerMethod: PsiMethod = myFixture.findParentElement("provi|der()")

    // Consumer
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
      package com.example.java;
      import javax.inject.Inject;

      class MyClass {
        @Inject String ${caret}injectedString;
      }
      """
        .trimIndent()
    )

    val consumerField = myFixture.elementAtCaret

    // Icons in consumer file.
    var icons = myFixture.findAllGutters()
    assertThat(icons).isNotEmpty()

    var gotoRelatedItems =
      getGotoElements(icons.find { it.tooltipText == "injectedString consumes provider()" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    var provider = gotoRelatedItems.first()

    assertThat(provider.group).isEqualTo("Providers")
    assertThat(provider.element).isEqualTo(providerMethod)

    // Kotlin consumer
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example.kotlin
      import javax.inject.Inject

      class MyClass {
        @Inject val <caret>injectedStringKt:String
      }
      """
        .trimIndent()
    )

    val consumerKtField = myFixture.elementAtCaret

    // Icons in kotlin-consumer file.
    icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    gotoRelatedItems =
      getGotoElements(icons.find { it.tooltipText == "injectedStringKt consumes provider()" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    provider = gotoRelatedItems.first()

    assertThat(provider.group).isEqualTo("Providers")
    assertThat(provider.element).isEqualTo(providerMethod)

    // Kotlin consumer as function parameter
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example.kotlin
      import dagger.Module
      import dagger.Provides

      @Module
      class MyModule {
        @Provides fun providerToDisplayAsConsumer(consumer:String)
      }
      """
        .trimIndent()
    )

    val consumerParameter: KtParameter = myFixture.findParentElement("cons|umer:String")

    // Kotlin consumer as parameter
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example.kotlin
      import javax.inject.Inject

      class MyClass2 @Inject constructor(injectedString:String)
      """
        .trimIndent()
    )

    val parameter: KtParameter = myFixture.findParentElement("inj|ectedString")

    // Icons in provider file.
    myFixture.configureFromExistingVirtualFile(providerFile.virtualFile)
    myFixture.moveCaret(" @Provides String pr|ovider()")
    icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon =
      icons.find { it.tooltipText == "Dependency Related Files for provider()" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(4)
    val consumerElements = gotoRelatedItems.map { it.element }

    assertThat(consumerElements)
      .containsExactly(
        consumerField,
        consumerKtField,
        parameter,
        consumerParameter,
      )

    val displayedNames = gotoRelatedItems.map { it.customName }
    assertThat(displayedNames)
      .containsAllOf("MyClass2", "MyClass", "MyClass", "providerToDisplayAsConsumer")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(4)
    assertThat(trackerService.calledMethods[0])
      .startsWith("trackGutterWasDisplayed owner: CONSUMER time: ")
    assertThat(trackerService.calledMethods[1])
      .startsWith("trackGutterWasDisplayed owner: CONSUMER time: ")
    assertThat(trackerService.calledMethods[2])
      .startsWith("trackGutterWasDisplayed owner: PROVIDER time: ")
    assertThat(trackerService.calledMethods[3]).isEqualTo("trackClickOnGutter PROVIDER")
  }

  fun testComponentMethodsForProvider() {
    // Provider
    val providerFile =
      myFixture
        .addClass(
          // language=JAVA
          """
        package test;
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides String provider() {
            return "logcat.filter.save.button.tooltip";
          }
        }
      """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    val componentFile =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {
        String getString();
      }
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    myFixture.configureFromExistingVirtualFile(componentFile)
    myFixture.moveCaret("getStr|ing")

    val iconForComponentMethod =
      myFixture.findGuttersAtCaret().find { it.tooltipText == "getString() exposes provider()" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItemForComponentMethod = getGotoElements(iconForComponentMethod).first()

    assertThat(gotoRelatedItemForComponentMethod.group).isEqualTo("Providers")
    assertThat((gotoRelatedItemForComponentMethod.element as PsiNamedElement).name)
      .isEqualTo("provider")

    myFixture.configureFromExistingVirtualFile(providerFile)
    myFixture.moveCaret("@Provides String pr|ovider")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon =
      icons.find { it.tooltipText == "provider() exposed in MyComponent" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(1)
    val method = gotoRelatedItems.first()
    assertThat(method.group).isEqualTo("Exposed by components")
    assertThat(method.element?.text).isEqualTo("String getString();")
    assertThat(method.customName).isEqualTo("MyComponent")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(6)
    assertThat(trackerService.calledMethods[0])
      .startsWith("trackGutterWasDisplayed owner: COMPONENT time: ")
    assertThat(trackerService.calledMethods[1])
      .startsWith("trackGutterWasDisplayed owner: COMPONENT_METHOD time: ")
    assertThat(trackerService.calledMethods[2])
      .startsWith("trackGutterWasDisplayed owner: MODULE time: ")
    assertThat(trackerService.calledMethods[3])
      .startsWith("trackGutterWasDisplayed owner: PROVIDER time: ")
    assertThat(trackerService.calledMethods[4]).isEqualTo("trackClickOnGutter PROVIDER")
    assertThat(trackerService.calledMethods[5])
      .isEqualTo("trackNavigation CONTEXT_GUTTER PROVIDER COMPONENT_METHOD")
  }

  fun testComponentMethodsForProvider_kotlin() {
    // Provider
    val providerFile =
      myFixture
        .addFileToProject(
          "src/test/MyModule.java",
          // language=JAVA
          """
        package test;
        import dagger.Provides;
        import dagger.Module;

        @Module
        public class MyModule {
          @Provides String provider() {
            return "logcat.filter.save.button.tooltip";
          }
        }
      """
            .trimIndent()
        )
        .virtualFile

    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
      import test.MyModule
      import dagger.Component

      @Component(modules = { MyModule::class })
      interface MyComponent {
        val str:String
      }
    """
        .trimIndent()
    )

    myFixture.moveCaret("st|r").parent as KtProperty

    val iconForComponentMethod =
      myFixture.findGuttersAtCaret().find { it.tooltipText == "str exposes provider()" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItemForComponentMethod = getGotoElements(iconForComponentMethod).first()

    assertThat(gotoRelatedItemForComponentMethod.group).isEqualTo("Providers")
    assertThat((gotoRelatedItemForComponentMethod.element as PsiNamedElement).name)
      .isEqualTo("provider")

    myFixture.configureFromExistingVirtualFile(providerFile)
    myFixture.moveCaret("@Provides String pr|ovider")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon =
      icons.find { it.tooltipText == "provider() exposed in MyComponent" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(1)
    val method = gotoRelatedItems.first()
    assertThat(method.group).isEqualTo("Exposed by components")
    assertThat(method.element?.text).isEqualTo("val str:String")
    assertThat(method.customName).isEqualTo("MyComponent")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(4)
    assertThat(trackerService.calledMethods[0])
      .startsWith("trackGutterWasDisplayed owner: COMPONENT_METHOD time: ")
    assertThat(
        trackerService.calledMethods[0]
          .removePrefix("trackGutterWasDisplayed owner: COMPONENT_METHOD time: ")
          .toInt()
      )
      .isNotNull()
    assertThat(trackerService.calledMethods[1])
      .startsWith("trackGutterWasDisplayed owner: PROVIDER time: ")
    assertThat(
        trackerService.calledMethods[1]
          .removePrefix("trackGutterWasDisplayed owner: PROVIDER time: ")
          .toInt()
      )
      .isNotNull()
    assertThat(trackerService.calledMethods[2]).isEqualTo("trackClickOnGutter PROVIDER")
    assertThat(trackerService.calledMethods[3])
      .isEqualTo("trackNavigation CONTEXT_GUTTER PROVIDER COMPONENT_METHOD")
  }

  fun testComponentForModules() {
    val moduleFile =
      myFixture
        .configureByText(
          // language=JAVA
          JavaFileType.INSTANCE,
          """
        package test;
        import dagger.Module;

        @Module
        class MyModule {
        }
      """
            .trimIndent()
        )
        .virtualFile

    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {
      }
    """
        .trimIndent()
    )

    // Java Module
    myFixture.addClass(
      // language=JAVA
      """
        package test;
        import dagger.Module;

        @Module(includes = { MyModule.class })
        class MyModule2 {}
      """
        .trimIndent()
    )

    // Subcomponent
    myFixture.addClass(
      // language=JAVA
      """
        package test;
        import dagger.Subcomponent;

        @Subcomponent(modules = { MyModule.class })
        class MySubcomponent {}
      """
        .trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(moduleFile)
    myFixture.moveCaret("class MyMod|ule")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon =
      icons.find { it.tooltipText == "Dependency Related Files for MyModule" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    val result = gotoRelatedItems.map { "${it.group}: ${it.element?.className()}" }
    assertThat(result)
      .containsExactly(
        "Included in components: MyComponent",
        "Included in modules: MyModule2",
        "Included in subcomponents: MySubcomponent"
      )

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(2)
    assertThat(trackerService.calledMethods[0])
      .startsWith("trackGutterWasDisplayed owner: MODULE time: ")
    assertThat(trackerService.calledMethods[1]).isEqualTo("trackClickOnGutter MODULE")

    gotoRelatedItems.find { it.group == "Included in components" }!!.navigate()
    assertThat(trackerService.calledMethods.last())
      .isEqualTo("trackNavigation CONTEXT_GUTTER MODULE COMPONENT")
  }

  fun testDependantComponentsForComponent() {
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
    myFixture.addFileToProject(
      "test/MyDependantComponent.kt",
      // language=kotlin
      """
      package test
      import dagger.Component

      @Component(dependencies = [MyComponent::class])
      interface MyDependantComponent
    """
        .trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(componentFile)
    myFixture.moveCaret("public interface MyComp|onent {}")

    val icons = myFixture.findGuttersAtCaret()
    val icon =
      icons.find { it.tooltipText == "MyDependantComponent is parent of MyComponent" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(1)
    val method = gotoRelatedItems.first()
    assertThat(method.group).isEqualTo("Parent components")
    assertThat(method.element?.className()).isEqualTo("MyDependantComponent")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(3)
    assertThat(trackerService.calledMethods[0])
      .startsWith("trackGutterWasDisplayed owner: COMPONENT time: ")
    assertThat(
        trackerService.calledMethods[0]
          .removePrefix("trackGutterWasDisplayed owner: COMPONENT time: ")
          .toInt()
      )
      .isNotNull()
    assertThat(trackerService.calledMethods[1]).isEqualTo("trackClickOnGutter COMPONENT")
    assertThat(trackerService.calledMethods[2])
      .isEqualTo("trackNavigation CONTEXT_GUTTER COMPONENT COMPONENT")
  }

  fun testParentsForSubcomponent() {
    // Java Subomponent
    val subcomponentFile =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;
      import dagger.Subcomponent;

      @Subcomponent
      public interface MySubcomponent {}
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    myFixture.addClass(
      // language=JAVA
      """
        package test;

        import dagger.Module;

        @Module(subcomponents = { MySubcomponent.class })
        class MyModule { }
      """
        .trimIndent()
    )

    // Java Component
    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
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

      @Component(modules = [ MyModule::class])
      interface MyComponentKt
    """
        .trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(subcomponentFile)
    myFixture.moveCaret("MySubcompon|ent")
    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems =
      getGotoElements(
        icons.find { it.tooltipText == "Dependency Related Files for MySubcomponent" }!!
      )
    assertThat(gotoRelatedItems).hasSize(2)
    val result = gotoRelatedItems.map { "${it.group}: ${it.element?.className()}" }
    assertThat(result)
      .containsAllOf("Parent components: MyComponent", "Parent components: MyComponentKt")
  }

  fun testSubcomponentsAndModulesForComponent() {
    // Java Subcomponent
    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent
      public interface MySubcomponent {}
    """
        .trimIndent()
    )

    // Kotlin Subcomponent
    myFixture.addFileToProject(
      "test/MySubcomponent2.kt",
      // language=kotlin
      """
      package test
      import dagger.Subcomponent

      @Subcomponent
      interface MySubcomponent2
    """
        .trimIndent()
    )

    myFixture.addClass(
      // language=JAVA
      """
        package test;

        import dagger.Module;

        @Module(subcomponents = { MySubcomponent.class, MySubcomponent2.class })
        class MyModule { }
      """
        .trimIndent()
    )

    // Java Component
    val file =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {}
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    myFixture.configureFromExistingVirtualFile(file)
    myFixture.moveCaret("MyCompon|ent")
    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems =
      getGotoElements(icons.find { it.tooltipText == "Dependency Related Files for MyComponent" }!!)
    assertThat(gotoRelatedItems).hasSize(3)
    val result = gotoRelatedItems.map { "${it.group}: ${it.element?.className()}" }
    assertThat(result)
      .containsAllOf(
        "Subcomponents: MySubcomponent2",
        "Subcomponents: MySubcomponent",
        "Modules included: MyModule"
      )
  }

  fun testAssistedInjectFactoriesAndConstructors() {
    myFixture.addFileToProject(
      "test/Repository.kt",
      // language=kotlin
      """
      package test
      import javax.inject.Inject

      class Repository @Inject constructor()
    """
        .trimIndent()
    )
    val assistedFactory =
      myFixture
        .addFileToProject(
          "test/AssistedFactory.kt",
          // language=kotlin
          """
      package test

      import dagger.assisted.AssistedFactory

      // Gutter icon with 'down' arrow as this is consumed somewhere else,
      // possible consumers are:
      // * @Provides method parameter
      // * @Inject constructor parameter
      // * @Inject field
      @AssistedFactory
      interface FooFactory {
          // Gutter icon with 'up arrow' to Foo's constructor with @AssistedInject
          fun create(id: String): Foo
      }
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    val assistedInject =
      myFixture
        .addFileToProject(
          "test/AssistedInject.kt",
          // language=kotlin
          """
      package test

      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject

      // Gutter icon in constructor (or in class as this is primary Kotlin constructor)
      // with 'down arrow', indicating this is consumed somewhere else. Link goes
      // to FooFactory#create()
      class Foo @AssistedInject constructor(
          // Gutter icon with the 'up' arrow, link to 'Repository' provider.
          val repository: Repository,
          // This is the assisted value, it does not need a gutter icon since the
          // @AssistedInject constructor links to the assisted factory.
          @Assisted val id: String
      )
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    // Assisted Inject constructor tests
    with(assistedInject) {
      myFixture.configureFromExistingVirtualFile(this)
      myFixture.moveCaret("class Foo @AssistedInject constructo|r")
      val icons = myFixture.findGuttersAtCaret()
      assertThat(icons).isNotEmpty()

      val icon =
        icons.find { it.tooltipText == "Foo(Repository, String) is created by create" }!!
          as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
      val gotoRelatedItems = getGotoElements(icon)
      assertThat(gotoRelatedItems).hasSize(1)
      val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiNamedElement).name}" }
      assertThat(result).containsExactly("AssistedFactory methods: create")

      clickOnIcon(icon)
      assertThat(trackerService.calledMethods).hasSize(4)
      assertThat(trackerService.calledMethods[0])
        .startsWith("trackGutterWasDisplayed owner: ASSISTED_INJECTED_CONSTRUCTOR time: ")
      assertThat(
          trackerService.calledMethods[0]
            .removePrefix("trackGutterWasDisplayed owner: ASSISTED_INJECTED_CONSTRUCTOR time: ")
            .toInt()
        )
        .isNotNull()
      assertThat(trackerService.calledMethods[2])
        .isEqualTo("trackClickOnGutter ASSISTED_INJECTED_CONSTRUCTOR")
      assertThat(trackerService.calledMethods[3])
        .isEqualTo(
          "trackNavigation CONTEXT_GUTTER ASSISTED_INJECTED_CONSTRUCTOR ASSISTED_FACTORY_METHOD"
        )
    }

    // Assisted Factory method tests
    with(assistedFactory) {
      myFixture.configureFromExistingVirtualFile(this)
      myFixture.moveCaret("creat|e")
      val icons = myFixture.findGuttersAtCaret()
      assertThat(icons).isNotEmpty()

      val icon =
        icons.find { it.tooltipText == "create(String) is defined by Foo" }!!
          as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
      val gotoRelatedItems = getGotoElements(icon)
      assertThat(gotoRelatedItems).hasSize(1)
      val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiNamedElement).name}" }
      assertThat(result).containsExactly("AssistedInject constructors: Foo")

      clickOnIcon(icon)
      assertThat(trackerService.calledMethods).hasSize(7)
      assertThat(trackerService.calledMethods[4])
        .startsWith("trackGutterWasDisplayed owner: ASSISTED_FACTORY_METHOD time: ")
      assertThat(
          trackerService.calledMethods[4]
            .removePrefix("trackGutterWasDisplayed owner: ASSISTED_FACTORY_METHOD time: ")
            .toInt()
        )
        .isNotNull()
      assertThat(trackerService.calledMethods[5])
        .isEqualTo("trackClickOnGutter ASSISTED_FACTORY_METHOD")
      assertThat(trackerService.calledMethods[6])
        .isEqualTo(
          "trackNavigation CONTEXT_GUTTER ASSISTED_FACTORY_METHOD ASSISTED_INJECTED_CONSTRUCTOR"
        )
    }
  }

  fun testAssistedInjectProducersAndConsumers() {
    val repository =
      myFixture
        .addFileToProject(
          "test/Repository.kt",
          // language=kotlin
          """
      package test
      import javax.inject.Inject

      class Repository @Inject constructor()
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile
    val assistedFactory =
      myFixture
        .addFileToProject(
          "test/AssistedFactory.kt",
          // language=kotlin
          """
      package test

      import dagger.assisted.AssistedFactory

      // Gutter icon with 'down' arrow as this is consumed somewhere else,
      // possible consumers are:
      // * @Provides method parameter
      // * @Inject constructor parameter
      // * @Inject field
      @AssistedFactory
      interface FooFactory {
          // Gutter icon with 'up arrow' to Foo's constructor with @AssistedInject
          fun create(id: String): Foo
      }

    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    val assistedInject =
      myFixture
        .addFileToProject(
          "test/AssistedInject.kt",
          // language=kotlin
          """
      package test

      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject

      // Gutter icon in constructor (or in class as this is primary Kotlin constructor)
      // with 'down arrow', indicating this is consumed somewhere else. Link goes
      // to FooFactory#create()
      class Foo @AssistedInject constructor(
          // Gutter icon with the 'up' arrow, link to 'Repository' provider.
          val repository: Repository,
          // This is the assisted value, it does not need a gutter icon since the
          // @AssistedInject constructor links to the assisted factory.
          @Assisted val id: String
      )
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    val consumingClass =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;
      import javax.inject.Inject;

      class MyClass {
        @Inject FooFactory myFooFactory;
      }

    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    checkGutterIcon(
      repository,
      "constru|ctor()",
      "Repository() provides for Foo",
      listOf("Consumers: repository"),
      DEPENDENCY_CONSUMER
    )

    checkGutterIcon(
      consumingClass,
      "@Inject FooFactory myFoo|Factory;",
      "myFooFactory consumes FooFactory",
      listOf("Providers: FooFactory"),
      DEPENDENCY_PROVIDER
    )

    checkGutterIcon(
      assistedFactory,
      "interface FooF|actory",
      "FooFactory provides for MyClass",
      listOf("Consumers: myFooFactory"),
      DEPENDENCY_CONSUMER
    )

    checkGutterIcon(
      assistedInject,
      "val repo|sitory: Repository,",
      "repository consumes Repository()",
      listOf("Providers: Repository"),
      DEPENDENCY_PROVIDER
    )

    // On the @AssistedInject-annotated constructor, any parameter annotated with @Assisted should
    // not have any icons on it.
    myFixture.configureFromExistingVirtualFile(assistedInject)
    myFixture.moveCaret("@Assisted val i|d: String")
    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isEmpty()
  }

  private fun checkGutterIcon(
    virtualFile: VirtualFile,
    caretLocation: String,
    tooltipText: String,
    resultStrings: List<String>,
    expectedIcon: Icon
  ) {
    myFixture.configureFromExistingVirtualFile(virtualFile)
    myFixture.moveCaret(caretLocation)

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()
    assertThat(icons.map { it.icon }).contains(expectedIcon)

    val gotoRelatedItems = getGotoElements(icons.find { it.tooltipText == tooltipText }!!)
    assertThat(gotoRelatedItems).hasSize(resultStrings.size)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiNamedElement).name}" }
    assertThat(result).containsExactlyElementsIn(resultStrings)
  }

  fun testObjectClassInKotlin() {
    val moduleFile =
      myFixture
        .addFileToProject(
          "test/MyModule.kt",
          // language=kotlin
          """
        package test
        import dagger.Module

        @Module
        object MyModule
      """
            .trimIndent()
        )
        .virtualFile

    // Enum shouldn't be treated as dagger entity.
    val notModuleFile =
      myFixture
        .addFileToProject(
          "test/NotMyModule.kt",
          // language=kotlin
          """
        package test
        import dagger.Module

        enum class NotMyModule {
          @Module
          ONE
        }
      """
            .trimIndent()
        )
        .virtualFile

    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {
      }
    """
        .trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(moduleFile)
    myFixture.moveCaret("MyMod|ule")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems =
      getGotoElements(
        icons.find { it.tooltipText == "MyModule is included in component MyComponent" }!!
      )
    assertThat(gotoRelatedItems).hasSize(1)
    val result = gotoRelatedItems.map { "${it.group}: ${it.element?.className()}" }
    assertThat(result).containsExactly("Included in components: MyComponent")

    myFixture.configureFromExistingVirtualFile(notModuleFile)
    myFixture.moveCaret("ON|E")

    assertThat(myFixture.findGuttersAtCaret()).isEmpty()
  }

  fun testBindsInstance() {
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

    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import javax.inject.Inject;

      class MyClass {
        @Inject String injectedString;
        @Inject @MyQualifier String injectedStringWithQualifier;
      }

    """
        .trimIndent()
    )

    myFixture.loadNewFile(
      "test/MyComponent.kt",
      // language=kotlin
      """
      package test
      import dagger.Component
      import dagger.BindsInstance
      import test.MyQualifier

      @Component
      interface MyComponent {
        @Component.Factory
        interface MyComponentFactory
        {
          fun bindString(@BindsInstance @MyQualifier str:String):String
        }
      }
      """
        .trimIndent()
    )

    myFixture.moveCaret("st|r")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon =
      icons.find { it.tooltipText == "str provides for MyClass" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(1)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiNamedElement).name}" }
    assertThat(result).containsExactly("Consumers: injectedStringWithQualifier")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods).hasSize(3)
    assertThat(trackerService.calledMethods[0])
      .startsWith("trackGutterWasDisplayed owner: PROVIDER time: ")
    assertThat(
        trackerService.calledMethods[0]
          .removePrefix("trackGutterWasDisplayed owner: PROVIDER time: ")
          .toInt()
      )
      .isNotNull()
    assertThat(trackerService.calledMethods[1]).isEqualTo("trackClickOnGutter PROVIDER")
    assertThat(trackerService.calledMethods[2])
      .isEqualTo("trackNavigation CONTEXT_GUTTER PROVIDER CONSUMER")
  }

  fun testModulesForSubcomponent() {
    myFixture.addClass(
      // language=JAVA
      """
        package test;

        import dagger.Module;

        @Module
        class MyModule { }
      """
        .trimIndent()
    )
    // Java Subomponent
    val file =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;
      import dagger.Subcomponent;

      @Subcomponent(modules = { MyModule.class })
      public interface MySubcomponent {}
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    myFixture.configureFromExistingVirtualFile(file)
    myFixture.moveCaret("MySubcompon|ent")
    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val gotoRelatedItems =
      getGotoElements(icons.find { it.tooltipText == "MySubcomponent includes module MyModule" }!!)
    assertThat(gotoRelatedItems).hasSize(1)
    val result = gotoRelatedItems.map { "${it.group}: ${it.element?.className()}" }
    assertThat(result).containsExactly("Modules included: MyModule")
  }

  fun testSubcomponentsForSubcomponent() {
    // Java Subcomponent
    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent
      public interface MySubcomponent {}
    """
        .trimIndent()
    )

    myFixture.addClass(
      // language=JAVA
      """
        package test;

        import dagger.Module;

        @Module(subcomponents = { MySubcomponent.class })
        class MyModule { }
      """
        .trimIndent()
    )

    // Java parent Subcomponent
    val file =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;
      import dagger.Subcomponent;

      @Subcomponent(modules = { MyModule.class })
      public interface MyParentSubcomponent {}
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    myFixture.configureFromExistingVirtualFile(file)
    myFixture.moveCaret("MyParent|Subcomponent")
    val icons = myFixture.findGuttersAtCaret()
    val icon =
      icons.find { it.tooltipText == "Dependency Related Files for MyParentSubcomponent" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    val result = gotoRelatedItems.map { "${it.group}: ${(it.element as PsiNamedElement).name}" }
    assertThat(result).contains("Subcomponents: MySubcomponent")

    clickOnIcon(icon)
    assertThat(trackerService.calledMethods.last()).isEqualTo("trackClickOnGutter SUBCOMPONENT")
    gotoRelatedItems.find { it.group == "Subcomponents" }!!.navigate()
    assertThat(trackerService.calledMethods.last())
      .isEqualTo("trackNavigation CONTEXT_GUTTER SUBCOMPONENT SUBCOMPONENT")
  }

  fun testEntryPointMethodsForProvider() {
    // Provider
    val providerFile =
      myFixture
        .configureByText(
          // language=JAVA
          JavaFileType.INSTANCE,
          """
        package test;
        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides String provider() {}
        }
      """
            .trimIndent()
        )
        .virtualFile

    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Component;

      @Component(modules = { MyModule.class })
      public interface MyComponent {
        String getString();
      }
    """
        .trimIndent()
    )

    val entryPointFile =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;
      import dagger.hilt.EntryPoint;

      @EntryPoint
      public interface MyEntryPoint {
        String getStringInEntryPoint();
      }
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    myFixture.configureFromExistingVirtualFile(entryPointFile)
    myFixture.moveCaret("getStringInEntr|yPoint")

    val iconForComponentMethod =
      myFixture.findGuttersAtCaret().find {
        it.tooltipText == "getStringInEntryPoint() exposes provider()"
      }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItemForComponentMethod = getGotoElements(iconForComponentMethod).first()

    assertThat(gotoRelatedItemForComponentMethod.group).isEqualTo("Providers")
    assertThat((gotoRelatedItemForComponentMethod.element as PsiNamedElement).name)
      .isEqualTo("provider")

    myFixture.configureFromExistingVirtualFile(providerFile)
    myFixture.moveCaret("@Provides String pr|ovider")

    val icons = myFixture.findGuttersAtCaret()
    assertThat(icons).isNotEmpty()

    val icon =
      icons.find { it.tooltipText == "Dependency Related Files for provider()" }!!
        as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    val gotoRelatedItems = getGotoElements(icon)
    assertThat(gotoRelatedItems).hasSize(2)
    val method = gotoRelatedItems.find { it.group == "Exposed by entry points" }!!
    assertThat(method.element?.text).isEqualTo("String getStringInEntryPoint();")
    assertThat(method.customName).isEqualTo("MyEntryPoint")

    method.navigate()
    assertThat(trackerService.calledMethods.last())
      .isEqualTo("trackNavigation CONTEXT_GUTTER PROVIDER ENTRY_POINT_METHOD")
  }

  fun testNavigationGoesToTargetElement() {
    myFixture.configureByText(
      "Components.kt",
      // language=kotlin
      """
      import dagger.Component

      @Component
      interface MyComp<caret>onent {}

      @Component(dependencies = [MyComponent::class])
      interface MyDependentComponent
      """
        .trimIndent()
    )

    clickOnIcon(
      myFixture.findGuttersAtCaret().single() as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
    )

    myFixture.checkResult(
      // language=kotlin
      """
      import dagger.Component

      @Component
      interface MyComponent {}

      @Component(dependencies = [MyComponent::class])
      interface <caret>MyDependentComponent
      """
        .trimIndent()
    )
  }

  private fun PsiElement.className(): String? =
    when (this) {
      is PsiClass -> name
      is KtClass -> name
      else -> throw IllegalArgumentException()
    }
}

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerRelatedItemLineMarkerProviderTest {

  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val myFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Test
  fun canReceiveLineMarker_kotlin() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      class Foo constructor() {
        val property: Int = 0
      }
      """
        .trimIndent()
    )

    assertThat(myFixture.moveCaret("cla|ss Foo constructor").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("class Fo|o constructor").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture.findParentElement<KtClass>("class Fo|o constructor").canReceiveLineMarker()
      )
      .isFalse()
    assertThat(myFixture.moveCaret("class Foo constr|uctor").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture.findParentElement<KtFunction>("class Foo constr|uctor").canReceiveLineMarker()
      )
      .isFalse()

    assertThat(myFixture.moveCaret("va|l property: Int = 0").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("val prop|erty: Int = 0").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture.findParentElement<KtProperty>("val prop|erty: Int = 0").canReceiveLineMarker()
      )
      .isFalse()
    assertThat(myFixture.moveCaret("val property: In|t = 0").canReceiveLineMarker()).isTrue()
    assertThat(myFixture.moveCaret("val property: Int = |0").canReceiveLineMarker()).isFalse()
  }

  @Test
  fun canReceiveLineMarker_java() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      public class Foo {
        public Foo() {}

        private int property = 0;
      }
      """
        .trimIndent()
    )

    assertThat(myFixture.moveCaret("cla|ss Foo").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("class Fo|o").canReceiveLineMarker()).isTrue()
    assertThat(myFixture.findParentElement<PsiClass>("class Fo|o").canReceiveLineMarker()).isFalse()

    assertThat(myFixture.moveCaret("pub|lic Foo()").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("public Fo|o()").canReceiveLineMarker()).isTrue()
    assertThat(myFixture.findParentElement<PsiMethod>("public Fo|o()").canReceiveLineMarker())
      .isFalse()

    assertThat(myFixture.moveCaret("pri|vate int property = 0;").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("private in|t property = 0;").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("private int pro|perty = 0;").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture.findParentElement<PsiField>("private int pro|perty = 0;").canReceiveLineMarker()
      )
      .isFalse()
    assertThat(myFixture.moveCaret("private int property = |0;").canReceiveLineMarker()).isFalse()
  }

  @Test
  fun gotoItemOrdering() {
    val mockRelatedElements =
      listOf(
        DaggerRelatedElement(
          createMockDaggerElement("ElementName8"),
          "Consumers",
          "navigate.to.consumer",
          "CustomName8"
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName7"),
          "Providers",
          "navigate.to.provider",
          "CustomName7"
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName4"),
          "Consumers",
          "navigate.to.consumer",
          null
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName3"),
          "Providers",
          "navigate.to.provider",
          null
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName6"),
          "Consumers",
          "navigate.to.consumer",
          "CustomName6"
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName5"),
          "Providers",
          "navigate.to.provider",
          "CustomName5"
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName2"),
          "Consumers",
          "navigate.to.consumer",
          null
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName1"),
          "Providers",
          "navigate.to.provider",
          null
        ),
      )

    val mockDaggerElement: AssistedFactoryMethodDaggerElement = MockitoKt.mock()
    MockitoKt.whenever(mockDaggerElement.getRelatedDaggerElements()).thenReturn(mockRelatedElements)

    val gotoItems = mockDaggerElement.getGotoItems()

    // Items are sorted at a higher level by their group.
    assertThat(gotoItems.map { it.group })
      .containsExactlyElementsIn(List(4) { "Consumers" } + List(4) { "Providers" })
      .inOrder()

    // Within the group, items are sorted by their names. We can't directly verify the display text
    // since it's controlled by the platform, but we can validate that the goto items contain the
    // associated PsiElements that we expect in the correct order.
    assertThat(gotoItems.map { it.element })
      .containsExactly(
        mockRelatedElements[4].relatedElement.psiElement, // CustomName6 (Consumers)
        mockRelatedElements[0].relatedElement.psiElement, // CustomName8 (Consumers)
        mockRelatedElements[6].relatedElement.psiElement, // ElementName2 (Consumers)
        mockRelatedElements[2].relatedElement.psiElement, // ElementName4 (Consumers)
        mockRelatedElements[5].relatedElement.psiElement, // CustomName5 (Providers)
        mockRelatedElements[1].relatedElement.psiElement, // CustomName7 (Providers)
        mockRelatedElements[7].relatedElement.psiElement, // ElementName1 (Providers)
        mockRelatedElements[3].relatedElement.psiElement, // ElementName3 (Providers)
      )
      .inOrder()
  }

  private fun createMockDaggerElement(elementText: String): DaggerElement {
    val psiElement = myFixture.addClass("class $elementText {}")
    val mockDaggerElement: AssistedFactoryMethodDaggerElement = MockitoKt.mock()
    MockitoKt.whenever(mockDaggerElement.psiElement).thenReturn(psiElement)

    return mockDaggerElement
  }
}
