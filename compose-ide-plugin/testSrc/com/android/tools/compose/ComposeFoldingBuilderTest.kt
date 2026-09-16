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
package com.android.tools.compose

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test for [ComposeFoldingBuilder]. */
class ComposeFoldingBuilderTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixtureImpl by lazy { projectRule.fixture as CodeInsightTestFixtureImpl }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation()
    myFixture.addFileToProject(
      "src/${COMPOSE_UI_PACKAGE.replace(".", "/")}/Modifier.kt",
      // language=kotlin
      """
    package $COMPOSE_UI_PACKAGE

    interface Modifier {
      fun adjust():Modifier
      companion object : Modifier {
        fun adjust():Modifier {}
      }
    }
    """
        .trimIndent(),
    )
    myFixture.addFileToProject(
      "src/androidx/compose/runtime/DisallowComposableCalls.kt",
      // language=kotlin
      """
      package androidx.compose.runtime

      annotation class DisallowComposableCalls
      """
        .trimIndent(),
    )
  }

  @Test
  fun basicModifierChainFolds() {
    assertFolding(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
        val m = Modifier
          .adjust()
          .adjust()
      }
      """,
      """
      package com.example

      import <fold text='...'>androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier</fold>

      @Composable
      fun HomeScreen() <fold text='{...}'>{
        val m = <fold text='Modifier.(...)'>Modifier
          .adjust()
          .adjust()</fold>
      }</fold>
      """
    )
  }

  @Test
  fun nestedComposableFunctions() {
    assertFolding(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Level1() {
        @Composable
        fun Level2() {
          @Composable
          fun Level3() {
            Modifier
              .adjust()
              .adjust()
              .adjust()
          }
        }
      }
      """,
      """
      package com.example

      import <fold text='...'>androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier</fold>

      @Composable
      fun Level1() <fold text='{...}'>{
        @Composable
        fun Level2() <fold text='{...}'>{
          @Composable
          fun Level3() <fold text='{...}'>{
            <fold text='Modifier.(...)'>Modifier
              .adjust()
              .adjust()
              .adjust()</fold>
          }</fold>
        }</fold>
      }</fold>
      """
    )
  }

  @Test
  fun nestedNonComposableLambdaDoesNotFoldModifier() {
    assertFolding(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun ComposableFunction() {
        val explicitLambda: () -> Unit = {
          val notFolded = Modifier
            .adjust()
            .adjust()
        }
      }
      """,
      """
      package com.example

      import <fold text='...'>androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier</fold>

      @Composable
      fun ComposableFunction() <fold text='{...}'>{
        val explicitLambda: () -> Unit = <fold text='{...}'>{
          val notFolded = Modifier
            .adjust()
            .adjust()
        }</fold>
      }</fold>
      """
    )
  }

  @Test
  fun nestedInlineComposableScopeFoldsModifier() {
    assertFolding(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      inline fun <T> myRun(block: () -> T): T = block()

      @Composable
      fun NestedInlineFolds() {
        myRun {
          myRun {
            Modifier
              .adjust()
              .adjust()
          }
        }
      }
      """,
      """
      package com.example

      import <fold text='...'>androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier</fold>

      inline fun <T> myRun(block: () -> T): T = block()

      @Composable
      fun NestedInlineFolds() <fold text='{...}'>{
        myRun <fold text='{...}'>{
          myRun <fold text='{...}'>{
            <fold text='Modifier.(...)'>Modifier
              .adjust()
              .adjust()</fold>
          }</fold>
        }</fold>
      }</fold>
      """
    )
  }

  @Test
  fun composableReturnTypeFoldsModifier() {
    assertFolding(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      fun returnsComposable(): @Composable () -> Unit {
        return {
          Modifier
            .adjust()
            .adjust()
        }
      }
      """,
      """
      package com.example

      import <fold text='...'>androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier</fold>

      fun returnsComposable(): @Composable () -> Unit <fold text='{...}'>{
        return <fold text='{...}'>{
          <fold text='Modifier.(...)'>Modifier
            .adjust()
            .adjust()</fold>
        }</fold>
      }</fold>
      """
    )
  }

  @Test
  fun disallowComposableCallsDoesNotFoldModifier() {
    assertFolding(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.DisallowComposableCalls
      import androidx.compose.ui.Modifier

      inline fun disallow(block: @d () -> Unit) = Unit

      @Composable
      fun ComposableFunction() {
        disallow {
          Modifier
            .adjust()
            .adjust()
        }
      }
      """,
      """
      package com.example

      import <fold text='...'>androidx.compose.runtime.Composable
      import androidx.compose.runtime.DisallowComposableCalls
      import androidx.compose.ui.Modifier</fold>

      inline fun disallow(block: @DisallowComposableCalls () -> Unit) = Unit

      @Composable
      fun ComposableFunction() <fold text='{...}'>{
        disallow <fold text='{...}'>{
          Modifier
            .adjust()
            .adjust()
        }</fold>
      }</fold>
      """
    )
  }

  @Test
  fun crossinlineComposableParameterFoldsModifier() {
    assertFolding(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      inline fun fold(crossinline block: @Composable () -> Unit) = Unit
      inline fun noFold(crossinline block: () -> Unit) = Unit

      @Composable
      fun ComposableFunction() {
        noFold {
          Modifier
            .adjust()
            .adjust()
        }

        fold {
          Modifier
            .adjust()
            .adjust()
        }
      }
      """,
      """
      package com.example

      import <fold text='...'>androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier</fold>

      inline fun fold(crossinline block: @Composable () -> Unit) = Unit
      inline fun noFold(crossinline block: () -> Unit) = Unit

      @Composable
      fun ComposableFunction() <fold text='{...}'>{
        noFold <fold text='{...}'>{
          Modifier
            .adjust()
            .adjust()
        }</fold>

        fold <fold text='{...}'>{
          <fold text='Modifier.(...)'>Modifier
            .adjust()
            .adjust()</fold>
        }</fold>
      }</fold>
      """
    )
  }

  @Test
  fun annotatedLambdaInsideNonComposableLambdaFoldsOnlyAnnotatedScopes() {
    assertFolding(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      val nonComposableAnnotatedLambda = {
        @Composable {
          Modifier
            .adjust()
            .adjust()

          val internalNonComposableAnnotatedLambda = {
            val mod = Modifier
              .adjust()
              .adjust()

            val composableLambda = @Composable {
              Modifier
                .adjust()
                .adjust()
            }
          }
        }
      }
      """,
      """
      package com.example

      import <fold text='...'>androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier</fold>

      val nonComposableAnnotatedLambda = <fold text='{...}'>{
        @Composable <fold text='{...}'>{
          <fold text='Modifier.(...)'>Modifier
            .adjust()
            .adjust()</fold>

          val internalNonComposableAnnotatedLambda = <fold text='{...}'>{
            val mod = Modifier
              .adjust()
              .adjust()

            val composableLambda = @Composable <fold text='{...}'>{
              <fold text='Modifier.(...)'>Modifier
                .adjust()
                .adjust()</fold>
            }</fold>
          }</fold>
        }</fold>
      }</fold>
      """
    )
  }

  private fun assertFolding(source: String, expected: String) {
    myFixture.loadNewFile("src/com/example/Test.kt", source.trimIndent())
    assertThat(myFixture.getFoldingDescription(false, false)).isEqualTo(expected.trimIndent())
  }
}
