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

  // TODO(): uncomment after fixing https://youtrack.jetbrains.com/issue/KT-36657
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
  //        |       6@Provides String provider() {}
  //        """.trimMargin()
  //    )
  //}
}
