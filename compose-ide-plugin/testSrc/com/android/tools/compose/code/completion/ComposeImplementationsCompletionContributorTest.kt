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
package com.android.tools.compose.code.completion

import com.android.tools.compose.COMPOSABLE_FQ_NAMES_ROOT
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test


/**
 * Test for [ComposeImplementationsCompletionContributor].
 */
class ComposeImplementationsCompletionContributorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(COMPOSABLE_FQ_NAMES_ROOT)

    myFixture.addFileToProject(
      "/src/androidx/compose/ui/Alignment.kt",
      //language=kotlin
      """
      package androidx.compose.ui


      fun interface Alignment {
        fun interface Horizontal
        fun interface Vertical

        companion object {
          val Top: Vertical = object : Vertical {}
          val CenterVertically: Vertical = object : Vertical {}
          val Bottom: Vertical = object : Vertical {}

          val Start: Horizontal = object : Horizontal {}
          val CenterHorizontally: Horizontal = object : Horizontal {}
          val End: Horizontal = object : Horizontal {}
        }
      }
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/src/androidx/compose/foundation/layout/Arrangement.kt",
      //language=kotlin
      """
      package androidx.compose.foundation.layout


      object Arrangement {
        interface Horizontal
        interface Vertical
        interface HorizontalOrVertical : Horizontal, Vertical

        val Start = object : Horizontal {}
        val End = object : Horizontal {}
        val Top = object : Vertical {}
        val Bottom = object : Vertical {}
        val Center = object : HorizontalOrVertical {}
        val SpaceAround = object : HorizontalOrVertical {}
      }
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/src/com/example/Rows.kt",
      //language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Alignment
      import androidx.compose.foundation.layout.Arrangement

      @Composable
      fun RowWithAlignment(
          horizontalAlignment: Alignment.Horizontal = Alignment.Start,
          verticalAlignment: Alignment.Vertical = Alignment.Top,
          content: @Composable () -> Unit
      )

      @Composable
      fun RowWithArrangement(
          horizontalArrangement: Arrangement.Horizontal = Alignment.Start,
          verticalArrangement: Arrangement.Vertical = Alignment.Top,
          content: @Composable () -> Unit
      )
    """.trimIndent())
  }

  @RunsInEdt
  @Test
  fun testHorizontalAlignmentCompletion() {

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      //language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithAlignment(<caret>)
      }
      """.trimIndent())

    myFixture.completeBasic()

    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).containsAllOf("Alignment.Start", "Alignment.CenterHorizontally", "Alignment.End")

    myFixture.lookup.currentItem = myFixture.lookupElements.find { it.lookupString == "Alignment.Start" }
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Alignment

      @Composable
      fun HomeScreen() {
        RowWithAlignment(Alignment.Start)
      }
      """.trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun testVerticalAlignmentCompletion() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      //language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithAlignment(verticalAlignment = <caret>)
      }
      """.trimIndent())
    myFixture.completeBasic()

    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).containsAllOf("Alignment.Top", "Alignment.CenterVertically", "Alignment.Bottom")

    myFixture.lookup.currentItem = myFixture.lookupElements.find { it.lookupString == "Alignment.CenterVertically" }
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Alignment

      @Composable
      fun HomeScreen() {
        RowWithAlignment(verticalAlignment = Alignment.CenterVertically)
      }
      """.trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun testHorizontalArrangementCompletion() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      //language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(<caret>)
      }
      """.trimIndent())

    myFixture.completeBasic()

    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).containsAllOf("Arrangement.Start", "Arrangement.Center", "Arrangement.End")

    myFixture.lookup.currentItem = myFixture.lookupElements.find { it.lookupString == "Arrangement.Start" }
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.foundation.layout.Arrangement
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(Arrangement.Start)
      }
      """.trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun testVerticalArrangementCompletion() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      //language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(verticalArrangement = <caret>)
      }
      """.trimIndent())
    myFixture.completeBasic()

    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings).containsAllOf("Arrangement.Top", "Arrangement.Bottom", "Arrangement.Center")

    myFixture.lookup.currentItem = myFixture.lookupElements.find { it.lookupString == "Arrangement.Top" }
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.foundation.layout.Arrangement
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(verticalArrangement = Arrangement.Top)
      }
      """.trimIndent()
    )
  }
}