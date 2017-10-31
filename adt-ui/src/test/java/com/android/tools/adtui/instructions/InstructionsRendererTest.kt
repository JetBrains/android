/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.instructions

import com.google.common.truth.Truth
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.util.*

class InstructionsRendererTest {

  @Test
  fun testRowHeightAndTotalSize() {
    // Fake a list of instructions.
    // Row height should be 100, and total size should be {150, 210}
    val instructions = Arrays.asList(
        FakeRenderInstructions(100, 100),
        FakeRenderInstructions(50, 90),
        NewRowInstruction(10),
        FakeRenderInstructions(50, 70)
    )

    val context = InstructionsRenderer(instructions, InstructionsRenderer.HorizontalAlignment.LEFT)

    Truth.assertThat(context.rowHeight).isEqualTo(100)
    Truth.assertThat(context.renderSize).isEqualTo(Dimension(150, 210))
  }

  @Test
  fun testLeftAlignStartX() {
    // Fake a list of instructions.
    val instructions = Arrays.asList(
        FakeRenderInstructions(100, 100),
        FakeRenderInstructions(50, 90),
        NewRowInstruction(10),
        FakeRenderInstructions(50, 70)
    )

    val context = InstructionsRenderer(instructions, InstructionsRenderer.HorizontalAlignment.LEFT)

    // Start X for the first row should be zero.
    Truth.assertThat(context.getStartX(0)).isEqualTo(0)
    // Start X for an invalid row should be zero.
    Truth.assertThat(context.getStartX(50)).isEqualTo(0)
    // Start X for the second row should be zero.
    Truth.assertThat(context.getStartX(110)).isEqualTo(0)
  }

  @Test
  fun testCenterAlignStartX() {
    // Fake a list of instructions.
    val instructions = Arrays.asList(
        FakeRenderInstructions(100, 100),
        FakeRenderInstructions(50, 90),
        NewRowInstruction(10),
        FakeRenderInstructions(50, 70)
    )

    val context = InstructionsRenderer(instructions, InstructionsRenderer.HorizontalAlignment.CENTER)

    // Start X for an invalid row should be zero.
    Truth.assertThat(context.getStartX(50)).isEqualTo(0)
    // Start X for the first (longest) row should be zero.
    Truth.assertThat(context.getStartX(0)).isEqualTo(0)
    // Start X for the second row should be centered (75 - 25)
    Truth.assertThat(context.getStartX(110)).isEqualTo(50)
  }

  @Test
  fun testRightAlignStartX() {
    // Fake a list of instructions.
    val instructions = Arrays.asList(
        FakeRenderInstructions(100, 100),
        FakeRenderInstructions(50, 90),
        NewRowInstruction(10),
        FakeRenderInstructions(50, 70)
    )

    val context = InstructionsRenderer(instructions, InstructionsRenderer.HorizontalAlignment.RIGHT)

    // Start X for an invalid row should be zero.
    Truth.assertThat(context.getStartX(50)).isEqualTo(0)
    // Start X for the first (longest) row should be zero.
    Truth.assertThat(context.getStartX(0)).isEqualTo(0)
    // Start X for the second row should be right-aligned (150 - 50)
    Truth.assertThat(context.getStartX(110)).isEqualTo(100)
  }

  private class FakeRenderInstructions(private val myWidth: Int, private val myHeight: Int) : RenderInstruction() {

    override fun getSize(): Dimension {
      return Dimension(myWidth, myHeight)
    }

    override fun moveCursor(renderer: InstructionsRenderer, cursor: Point) {
      cursor.x += myWidth
    }
  }
}