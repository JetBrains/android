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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

private val RECTANGLE = Rectangle2D.Float(10f, 20f, 30f, 40f)
private const val SCALE = 1.5f
private val HIGHLIGHT_COLOR = Color.RED

private val IMAGE_RECT = Rectangle2D.Float(11f, 21f, 28f, 38f)
private val HIGHLIGHT_RECT = RoundRectangle2D.Float(4f, 14f, 42f, 52f, 6f, 6f)
private val REGULAR_FRAME_STROKE = BasicStroke(1f)
private val HIGHLIGHTED_FRAME_STROKE = BasicStroke(2f)
private val FRAME_COLOR = JBColor(0xa7a7a7, 0x2d2f31)

class DrawFragmentTest : NavTestCase() {
  fun testDrawFragment() {
    val drawFragment = DrawFragment(RECTANGLE, SCALE, null)

    assertEquals(drawFragment.commands.size, 2)
    assertDrawCommandsEqual(DrawShape(RECTANGLE, FRAME_COLOR, REGULAR_FRAME_STROKE), drawFragment.commands[0])
    assertDrawCommandsEqual(DrawPlaceholder(IMAGE_RECT), drawFragment.commands[1])
  }

  fun testDrawHighlightedFragment() {
    val drawFragment = DrawFragment(RECTANGLE, SCALE, HIGHLIGHT_COLOR)

    assertEquals(drawFragment.commands.size, 3)
    assertDrawCommandsEqual(DrawShape(RECTANGLE, FRAME_COLOR, REGULAR_FRAME_STROKE), drawFragment.commands[0])
    assertDrawCommandsEqual(DrawPlaceholder(IMAGE_RECT), drawFragment.commands[1])
    assertDrawCommandsEqual(DrawShape(HIGHLIGHT_RECT, HIGHLIGHT_COLOR, HIGHLIGHTED_FRAME_STROKE), drawFragment.commands[2])
  }

  fun testDrawFragmentWithPreview() {
    val image = RefinableImage()
    val drawFragment = DrawFragment(RECTANGLE, SCALE, null, image)

    assertEquals(drawFragment.commands.size, 2)
    assertDrawCommandsEqual(DrawShape(RECTANGLE, FRAME_COLOR, REGULAR_FRAME_STROKE), drawFragment.commands[0])
    assertDrawCommandsEqual(DrawNavScreen(IMAGE_RECT, image), drawFragment.commands[1])
  }
}
