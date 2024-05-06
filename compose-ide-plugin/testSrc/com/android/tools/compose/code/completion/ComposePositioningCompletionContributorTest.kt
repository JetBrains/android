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

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test for [ComposePositioningCompletionContributor]. */
class ComposePositioningCompletionContributorTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation()

    myFixture.addFileToProject(
      "/src/androidx/compose/ui/Alignment.kt",
      // language=kotlin
      """
      package androidx.compose.ui

      fun interface Alignment {
        fun interface Horizontal
        fun interface Vertical

        companion object {
          // 2D Alignments
          val TopStart: Alignment = object : Alignment {}
          val TopCenter: Alignment = object : Alignment {}
          val TopEnd: Alignment = object : Alignment {}
          val CenterStart: Alignment = object : Alignment {}
          val Center: Alignment = object : Alignment {}
          val CenterEnd: Alignment = object : Alignment {}
          val BottomStart: Alignment = object : Alignment {}
          val BottomCenter: Alignment = object : Alignment {}
          val BottomEnd: Alignment = object : Alignment {}

          // 1D Alignment.Verticals
          val Top: Vertical = object : Vertical {}
          val CenterVertically: Vertical = object : Vertical {}
          val Bottom: Vertical = object : Vertical {}

          // 1D Alignment.Horizontals
          val Start: Horizontal = object : Horizontal {}
          val CenterHorizontally: Horizontal = object : Horizontal {}
          val End: Horizontal = object : Horizontal {}
        }
      }

      object AbsoluteAlignment {
        // 2D AbsoluteAlignments
        val TopLeft: Alignment = object : Alignment {}
        val TopRight: Alignment = object : Alignment {}
        val CenterLeft: Alignment = object : Alignment {}
        val CenterRight: Alignment = object : Alignment {}
        val BottomLeft: Alignment = object : Alignment {}
        val BottomRight: Alignment = object : Alignment {}

        // 1D BiasAbsoluteAlignment.Horizontals
        val Left: Alignment.Horizontal = object : Alignment.Horizontal {}
        val Right: Alignment.Horizontal = object : Alignment.Horizontal {}
      }
      """
        .trimIndent()
    )

    myFixture.addFileToProject(
      "/src/androidx/compose/foundation/layout/Arrangement.kt",
      // language=kotlin
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
        val SpaceEvenly = object : HorizontalOrVertical {}
        val SpaceBetween = object : HorizontalOrVertical {}
        val SpaceAround = object : HorizontalOrVertical {}

        object Absolute {
          val Left = object : Horizontal {}
          val Center = object : Horizontal {}
          val Right = object : Horizontal {}
          val SpaceBetween = object : Horizontal {}
          val SpaceEvenly = object : Horizontal {}
          val SpaceAround = object : Horizontal {}
        }
      }
      """
        .trimIndent()
    )

    myFixture.addFileToProject(
      "/src/com/example/Rows.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Alignment
      import androidx.compose.foundation.layout.Arrangement

      @Composable
      fun RowWithAlignment(
          horizontalAlignment: Alignment.Horizontal = Alignment.Start,
          verticalAlignment: Alignment.Vertical = Alignment.Top,
          twoDimensionalAlignment: Alignment = Alignment.Center,
          content: @Composable () -> Unit
      ) {}

      @Composable
      fun RowWithArrangement(
          horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
          verticalArrangement: Arrangement.Vertical = Arrangement.Top,
          horizontalOrVerticalArrangement: Arrangement.HorizontalOrVertical = Arrangement.Center,
          content: @Composable () -> Unit
      ) {}
    """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun horizontalAlignmentCompletion() {

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithAlignment(<caret>)
      }
      """
        .trimIndent()
    )

    myFixture.completeBasic()

    // Ordering: all Horizontal entries on `Alignment` should come first, followed by those on
    // `AbsoluteAlignment`.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 3))
      .containsExactly(
        "Alignment.Start",
        "Alignment.CenterHorizontally",
        "Alignment.End",
      )
    assertThat(lookupStrings.subList(3, 5))
      .containsExactly(
        "AbsoluteAlignment.Left",
        "AbsoluteAlignment.Right",
      )

    val alignmentStartLookupItem =
      myFixture.lookupElements?.find { it.lookupString == "Alignment.Start" }!!

    val presentation = LookupElementPresentation()
    alignmentStartLookupItem.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Alignment.Horizontal")

    myFixture.lookup.currentItem = alignmentStartLookupItem
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
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun horizontalAlignmentCompletion_choosesAbsoluteAlignment() {

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithAlignment(<caret>)
      }
      """
        .trimIndent()
    )

    myFixture.completeBasic()

    // Ordering: all Horizontal entries on `Alignment` should come first, followed by those on
    // `AbsoluteAlignment`.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 3))
      .containsExactly(
        "Alignment.Start",
        "Alignment.CenterHorizontally",
        "Alignment.End",
      )
    assertThat(lookupStrings.subList(3, 5))
      .containsExactly(
        "AbsoluteAlignment.Left",
        "AbsoluteAlignment.Right",
      )

    val alignmentStartLookupItem =
      myFixture.lookupElements?.find { it.lookupString == "AbsoluteAlignment.Left" }!!

    val presentation = LookupElementPresentation()
    alignmentStartLookupItem.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Alignment.Horizontal")

    myFixture.lookup.currentItem = alignmentStartLookupItem
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.AbsoluteAlignment

      @Composable
      fun HomeScreen() {
        RowWithAlignment(AbsoluteAlignment.Left)
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun horizontalAlignmentCompletion_completionWithPartialName() {

    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithAlignment(Alignment.<caret>)
      }
      """
        .trimIndent()
    )

    myFixture.completeBasic()

    // Ordering: all Horizontal entries on `Alignment` should come first. No entries from
    // `AbsoluteAlignment` should be present.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 3))
      .containsExactly(
        "Start",
        "CenterHorizontally",
        "End",
      )

    assertThat(lookupStrings).doesNotContain("AbsoluteAlignment.Left")
    assertThat(lookupStrings).doesNotContain("AbsoluteAlignment.Right")
    assertThat(lookupStrings).doesNotContain("Left")
    assertThat(lookupStrings).doesNotContain("Right")

    val alignmentStartLookupItem = myFixture.lookupElements?.find { it.lookupString == "Start" }!!

    val presentation = LookupElementPresentation()
    alignmentStartLookupItem.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Alignment.Horizontal")

    myFixture.lookup.currentItem = alignmentStartLookupItem
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
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun verticalAlignmentCompletion() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithAlignment(verticalAlignment = <caret>)
      }
      """
        .trimIndent()
    )
    myFixture.completeBasic()

    // Ordering: all Vertical alignments on `Alignment` should come at the top. There are no
    // Vertical entries on `AbsoluteAlignment`.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 3))
      .containsExactly(
        "Alignment.Top",
        "Alignment.CenterVertically",
        "Alignment.Bottom",
      )

    val centerVerticallyLookupElement =
      myFixture.lookupElements?.find { it.lookupString == "Alignment.CenterVertically" }!!

    val presentation = LookupElementPresentation()
    centerVerticallyLookupElement.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Alignment.Vertical")

    myFixture.lookup.currentItem = centerVerticallyLookupElement
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
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun twoDimensionalAlignmentCompletion() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithAlignment(twoDimensionalAlignment = <caret>)
      }
      """
        .trimIndent()
    )
    myFixture.completeBasic()

    // Ordering: all 2D entries on `Alignment` should come first, followed by those on
    // `AbsoluteAlignment`.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 9))
      .containsExactly(
        "Alignment.TopStart",
        "Alignment.TopCenter",
        "Alignment.TopEnd",
        "Alignment.CenterStart",
        "Alignment.Center",
        "Alignment.CenterEnd",
        "Alignment.BottomStart",
        "Alignment.BottomCenter",
        "Alignment.BottomEnd",
      )
    assertThat(lookupStrings.subList(9, 15))
      .containsExactly(
        "AbsoluteAlignment.TopLeft",
        "AbsoluteAlignment.TopRight",
        "AbsoluteAlignment.CenterLeft",
        "AbsoluteAlignment.CenterRight",
        "AbsoluteAlignment.BottomLeft",
        "AbsoluteAlignment.BottomRight",
      )

    val centerVerticallyLookupElement =
      myFixture.lookupElements?.find { it.lookupString == "Alignment.CenterStart" }!!

    val presentation = LookupElementPresentation()
    centerVerticallyLookupElement.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Alignment")

    myFixture.lookup.currentItem = centerVerticallyLookupElement
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Alignment

      @Composable
      fun HomeScreen() {
        RowWithAlignment(twoDimensionalAlignment = Alignment.CenterStart)
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun horizontalArrangementCompletion() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(<caret>)
      }
      """
        .trimIndent()
    )

    myFixture.completeBasic()

    // Ordering: all Horizontal entries on `Arrangement` should come first, followed by those on
    // `Arrangement.Absolute`.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 6))
      .containsExactly(
        "Arrangement.Start",
        "Arrangement.End",
        "Arrangement.Center",
        "Arrangement.SpaceEvenly",
        "Arrangement.SpaceBetween",
        "Arrangement.SpaceAround",
      )
    assertThat(lookupStrings.subList(6, 12))
      .containsExactly(
        "Arrangement.Absolute.Left",
        "Arrangement.Absolute.Center",
        "Arrangement.Absolute.Right",
        "Arrangement.Absolute.SpaceBetween",
        "Arrangement.Absolute.SpaceEvenly",
        "Arrangement.Absolute.SpaceAround",
      )

    val startLookupElement =
      myFixture.lookupElements?.find { it.lookupString == "Arrangement.Center" }!!

    val presentation = LookupElementPresentation()
    startLookupElement.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Arrangement.HorizontalOrVertical")

    myFixture.lookup.currentItem = startLookupElement
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.foundation.layout.Arrangement
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(Arrangement.Center)
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun horizontalArrangementCompletion_choosesAbsoluteArrangement() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(<caret>)
      }
      """
        .trimIndent()
    )

    myFixture.completeBasic()

    // Ordering: all Horizontal entries on `Arrangement` should come first, followed by those on
    // `Arrangement.Absolute`.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 6))
      .containsExactly(
        "Arrangement.Start",
        "Arrangement.End",
        "Arrangement.Center",
        "Arrangement.SpaceEvenly",
        "Arrangement.SpaceBetween",
        "Arrangement.SpaceAround",
      )
    assertThat(lookupStrings.subList(6, 12))
      .containsExactly(
        "Arrangement.Absolute.Left",
        "Arrangement.Absolute.Center",
        "Arrangement.Absolute.Right",
        "Arrangement.Absolute.SpaceBetween",
        "Arrangement.Absolute.SpaceEvenly",
        "Arrangement.Absolute.SpaceAround",
      )

    val startLookupElement =
      myFixture.lookupElements?.find { it.lookupString == "Arrangement.Absolute.Center" }!!

    val presentation = LookupElementPresentation()
    startLookupElement.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Arrangement.Horizontal")

    myFixture.lookup.currentItem = startLookupElement
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.foundation.layout.Arrangement
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(Arrangement.Absolute.Center)
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun horizontalArrangementCompletion_arrangementAlreadyCompleted() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(Arrangement.<caret>)
      }
      """
        .trimIndent()
    )

    myFixture.completeBasic()

    // Ordering: all Horizontal entries on `Arrangement` should come first, followed by those on
    // `Arrangement.Absolute`.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 6))
      .containsExactly(
        "Start",
        "End",
        "Center",
        "SpaceEvenly",
        "SpaceBetween",
        "SpaceAround",
      )
    assertThat(lookupStrings.subList(6, 12))
      .containsExactly(
        "Absolute.Left",
        "Absolute.Center",
        "Absolute.Right",
        "Absolute.SpaceBetween",
        "Absolute.SpaceEvenly",
        "Absolute.SpaceAround",
      )

    val startLookupElement =
      myFixture.lookupElements?.find { it.lookupString == "Absolute.Center" }!!

    val presentation = LookupElementPresentation()
    startLookupElement.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Arrangement.Horizontal")

    myFixture.lookup.currentItem = startLookupElement
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.foundation.layout.Arrangement
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(Arrangement.Absolute.Center)
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun horizontalArrangementCompletion_arrangementAbsoluteAlreadyCompleted() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(Arrangement.Absolute.<caret>)
      }
      """
        .trimIndent()
    )

    myFixture.completeBasic()

    // Ordering: all Horizontal entries on `Arrangement` should come first. No entries from
    // `Arrangement.Absolute` should be present.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 6))
      .containsExactly("Left", "Center", "Right", "SpaceBetween", "SpaceEvenly", "SpaceAround")

    assertThat(lookupStrings).doesNotContain("Start")
    assertThat(lookupStrings).doesNotContain("End")

    val startLookupElement = myFixture.lookupElements?.find { it.lookupString == "Center" }!!

    val presentation = LookupElementPresentation()
    startLookupElement.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Arrangement.Horizontal")

    myFixture.lookup.currentItem = startLookupElement
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.foundation.layout.Arrangement
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(Arrangement.Absolute.Center)
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun verticalArrangementCompletion() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(verticalArrangement = <caret>)
      }
      """
        .trimIndent()
    )
    myFixture.completeBasic()

    // Ordering: all Vertical entries on `Arrangement` should come at the top. There are no Vertical
    // entries on `Arrangement.Absolute`.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 6))
      .containsExactly(
        "Arrangement.Top",
        "Arrangement.Bottom",
        "Arrangement.Center",
        "Arrangement.SpaceEvenly",
        "Arrangement.SpaceBetween",
        "Arrangement.SpaceAround",
      )

    val topLookupElement = myFixture.lookupElements?.find { it.lookupString == "Arrangement.Top" }!!

    val presentation = LookupElementPresentation()
    topLookupElement.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Arrangement.Vertical")

    myFixture.lookup.currentItem = topLookupElement
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
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun horizontalOrVerticalArrangementCompletion() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(horizontalOrVerticalArrangement = <caret>)
      }
      """
        .trimIndent()
    )
    myFixture.completeBasic()

    // Ordering: all HorizontalOrVertical entries on `Arrangement` should come at the top. There are
    // no HorizontalOrVertical entries on
    // `Arrangement.Absolute`.
    val lookupStrings = myFixture.lookupElementStrings!!
    assertThat(lookupStrings.subList(0, 4))
      .containsExactly(
        "Arrangement.Center",
        "Arrangement.SpaceEvenly",
        "Arrangement.SpaceBetween",
        "Arrangement.SpaceAround",
      )

    val topLookupElement =
      myFixture.lookupElements?.find { it.lookupString == "Arrangement.SpaceEvenly" }!!

    val presentation = LookupElementPresentation()
    topLookupElement.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Arrangement.HorizontalOrVertical")

    myFixture.lookup.currentItem = topLookupElement
    myFixture.finishLookup('\n')

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.foundation.layout.Arrangement
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(horizontalOrVerticalArrangement = Arrangement.SpaceEvenly)
      }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun duplicateLookupEntriesFromOtherContributorsHandled() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.foundation.layout.Arrangement
      import androidx.compose.runtime.Composable

      @Composable
      fun HomeScreen() {
        RowWithArrangement(horizontalArrangement = A)
        RowWithArrangement(horizontalArrangement = Arrangement.A)
        RowWithArrangement(horizontalArrangement = Arrangement.Absolute.L)
      }
      """
        .trimIndent()
    )

    myFixture.moveCaret("(horizontalArrangement = A|)")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings!!.filter { it.startsWith("Arrangement") })
      .containsNoDuplicates()

    myFixture.moveCaret("(horizontalArrangement = Arrangement.A|)")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings!!).containsNoDuplicates()

    myFixture.moveCaret("(horizontalArrangement = Arrangement.Absolute.L|)")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings!!).containsNoDuplicates()
  }
}
