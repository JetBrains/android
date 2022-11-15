/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PsiUtilsTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation("androidx.compose.runtime")
  }

  @Test
  fun isComposableFunction_functionIsComposable() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun Greet${caret}ing() {}
      """.trimIndent()
    )

    runReadAction {
      assertThat(fixture.elementAtCaret.isComposableFunction()).isTrue()
    }
  }

  @Test
  fun isComposableFunction_functionIsNotComposable() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      fun Greet${caret}ing() {}
      """.trimIndent()
    )

    runReadAction {
      assertThat(fixture.elementAtCaret.isComposableFunction()).isFalse()
    }
  }

  @Test
  fun isComposableFunction_elementIsNotFunction() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      val greet${caret}ing = ""
      """.trimIndent()
    )

    runReadAction {
      assertThat(fixture.elementAtCaret.isComposableFunction()).isFalse()
    }
  }
}
