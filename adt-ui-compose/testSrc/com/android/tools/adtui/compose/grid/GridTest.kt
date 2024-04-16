/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.compose.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.google.common.truth.Truth.assertThat
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class GridTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun grid2x2() {
    composeTestRule.setContent {
      Grid(Modifier.testTag("G")) {
        GridRow {
          Box(Modifier.size(50.dp).testTag("TL"))
          Box(Modifier.size(100.dp).testTag("TR"))
        }
        GridRow {
          Box(Modifier.size(100.dp).testTag("BL"))
          Box(Modifier.size(50.dp).testTag("BR"))
        }
      }
    }

    composeTestRule.onNodeWithTag("G").assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(200.dp)
    composeTestRule.onNodeWithTag("TL").assertPositionInRootIsEqualTo(0.dp, 0.dp)
    composeTestRule.onNodeWithTag("TR").assertPositionInRootIsEqualTo(100.dp, 0.dp)
    composeTestRule.onNodeWithTag("BL").assertPositionInRootIsEqualTo(0.dp, 100.dp)
    composeTestRule.onNodeWithTag("BR").assertPositionInRootIsEqualTo(100.dp, 100.dp)
  }

  @Test
  fun grid2x2Overconstrained() {
    composeTestRule.setContent {
      Grid(Modifier.testTag("G").size(60.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        GridRow {
          Box(Modifier.size(50.dp).testTag("TL"))
          Box(Modifier.size(50.dp).testTag("TR"))
        }
        GridRow {
          Box(Modifier.size(50.dp).testTag("BL"))
          Box(Modifier.size(50.dp).testTag("BR"))
        }
      }
    }

    composeTestRule.onNodeWithTag("G").assertWidthIsEqualTo(60.dp).assertHeightIsEqualTo(60.dp)

    composeTestRule.onNodeWithTag("TL").assertPositionInRootIsEqualTo(0.dp, 0.dp)
    composeTestRule.onNodeWithTag("TL").assertWidthIsEqualTo(30.dp).assertHeightIsEqualTo(50.dp)

    composeTestRule.onNodeWithTag("TR").assertPositionInRootIsEqualTo(30.dp, 0.dp)
    composeTestRule.onNodeWithTag("TR").assertWidthIsEqualTo(30.dp).assertHeightIsEqualTo(50.dp)

    composeTestRule.onNodeWithTag("BL").assertPositionInRootIsEqualTo(0.dp, 55.dp)
    composeTestRule.onNodeWithTag("BL").assertWidthIsEqualTo(30.dp).assertHeightIsEqualTo(5.dp)

    composeTestRule.onNodeWithTag("BR").assertPositionInRootIsEqualTo(30.dp, 55.dp)
    composeTestRule.onNodeWithTag("BR").assertWidthIsEqualTo(30.dp).assertHeightIsEqualTo(5.dp)
  }

  @Test
  fun grid2x2Incomplete() {
    composeTestRule.setContent {
      Grid(Modifier.testTag("G")) {
        GridRow {
          Box(Modifier.size(50.dp).testTag("TL"))
          Box(Modifier.size(100.dp).testTag("TR"))
        }
        GridRow { Box(Modifier.size(100.dp).testTag("BL")) }
      }
    }

    composeTestRule.onNodeWithTag("G").assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(200.dp)
    composeTestRule.onNodeWithTag("TL").assertPositionInRootIsEqualTo(0.dp, 0.dp)
    composeTestRule.onNodeWithTag("TR").assertPositionInRootIsEqualTo(100.dp, 0.dp)
    composeTestRule.onNodeWithTag("BL").assertPositionInRootIsEqualTo(0.dp, 100.dp)
  }

  @Test
  fun grid2x2WithSpacing() {
    composeTestRule.setContent {
      Grid(
        Modifier.testTag("G"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        GridRow {
          Box(Modifier.size(50.dp).testTag("TL"))
          Box(Modifier.size(100.dp).testTag("TR"))
        }
        GridRow {
          Box(Modifier.size(100.dp).testTag("BL"))
          Box(Modifier.size(50.dp).testTag("BR"))
        }
      }
    }

    composeTestRule.onNodeWithTag("G").assertWidthIsEqualTo(210.dp).assertHeightIsEqualTo(205.dp)
    composeTestRule.onNodeWithTag("TL").assertPositionInRootIsEqualTo(0.dp, 0.dp)
    composeTestRule.onNodeWithTag("TR").assertPositionInRootIsEqualTo(110.dp, 0.dp)
    composeTestRule.onNodeWithTag("BL").assertPositionInRootIsEqualTo(0.dp, 105.dp)
    composeTestRule.onNodeWithTag("BR").assertPositionInRootIsEqualTo(110.dp, 105.dp)
  }

  @Test
  fun gridWithArrangement() {
    composeTestRule.setContent {
      Grid(
        Modifier.testTag("G").size(500.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.SpaceEvenly,
      ) {
        GridRow {
          Box(Modifier.size(50.dp).testTag("TL"))
          Box(Modifier.size(100.dp).testTag("TR"))
        }
        GridRow {
          Box(Modifier.size(100.dp).testTag("BL"))
          Box(Modifier.size(50.dp).testTag("BR"))
        }
      }
    }

    composeTestRule.onNodeWithTag("TL").assertPositionInRootIsEqualTo(100.dp, 100.dp)
    composeTestRule.onNodeWithTag("TR").assertPositionInRootIsEqualTo(300.dp, 100.dp)
    composeTestRule.onNodeWithTag("BL").assertPositionInRootIsEqualTo(100.dp, 300.dp)
    composeTestRule.onNodeWithTag("BR").assertPositionInRootIsEqualTo(300.dp, 300.dp)
  }

  @Test
  fun grid2x2WithDefaultCenterAlignment() {
    composeTestRule.setContent {
      Grid(
        Modifier.testTag("G"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        GridRow {
          Box(Modifier.size(50.dp).testTag("TL"))
          Box(Modifier.size(100.dp).testTag("TR"))
        }
        GridRow {
          Box(Modifier.size(100.dp).testTag("BL"))
          Box(Modifier.size(50.dp).testTag("BR"))
        }
      }
    }

    composeTestRule.onNodeWithTag("G").assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(200.dp)
    composeTestRule.onNodeWithTag("TL").assertPositionInRootIsEqualTo(25.dp, 25.dp)
    composeTestRule.onNodeWithTag("TR").assertPositionInRootIsEqualTo(100.dp, 0.dp)
    composeTestRule.onNodeWithTag("BL").assertPositionInRootIsEqualTo(0.dp, 100.dp)
    composeTestRule.onNodeWithTag("BR").assertPositionInRootIsEqualTo(125.dp, 125.dp)
  }

  @Test
  fun grid2x2WithCellAlignmentModifiers() {
    composeTestRule.setContent {
      Grid(
        Modifier.testTag("G"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        GridRow {
          Box(Modifier.size(50.dp).testTag("TL").align(Alignment.Bottom).align(Alignment.End))
          Box(Modifier.size(100.dp).testTag("TR").align(Alignment.Bottom).align(Alignment.Start))
        }
        GridRow {
          Box(Modifier.size(100.dp).testTag("BL").align(Alignment.Top).align(Alignment.End))
          Box(Modifier.size(50.dp).testTag("BR").align(Alignment.Top).align(Alignment.Start))
        }
      }
    }

    composeTestRule.onNodeWithTag("G").assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(200.dp)
    composeTestRule.onNodeWithTag("TL").assertPositionInRootIsEqualTo(50.dp, 50.dp)
    composeTestRule.onNodeWithTag("TR").assertPositionInRootIsEqualTo(100.dp, 0.dp)
    composeTestRule.onNodeWithTag("BL").assertPositionInRootIsEqualTo(0.dp, 100.dp)
    composeTestRule.onNodeWithTag("BR").assertPositionInRootIsEqualTo(100.dp, 100.dp)
  }

  @OptIn(ExperimentalLayoutApi::class)
  @Test
  fun flex_exactFit() {
    composeTestRule.setContent {
      Grid(Modifier.testTag("G").size(width = 300.dp, height = 500.dp)) {
        GridRow {
          FlowRow { repeat(10) { Box(Modifier.size(10.dp).testTag("1-$it")) } }
          FlowRow { repeat(20) { Box(Modifier.size(10.dp).testTag("2-$it")) } }
        }
      }
    }

    for (i in 0 until 10) {
      composeTestRule.onNodeWithTag("1-$i").assertPositionInRootIsEqualTo((i * 10).dp, 0.dp)
    }
    for (i in 0 until 20) {
      composeTestRule.onNodeWithTag("2-$i").assertPositionInRootIsEqualTo((100 + i * 10).dp, 0.dp)
    }
  }

  @OptIn(ExperimentalLayoutApi::class)
  @Test
  fun flex_wrapOneRow() {
    composeTestRule.setContent {
      Grid(Modifier.testTag("G").size(width = 160.dp, height = 500.dp)) {
        GridRow {
          FlowRow { repeat(10) { Box(Modifier.size(10.dp).testTag("1-$it")) } }
          FlowRow { repeat(20) { Box(Modifier.size(10.dp).testTag("2-$it")) } }
        }
      }
    }

    // In theory, we could fit all of the elements in two rows with a width of 150, but our space
    // allocation heuristic doesn't permit it; it doesn't know the details of when the child
    // components will wrap, only their min and max intrinsic widths.

    for (i in 0 until 5) {
      composeTestRule.onNodeWithTag("1-$i").assertPositionInRootIsEqualTo((i * 10).dp, 0.dp)
    }
    for (i in 5 until 10) {
      composeTestRule.onNodeWithTag("1-$i").assertPositionInRootIsEqualTo(((i - 5) * 10).dp, 10.dp)
    }
    for (i in 0 until 10) {
      composeTestRule.onNodeWithTag("2-$i").assertPositionInRootIsEqualTo((55 + i * 10).dp, 0.dp)
    }
    for (i in 10 until 20) {
      composeTestRule
        .onNodeWithTag("2-$i")
        .assertPositionInRootIsEqualTo((55 + (i - 10) * 10).dp, 10.dp)
    }
  }

  @Test
  fun gridInsideScrollContainer() {
    composeTestRule.setContent {
      VerticallyScrollableContainer(Modifier.size(200.dp, 200.dp)) {
        Grid {
          for (i in 1..4) {
            GridRow { Box(Modifier.size(100.dp).testTag("$i")) }
          }
        }
      }
    }

    composeTestRule.onNodeWithTag("1").assertIsDisplayed().assertTopPositionInRootIsEqualTo(0.dp)
    composeTestRule.onNodeWithTag("2").assertIsDisplayed().assertTopPositionInRootIsEqualTo(100.dp)
    composeTestRule
      .onNodeWithTag("3")
      .assertIsNotDisplayed()
      .assertTopPositionInRootIsEqualTo(200.dp)
    composeTestRule
      .onNodeWithTag("4")
      .assertIsNotDisplayed()
      .assertTopPositionInRootIsEqualTo(300.dp)
  }

  @Composable
  fun FakeText(baseline: Int, modifier: Modifier) {
    Layout(modifier) { measurables, constraints ->
      layout(
        constraints.minWidth,
        constraints.minHeight,
        alignmentLines = mapOf(FirstBaseline to baseline, LastBaseline to baseline),
      ) {}
    }
  }

  @Test
  fun alignmentLines() {
    composeTestRule.setContent {
      Grid(Modifier.testTag("G")) {
        GridRow {
          FakeText(20, Modifier.size(50.dp).testTag("TL").alignByBaseline().align(Alignment.End))
          FakeText(50, Modifier.size(100.dp).testTag("TR").alignByBaseline().align(Alignment.Start))
        }
        GridRow {
          FakeText(20, Modifier.size(100.dp).testTag("BL").alignByBaseline().align(Alignment.End))
          FakeText(50, Modifier.size(50.dp).testTag("BR").alignByBaseline().align(Alignment.Start))
        }
      }
    }

    composeTestRule.onNodeWithTag("G").assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(230.dp)
    composeTestRule.onNodeWithTag("TL").assertPositionInRootIsEqualTo(50.dp, 30.dp)
    composeTestRule.onNodeWithTag("TR").assertPositionInRootIsEqualTo(100.dp, 0.dp)
    composeTestRule.onNodeWithTag("BL").assertPositionInRootIsEqualTo(0.dp, 130.dp)
    composeTestRule.onNodeWithTag("BR").assertPositionInRootIsEqualTo(100.dp, 100.dp)
  }

  /*
   * Currently, the maxIntrinsicHeight computation on T1 does not take into account T1's constrained width; it assumes it will have the
   * width passed in available to it, even though it has a width() modifier of its own that constrains its width more severely. This is
   * arguably a bug in SizeModifier.
   */
  @Ignore("b/377300967")
  @Test
  fun textWrapping() {
    composeTestRule.setContent {
      Grid(Modifier.width(100.dp)) {
        GridRow {
          Text(
            "A long text string that will need to wrap across several lines",
            Modifier.width(50.dp).testTag("T1").align(Alignment.End),
          )
        }
        GridRow {
          Text(
            "A long text string that will need to wrap across several lines",
            Modifier.testTag("T2"),
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("T1").assertWidthIsEqualTo(50.dp)
    composeTestRule.onNodeWithTag("T2").assertWidthIsEqualTo(100.dp)

    // Actual height depends on the font, but we should be wrapping T1 more than T2,
    // and T2 should be positioned at the end of T1.
    val t1Height = composeTestRule.onNodeWithTag("T1").fetchSemanticsNode().size.height
    val t2Height = composeTestRule.onNodeWithTag("T2").fetchSemanticsNode().size.height
    assertThat(t1Height).isGreaterThan(t2Height)

    with(composeTestRule.density) {
      composeTestRule.onNodeWithTag("T1").assertPositionInRootIsEqualTo(50.dp, 0.dp)
      composeTestRule.onNodeWithTag("T2").assertPositionInRootIsEqualTo(0.dp, t1Height.toDp())
    }
  }
}
