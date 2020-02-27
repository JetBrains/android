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
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType

class DaggerCustomUsageSearcherTest : DaggerTestCase() {

  fun testProviders() {
    myFixture.addClass(
      //language=JAVA
      """
        package myExample;

        import dagger.Provides;
        import dagger.Module;

        @Module
        class MyModule {
          @Provides String provider() {}
        }
      """.trimIndent()
    )

    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        package myExample;

        import javax.inject.Inject;

        class MyClass {
          @Inject String ${caret}injectedString;
        }
      """.trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation).contains(
      """
      | Found usages (1 usage)
      |  Provided by Dagger (1 usage)
      |   ${module.name} (1 usage)
      |    myExample (1 usage)
      |     MyModule (1 usage)
      |      provider() (1 usage)
      |       8@Provides String provider() {}
      """.trimMargin()
    )
  }

  fun testProvidersFromKotlin() {
    myFixture.addFileToProject(
      "MyClass.kt",
      //language=kotlin
      """
        package example

        import dagger.Provides
        import dagger.Module

        @Module
        class MyModule {
          @Provides fun provider():String {}
          @Provides fun providerInt():Int {}
        }
      """.trimIndent()
    )

    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        package example;

        import javax.inject.Inject;

        class MyClass {
          @Inject String ${caret}injectedString;
        }
      """.trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation).contains(
      """
      | Found usages (1 usage)
      |  Provided by Dagger (1 usage)
      |   ${module.name} (1 usage)
      |     (1 usage)
      |     MyClass.kt (1 usage)
      |      MyModule (1 usage)
      |       8@Provides fun provider():String {}
      """.trimMargin()
    )
  }

  fun testInjectedConstructor() {
    myFixture.addClass(
      //language=JAVA
      """
        package myExample;

        import javax.inject.Inject;

        public class MyProvider {
          @Inject public MyProvider() {}
        }
      """.trimIndent()
    )

    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        package myExample;

        import javax.inject.Inject;

        class MyClass {
          @Inject MyProvider ${caret}injectedString;
        }
      """.trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation).contains(
      """
      | Found usages (1 usage)
      |  Provided by Dagger (1 usage)
      |   ${module.name} (1 usage)
      |    myExample (1 usage)
      |     MyProvider (1 usage)
      |      MyProvider() (1 usage)
      |       6@Inject public MyProvider() {}
      """.trimMargin()
    )
  }

  fun testInjectedConstructor_kotlin() {
    myFixture.addFileToProject(
      "MyProvider.kt",
      //language=kotlin
      """
        import javax.inject.Inject

        class MyProvider @Inject constructor()
      """.trimIndent()
    )

    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        import javax.inject.Inject;

        class MyClass {
          @Inject MyProvider ${caret}injectedString;
        }
      """.trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation).contains(
      """
      | Found usages (1 usage)
      |  Provided by Dagger (1 usage)
      |   ${module.name} (1 usage)
      |     (1 usage)
      |     MyProvider.kt (1 usage)
      |      MyProvider (1 usage)
      |       3class MyProvider @Inject constructor()
      """.trimMargin()
    )
  }

  fun testBinds() {
    myFixture.addClass(
      //language=JAVA
      """
        package myExample;

        import dagger.Binds;
        import dagger.Module;

        @Module
        abstract class MyModule {
          @Binds abstract String bindsMethod(String s) {}
        }
      """.trimIndent()
    )

    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        package myExample;

        import javax.inject.Inject;

        class MyClass {
          @Inject String ${caret}injectedString;
        }
      """.trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation).contains(
      """
      | Found usages (1 usage)
      |  Provided by Dagger (1 usage)
      |   ${module.name} (1 usage)
      |    myExample (1 usage)
      |     MyModule (1 usage)
      |      bindsMethod(String) (1 usage)
      |       8@Binds abstract String bindsMethod(String s) {}
      """.trimMargin()
    )
  }

  fun testBindsFromKotlin() {
    myFixture.addFileToProject(
      "MyClass.kt",
      //language=kotlin
      """
        package example

        import dagger.Binds
        import dagger.Module

        @Module
        abstract class MyModule {
          @Binds abstract fun bindsMethod(s: String):String {}
          @Binds abstract fun bindsMethodInt(i: Int):Int {}
        }
      """.trimIndent()
    )

    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        package example;

        import javax.inject.Inject;

        class MyClass {
          @Inject String ${caret}injectedString;
        }
      """.trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation).contains(
      """
      | Found usages (1 usage)
      |  Provided by Dagger (1 usage)
      |   ${module.name} (1 usage)
      |     (1 usage)
      |     MyClass.kt (1 usage)
      |      MyModule (1 usage)
      |       8@Binds abstract fun bindsMethod(s: String):String {}
      """.trimMargin()
    )
  }

  fun testBinds_for_param() {
    myFixture.addFileToProject(
      "MyClass.kt",
      //language=kotlin
      """
        package example

        import dagger.Binds
        import dagger.Module

        @Module
        abstract class MyModule {
          @Binds abstract fun bindsMethod(s: String):String {}
          @Binds abstract fun bindsMethodInt(i: Int):Int {}
        }
      """.trimIndent()
    )

    // JAVA consumer
    myFixture.configureByText(
      //language=JAVA
      JavaFileType.INSTANCE,
      """
        package example;

        import javax.inject.Inject;

        class MyClass {
          @Inject MyClass(String ${caret}str) {}
        }
      """.trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation).contains(
      """
      | Found usages (1 usage)
      |  Provided by Dagger (1 usage)
      |   ${module.name} (1 usage)
      |     (1 usage)
      |     MyClass.kt (1 usage)
      |      MyModule (1 usage)
      |       8@Binds abstract fun bindsMethod(s: String):String {}
      """.trimMargin()
    )

    // TODO(b/150134125): uncomment
    //
    //// kotlin consumer
    //myFixture.configureByText(
    //  //language=kotlin
    //  KotlinFileType.INSTANCE,
    //  """
    //    import javax.inject.Inject
    //
    //    class MyClass @Inject constructor(${caret}strKotlin: String)
    //  """.trimIndent()
    //)
    //
    //presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    //assertThat(presentation).contains(
    //  """
    //  | Found usages (1 usage)
    //  |  Provided by Dagger (1 usage)
    //  |   ${module.name} (1 usage)
    //  |     (1 usage)
    //  |     MyClass.kt (1 usage)
    //  |      MyModule (1 usage)
    //  |       8@Binds abstract fun bindsMethod(s: String):String {}
    //  """.trimMargin()
    //)
  }

  // TODO(b/150134125): uncomment
  //fun testProvidersKotlin() {
  //  myFixture.addClass(
  //    //language=JAVA
  //    """
  //      import dagger.Provides;
  //
  //      class MyModule {
  //        @Provides String provider() {};
  //      }
  //    """.trimIndent()
  //  )
  //
  //  myFixture.configureByText(
  //    KotlinFileType.INSTANCE,
  //    //language=kotlin
  //    """
  //      import javax.inject.Inject
  //
  //      class MyClass {
  //        @Inject val injectedString:String
  //      }
  //    """.trimIndent()
  //  )
  //
  //    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
  //    assertThat(presentation).contains(
  //    """
  //        | Found usages (1 usage)
  //        |  Provided by Dagger (1 usage)
  //        |   6 (1 usage)
  //        |    myExample (1 usage)
  //        |     MyModule (1 usage)
  //        |      provider() (1 usage)
  //        |       8@Provides String provider() {}
  //        """.trimMargin()
  //    )
  //}
}
