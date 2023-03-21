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
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.registerServiceInstance
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.impl.UsageViewImpl
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.junit.Assert

class DaggerCustomUsageSearcherTest : DaggerTestCase() {

  private fun findAllUsages(targetElement: PsiElement): MutableSet<Usage> {
    val usagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
    val handler = usagesManager.getFindUsagesHandler(targetElement, false)
    Assert.assertNotNull("Cannot find handler for: $targetElement", handler)
    val usageView =
      usagesManager.doFindUsages(
        handler!!.primaryElements,
        handler.secondaryElements,
        handler,
        handler.findUsagesOptions,
        false
      ) as UsageViewImpl

    return usageView.usages
  }

  fun testProviders() {
    myFixture.addClass(
      // language=JAVA
      """
        package myExample;

        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides String provider() {}
        }
      """
        .trimIndent()
    )

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        package myExample;

        import javax.inject.Inject;

        class MyClass {
          @Inject String ${caret}injectedString;
        }
      """
        .trimIndent()
    )

    val trackerService = TestDaggerAnalyticsTracker()
    project.registerServiceInstance(DaggerAnalyticsTracker::class.java, trackerService)

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      | Usages in Project Files (1)
      |  Providers (1)
      |   ${module.name} (1)
      |    myExample (1)
      |     MyModule (1)
      |      provider() (1)
      |       8@Provides String provider() {}
      """
          .trimMargin()
      )

    assertThat(trackerService.calledMethods).hasSize(1)
    assertThat(trackerService.calledMethods.last())
      .startsWith("trackFindUsagesNodeWasDisplayed owner: CONSUMER time:")
    assertThat(
        trackerService.calledMethods
          .last()
          .removePrefix("trackFindUsagesNodeWasDisplayed owner: CONSUMER time: ")
          .toInt()
      )
      .isNotNull()

    val usage =
      findAllUsages(myFixture.elementAtCaret).filterIsInstance<UsageInfo2UsageAdapter>().first()

    assertThat(trackerService.calledMethods).hasSize(2)
    assertThat(trackerService.calledMethods.last())
      .startsWith("trackFindUsagesNodeWasDisplayed owner: CONSUMER time: ")
    assertThat(
        trackerService.calledMethods
          .last()
          .removePrefix("trackFindUsagesNodeWasDisplayed owner: CONSUMER time: ")
          .toInt()
      )
      .isNotNull()

    usage.navigate(false)

    assertThat(trackerService.calledMethods.last())
      .isEqualTo("trackNavigation CONTEXT_USAGES CONSUMER PROVIDER")
  }

  fun testProvidersFromKotlin() {
    myFixture.addFileToProject(
      "MyClass.kt",
      // language=kotlin
      """
        package example

        import dagger.Provides
        import dagger.BindsInstance
        import dagger.Module

        @Module
        class MyModule {
          @Provides fun provider():String {}
          @Provides fun providerInt():Int {}
          @BindsInstance fun bindsMethod():String {}
          fun builder(@BindsInstance str:String) {}
        }
      """
        .trimIndent()
    )

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        package example;

        import javax.inject.Inject;

        class MyClass {
          @Inject String ${caret}injectedString;
        }
      """
        .trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      | Usages in Project Files (3)
      |  Providers (3)
      |   ${module.name} (3)
      |     (3)
      |     MyClass.kt (3)
      |      MyModule (3)
      |       builder (1)
      |        12fun builder(@BindsInstance str:String) {}
      |       9@Provides fun provider():String {}
      |       11@BindsInstance fun bindsMethod():String {}
      """
          .trimMargin()
      )
  }

  fun testInjectedConstructor() {
    myFixture.addClass(
      // language=JAVA
      """
        package myExample;

        import javax.inject.Inject;

        public class MyProvider {
          @Inject public MyProvider() {}
        }
      """
        .trimIndent()
    )

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        package myExample;

        import javax.inject.Inject;

        class MyClass {
          @Inject MyProvider ${caret}injectedString;
        }
      """
        .trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      | Usages in Project Files (1)
      |  Providers (1)
      |   ${module.name} (1)
      |    myExample (1)
      |     MyProvider (1)
      |      MyProvider() (1)
      |       6@Inject public MyProvider() {}
      """
          .trimMargin()
      )
  }

  fun testInjectedConstructor_kotlin() {
    myFixture.addFileToProject(
      "MyProvider.kt",
      // language=kotlin
      """
        import javax.inject.Inject

        class MyProvider @Inject constructor()
      """
        .trimIndent()
    )

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        class MyClass {
          @Inject MyProvider ${caret}injectedString;
        }
      """
        .trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      | Usages in Project Files (1)
      |  Providers (1)
      |   ${module.name} (1)
      |     (1)
      |     MyProvider.kt (1)
      |      MyProvider (1)
      |       3class MyProvider @Inject constructor()
      """
          .trimMargin()
      )
  }

  fun testBinds() {
    myFixture.addClass(
      // language=JAVA
      """
        package myExample;

        import dagger.Binds;
        import dagger.Module;

        @Module
        abstract class MyModule {
          @Binds abstract String bindsMethod(String s) {}
        }
      """
        .trimIndent()
    )

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        package myExample;

        import javax.inject.Inject;

        class MyClass {
          @Inject String ${caret}injectedString;
        }
      """
        .trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      | Usages in Project Files (1)
      |  Providers (1)
      |   ${module.name} (1)
      |    myExample (1)
      |     MyModule (1)
      |      bindsMethod(String) (1)
      |       8@Binds abstract String bindsMethod(String s) {}
      """
          .trimMargin()
      )
  }

  fun testBindsFromKotlin() {
    myFixture.addFileToProject(
      "MyClass.kt",
      // language=kotlin
      """
        package example

        import dagger.Binds
        import dagger.Module

        @Module
        abstract class MyModule {
          @Binds abstract fun bindsMethod(s: String):String {}
          @Binds abstract fun bindsMethodInt(i: Int):Int {}
        }
      """
        .trimIndent()
    )

    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        package example;

        import javax.inject.Inject;

        class MyClass {
          @Inject String ${caret}injectedString;
        }
      """
        .trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      | Usages in Project Files (1)
      |  Providers (1)
      |   ${module.name} (1)
      |     (1)
      |     MyClass.kt (1)
      |      MyModule (1)
      |       8@Binds abstract fun bindsMethod(s: String):String {}
      """
          .trimMargin()
      )
  }

  fun testBinds_for_param() {
    myFixture.addFileToProject(
      "MyClass.kt",
      // language=kotlin
      """
        package example

        import dagger.Binds
        import dagger.Module

        @Module
        abstract class MyModule {
          @Binds abstract fun bindsMethod(s: String):String {}
          @Binds abstract fun bindsMethodInt(i: Int):Int {}
        }
      """
        .trimIndent()
    )

    // JAVA consumer
    myFixture.configureByText(
      // language=JAVA
      JavaFileType.INSTANCE,
      """
        package example;

        import javax.inject.Inject;

        class MyClass {
          @Inject MyClass(String ${caret}str) {}
        }
      """
        .trimIndent()
    )

    var presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      | Usages in Project Files (1)
      |  Providers (1)
      |   ${module.name} (1)
      |     (1)
      |     MyClass.kt (1)
      |      MyModule (1)
      |       8@Binds abstract fun bindsMethod(s: String):String {}
      """
          .trimMargin()
      )

    // kotlin consumer
    myFixture.configureByText(
      // language=kotlin
      KotlinFileType.INSTANCE,
      """
        import javax.inject.Inject

        class MyClass @Inject constructor(${caret}strKotlin: String)
      """
        .trimIndent()
    )

    presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      | Usages in Project Files (1)
      |  Providers (1)
      |   ${module.name} (1)
      |     (1)
      |     MyClass.kt (1)
      |      MyModule (1)
      |       8@Binds abstract fun bindsMethod(s: String):String {}
      """
          .trimMargin()
      )
  }

  fun testDaggerConsumer() {
    // Dagger provider.
    myFixture
      .loadNewFile(
        "example/MyProvider.java",
        // language=JAVA
        """
        package example;

        import javax.inject.Inject;

        public class MyProvider {
          @Inject public MyProvider() {}
        }
      """
          .trimIndent()
      )
      .containingFile

    val provider: PsiMethod = myFixture.findParentElement("public MyProvi|der()")

    // Dagger consumer as param of @Provides-annotated method.
    myFixture.addClass(
      // language=JAVA
      """
        package example;

        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides String provider(MyProvider consumer) {}
        }
      """
        .trimIndent()
    )

    // Dagger consumer as param of @Inject-annotated constructor.
    myFixture.addClass(
      // language=JAVA
      """
        package example;

        import javax.inject.Inject;

        public class MyClass {
          @Inject public MyClass(MyProvider consumer) {}
        }
      """
        .trimIndent()
    )

    // Dagger consumer as @Inject-annotated field.
    myFixture.addClass(
      // language=JAVA
      """
        package example;

        import javax.inject.Inject;

        public class MyClassWithInjectedField {
          @Inject MyProvider consumer;
        }
      """
        .trimIndent()
    )

    // Dagger consumer as @Inject-annotated field in Kotlin.
    myFixture.addFileToProject(
      "example/MyClassWithInjectedFieldKt.kt",
      // language=kotlin
      """
        package example

        import javax.inject.Inject

        class MyClassWithInjectedFieldKt {
          @Inject val consumer:MyProvider
        }
      """
        .trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(provider)
    assertThat(presentation)
      .contains(
        """
      |  Consumers (4)
      |   ${module.name} (4)
      |    example (4)
      |     MyClassWithInjectedFieldKt.kt (1)
      |      MyClassWithInjectedFieldKt (1)
      |       6@Inject val consumer:MyProvider
      |     MyClass (1)
      |      MyClass(MyProvider) (1)
      |       6@Inject public MyClass(MyProvider consumer) {}
      |     MyClassWithInjectedField (1)
      |      6@Inject MyProvider consumer;
      |     MyModule (1)
      |      provider(MyProvider) (1)
      |       8@Provides String provider(MyProvider consumer) {}
      """
          .trimMargin()
      )
  }

  fun testDaggerComponentMethods() {
    val classFile =
      myFixture
        .addFileToProject(
          "test/MyClass.java",
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
        .virtualFile

    val componentFile =
      myFixture
        .addFileToProject(
          "test/MyComponent.java",
          // language=JAVA
          """
      package test;
      import dagger.Component;

      @Component()
      public interface MyComponent {
        MyClass getMyClass();
      }
    """
            .trimIndent()
        )
        .virtualFile

    myFixture.configureFromExistingVirtualFile(componentFile)
    val componentMethod: PsiMethod = myFixture.findParentElement("getMyCl|ass")

    var presentation = myFixture.getUsageViewTreeTextRepresentation(componentMethod)

    assertThat(presentation)
      .contains(
        """
      |  Providers (1)
      |   ${module.name} (1)
      |    test (1)
      |     MyClass (1)
      |      MyClass() (1)
      |       6@Inject public MyClass() {}
      """
          .trimMargin()
      )

    myFixture.configureFromExistingVirtualFile(classFile)
    val classProvider: PsiMethod = myFixture.findParentElement("@Inject public MyCla|ss")

    presentation = myFixture.getUsageViewTreeTextRepresentation(classProvider)

    assertThat(presentation)
      .contains(
        """
      |  Exposed by components (1)
      |   ${module.name} (1)
      |    test (1)
      |     MyComponent (1)
      |      getMyClass() (1)
      |       6MyClass getMyClass();
      """
          .trimMargin()
      )
  }

  fun testEntryPointMethodsForProvider() {
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

    val entryPointFile =
      myFixture
        .addClass(
          // language=JAVA
          """
      package test;
      import dagger.hilt.EntryPoint;

      @EntryPoint
      public interface MyEntryPoint {
        MyClass getMyClassInEntryPoint();
      }
    """
            .trimIndent()
        )
        .containingFile
        .virtualFile

    myFixture.configureFromExistingVirtualFile(entryPointFile)
    val entryPointMethod: PsiMethod = myFixture.findParentElement("getMyClassInEntry|Point")

    var presentation = myFixture.getUsageViewTreeTextRepresentation(entryPointMethod)

    assertThat(presentation)
      .contains(
        """
      |  Providers (1)
      |   ${module.name} (1)
      |    test (1)
      |     MyClass (1)
      |      MyClass() (1)
      |       6@Inject public MyClass() {}
      """
          .trimMargin()
      )

    myFixture.configureFromExistingVirtualFile(classFile)
    val classProvider: PsiMethod = myFixture.findParentElement("@Inject public MyCla|ss")

    presentation = myFixture.getUsageViewTreeTextRepresentation(classProvider)

    assertThat(presentation)
      .contains(
        """
      |  Exposed by entry points (1)
      |   ${module.name} (1)
      |    test (1)
      |     MyEntryPoint (1)
      |      getMyClassInEntryPoint() (1)
      |       6MyClass getMyClassInEntryPoint();
      """
          .trimMargin()
      )
  }

  fun testUsagesForModules() {
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

      @Component(modules = [MyModule::class])
      interface MyComponentKt
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
    val module: PsiClass = myFixture.findParentElement("class MyMod|ule {}")
    val presentation = myFixture.getUsageViewTreeTextRepresentation(module)

    assertThat(presentation)
      .contains(
        """
      |  Included in components (2)
      |   ${myFixture.module.name} (2)
      |    test (2)
      |     MyComponentKt.kt (1)
      |      5interface MyComponentKt
      |     MyComponent.java (1)
      |      5public interface MyComponent {}
      |  Included in modules (1)
      |   ${myFixture.module.name} (1)
      |    test (1)
      |     MyModule2.java (1)
      |      5class MyModule2 {}
      |  Included in subcomponents (1)
      |   ${myFixture.module.name} (1)
      |    test (1)
      |     MySubcomponent.java (1)
      |      5class MySubcomponent {}
      """
          .trimMargin()
      )
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
    val component: PsiClass = myFixture.findParentElement("MyCompon|ent {}")
    val presentation = myFixture.getUsageViewTreeTextRepresentation(component)

    assertThat(presentation)
      .contains(
        """
      |  Parent components (1)
      |   ${myFixture.module.name} (1)
      |    test (1)
      |     MyDependantComponent.kt (1)
      |      5interface MyDependantComponent
      """
          .trimMargin()
      )
  }

  fun testParentsForSubcomponent() {
    // Java Subcomponent
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

    // Java Subcomponent
    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent(modules = { MyModule.class })
      public interface MyParentSubcomponent {}
    """
        .trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(subcomponentFile)
    val component: PsiClass = myFixture.findParentElement("MySubcompon|ent")
    val presentation = myFixture.getUsageViewTreeTextRepresentation(component)

    assertThat(presentation)
      .contains(
        """
      |  Parent components (3)
      |   ${myFixture.module.name} (3)
      |    test (3)
      |     MyComponentKt.kt (1)
      |      5interface MyComponentKt
      |     MyComponent.java (1)
      |      5public interface MyComponent {}
      |     MyParentSubcomponent.java (1)
      |      5public interface MyParentSubcomponent {}
      """
          .trimMargin()
      )
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
    val component: PsiClass = myFixture.findParentElement("MyParent|Subcomponent")
    val presentation = myFixture.getUsageViewTreeTextRepresentation(component)

    assertThat(presentation)
      .contains(
        """
      |  Subcomponents (1)
      |   ${myFixture.module.name} (1)
      |    test (1)
      |     MySubcomponent.java (1)
      |      5public interface MySubcomponent {}
      """
          .trimMargin()
      )
  }

  fun testSubcomponentAndModulesForComponent() {
    // Java Subcomponent
    myFixture.addClass(
      // language=JAVA
      """
      package test;
      import dagger.Subcomponent;

      @Subcomponent
      public interface MySubcomponent {
        @Subcomponent.Builder
          interface Builder {}
      }
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
    val component: PsiClass = myFixture.findParentElement("MyCompon|ent")
    val presentation = myFixture.getUsageViewTreeTextRepresentation(component)

    assertThat(presentation)
      .contains(
        """
      |  Subcomponents (2)
      |   ${myFixture.module.name} (2)
      |    test (2)
      |     MySubcomponent2.kt (1)
      |      5interface MySubcomponent2
      |     MySubcomponent.java (1)
      |      5public interface MySubcomponent {
      """
          .trimMargin()
      )

    assertThat(presentation)
      .contains(
        """
      |  Modules included (1)
      |   ${myFixture.module.name} (1)
      |    test (1)
      |     MyModule.java (1)
      |      6class MyModule { }
      """
          .trimMargin()
      )
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

    myFixture.configureFromExistingVirtualFile(moduleFile)
    val module: KtClassOrObject = myFixture.findParentElement("MyMod|ule")
    val presentation = myFixture.getUsageViewTreeTextRepresentation(module)

    assertThat(presentation)
      .contains(
        """
      |  Included in components (1)
      |   ${myFixture.module.name} (1)
      |    test (1)
      |     MyComponent.java (1)
      |      5public interface MyComponent {}
      """
          .trimMargin()
      )
  }

  fun testProvidersKotlin() {
    myFixture.addClass(
      // language=JAVA
      """
        package example;

        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides String provider() {}
        }
      """
        .trimIndent()
    )

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import javax.inject.Inject

        class MyClass {
          @Inject val injected<caret>String:String
        }
      """
        .trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
          | Usages in Project Files (1)
          |  Providers (1)
          |   ${myFixture.module.name} (1)
          |    example (1)
          |     MyModule (1)
          |      provider() (1)
          |       8@Provides String provider() {}
          """
          .trimMargin()
      )
  }

  fun testFromKotlinComponentToKotlinSubcomponent() {
    myFixture.addFileToProject(
      "test/MySubcomponent.kt",
      // language=kotlin
      """
      package test

      import dagger.Subcomponent

      @Subcomponent
      interface MySubcomponent
    """
        .trimIndent()
    )

    myFixture.loadNewFile(
      "test/MyComponent.kt",
      // language=kotlin
      """
      package test

      import dagger.Component
      import dagger.Module

      @Module(subcomponents = [ MySubcomponent::class ])
      class MyModule

      @Component(modules = [ MyModule::class])
      interface MyComponen<caret>t
    """
        .trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
          |  Subcomponents (1)
          |   ${myFixture.module.name} (1)
          |    test (1)
          |     MySubcomponent.kt (1)
          |      6interface MySubcomponent
      """
          .trimMargin()
      )
  }

  fun testFromKotlinModuleToKotlinComponent() {
    myFixture.addFileToProject(
      "test/MyComponent.kt",
      // language=kotlin
      """
      package test

      import dagger.Component

      @Component(modules = [MyModule::class])
      interface MyComponent
    """
        .trimIndent()
    )

    myFixture.loadNewFile(
      "test/MyModule.kt",
      // language=kotlin
      """
      package test

      import dagger.Module

      @Module
      class MyModu<caret>le
    """
        .trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      |  Included in components (1)
      |   ${myFixture.module.name} (1)
      |    test (1)
      |     MyComponent.kt (1)
      |      6interface MyComponent
      """
          .trimMargin()
      )
  }

  fun testFromKotlinAssistedInjectedConstructorToKotlinAssistedFactoryMethod() {
    myFixture.loadNewFile(
      "test/FooFactory.kt",
      // language=kotlin
      """
      import dagger.assisted.AssistedFactory

      @AssistedFactory
      interface FooFactory {
          // Is a factory method
          fun create(id: String): Foo
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

      class Foo @AssistedInject con<caret>structor(
          @Assisted val id: String
      )
    """
        .trimIndent()
    )
    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      |  AssistedFactory methods (1)
      |   ${myFixture.module.name} (1)
      |    test (1)
      |     FooFactory.kt (1)
      |      FooFactory (1)
      |       6fun create(id: String): Foo
      """
          .trimMargin()
      )
  }

  fun testFromKotlinAssistedFactoryMethodToKotlinAssistedInjectedConstructor() {
    myFixture.loadNewFile(
      "test/Foo.kt",
      // language=kotlin
      """
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject

      class Foo @AssistedInject constructor(
          @Assisted val id: String
      )
    """
        .trimIndent()
    )
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      import dagger.assisted.AssistedFactory

      @AssistedFactory
      interface FooFactory {
          // Is a factory method
          fun cre<caret>ate(id: String): Foo
      }
    """
        .trimIndent()
    )

    val trackerService = TestDaggerAnalyticsTracker()
    project.registerServiceInstance(DaggerAnalyticsTracker::class.java, trackerService)

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation)
      .contains(
        """
      |  AssistedInject constructors (1)
      |   ${myFixture.module.name} (1)
      |    test (1)
      |     Foo.kt (1)
      |      Foo (1)
      |       4class Foo @AssistedInject constructor(
      """
          .trimMargin()
      )

    assertThat(trackerService.calledMethods).hasSize(1)
    assertThat(trackerService.calledMethods.last())
      .startsWith("trackFindUsagesNodeWasDisplayed owner: ASSISTED_FACTORY_METHOD time: ")
    assertThat(
        trackerService.calledMethods
          .last()
          .removePrefix("trackFindUsagesNodeWasDisplayed owner: ASSISTED_FACTORY_METHOD time: ")
          .toInt()
      )
      .isNotNull()
  }
}
