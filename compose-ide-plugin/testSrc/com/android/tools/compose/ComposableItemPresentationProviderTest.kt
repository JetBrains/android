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

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComposableItemPresentationProviderTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  private val provider = ComposableItemPresentationProvider()

  @Test
  fun getPresentation_ktFunctionLiteral() {
    projectRule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      """
      package com.example

      val testFunction = <caret>{ }

      """.trimIndent())

    runReadAction {
      val function = runReadAction { projectRule.fixture.elementAtCaret }
      assertThat(function).isInstanceOf(KtFunctionLiteral::class.java)

      val presentation = provider.getPresentation(function as KtFunctionLiteral)
      assertThat(presentation).isNull()
    }
  }

  @Test
  fun getPresentation_functionIsNotComposable_defaultKotlinPresentationReturned() {
    projectRule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      """
      package com.example

      fun testFun<caret>ction(arg0: Int, arg1: Int) {}

      """.trimIndent())

    runReadAction {
      val function = projectRule.fixture.elementAtCaret
      assertThat(function).isInstanceOf(KtFunction::class.java)

      val presentation = provider.getPresentation(function as KtFunction)!!
      assertThat(presentation.presentableText).isEqualTo("testFunction(Int, Int)")
    }
  }

  @Test
  fun getPresentation_functionIsComposable_composablePresentationReturned() {
    projectRule.fixture.addFileToProject("androidx/compose/runtime/Composable.kt", """
      package androidx.compose.runtime

      annotation class Composable

      """.trimIndent())

    projectRule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun testFun<caret>ction(arg0: Int, arg1: Int = 0) {}

      """.trimIndent())

    runReadAction {
      val function = projectRule.fixture.elementAtCaret
      assertThat(function).isInstanceOf(KtFunction::class.java)

      val presentation = provider.getPresentation(function as KtFunction)!!
      assertThat(presentation.presentableText).isEqualTo("@Composable testFunction(arg0: Int, ...)")
    }
  }

  @Test
  fun getPresentation_functionIsComposable_composablePresentationReturnedWithLambda() {
    projectRule.fixture.addFileToProject("androidx/compose/runtime/Composable.kt", """
      package androidx.compose.runtime

      annotation class Composable

      """.trimIndent())

    projectRule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun testFun<caret>ction(arg0: Int, arg1: Int = 0, arg2: @Composable () -> Unit) {}

      """.trimIndent())

    runReadAction {
      val function = projectRule.fixture.elementAtCaret
      assertThat(function).isInstanceOf(KtFunction::class.java)

      val presentation = provider.getPresentation(function as KtFunction)!!
      assertThat(presentation.presentableText).isEqualTo("@Composable testFunction(arg0: Int, ...) {...}")
    }
  }
}
