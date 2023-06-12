/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose

import androidx.compose.compiler.plugins.kotlin.ComposeFqNames
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposableElementAutomaticRenamerFactoryTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val myFixture: CodeInsightTestFixtureImpl by lazy { projectRule.fixture as CodeInsightTestFixtureImpl }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(COMPOSABLE_FQ_NAMES_ROOT)
  }

  @RunsInEdt
  @Test
  fun testRenaming() {
    val kotlinFile = myFixture.addFileToProject(
      "/scr/com/example/Greeting.kt",
      //language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun Greeting() {}
    """.trimIndent())

    val javaFile = myFixture.addFileToProject(
      "src/com/example/MyClass.java",
      //language=Java
      """
      package com.example;

      public class MyClass {
        public static void callComposable() {
          GreetingKt.Greeting();
        }
      }
    """.trimIndent())

    myFixture.openFileInEditor(kotlinFile.virtualFile)
    myFixture.moveCaret("Gree|ting")
    myFixture.renameElementAtCaret("GreetingNew")

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun GreetingNew() {}
      """.trimIndent()
    )

    // Check the file name is changed.
    assertThat(myFixture.file?.name).isEqualTo("GreetingNew.kt")

    //Check references to the file name are changed.
    myFixture.openFileInEditor(javaFile.virtualFile)
    myFixture.checkResult(
      """
      package com.example;

      public class MyClass {
        public static void callComposable() {
          GreetingNewKt.GreetingNew();
        }
      }
      """.trimIndent()
    )
  }
}