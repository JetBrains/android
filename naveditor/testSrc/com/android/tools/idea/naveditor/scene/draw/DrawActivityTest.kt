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

import com.android.tools.idea.common.scene.draw.DrawFilledRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawRectangle
import com.android.tools.idea.common.scene.draw.DrawRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

private val FRAME_RECT = Rectangle2D.Float(10f, 20f, 100f, 300f)
private val FRAME_COLOR = Color.RED
private const val FRAME_THICKNESS = 1f
private const val SCALE = 1.5f
private val BORDER_RECT = RoundRectangle2D.Float(10f, 20f, 100f, 300f, 18f, 18f)
private val BACKGROUND_COLOR = JBColor(0xfafafa, 0x515658)
private val IMAGE_RECT = Rectangle2D.Float(22f, 32f, 76f, 249f)
private val IMAGE_BORDER_COLOR = JBColor(0xa7a7a7, 0x2d2f31)
private const val IMAGE_BORDER_WIDTH = 1f
private val TEXT_RECT = Rectangle2D.Float(10f, 281f, 100f, 39f)
private val TEXT_COLOR = Color.BLUE
private val FONT = Font("Default", Font.BOLD, 18)

class DrawActivityTest : NavTestCase() {
  fun testDrawActivity() {
    val drawFragment = DrawActivity(FRAME_RECT, IMAGE_RECT, SCALE, FRAME_COLOR, FRAME_THICKNESS, TEXT_COLOR)

    assertEquals(5, drawFragment.commands.size)
    assertEquals(DrawFilledRoundRectangle(0, BORDER_RECT, BACKGROUND_COLOR), drawFragment.commands[0])
    assertEquals(DrawRoundRectangle(1, BORDER_RECT, FRAME_COLOR, FRAME_THICKNESS), drawFragment.commands[1])
    assertDrawCommandsEqual(DrawPlaceholder(IMAGE_RECT), drawFragment.commands[2])
    assertEquals(DrawRectangle(3, IMAGE_RECT, IMAGE_BORDER_COLOR, IMAGE_BORDER_WIDTH), drawFragment.commands[3])
    assertEquals(DrawTruncatedText(4, "Activity", TEXT_RECT, TEXT_COLOR, FONT, true), drawFragment.commands[4])
  }

  fun testDrawActivityWithPreview() {
    val image = RefinableImage()
    val drawFragment = DrawActivity(FRAME_RECT, IMAGE_RECT, SCALE, FRAME_COLOR, FRAME_THICKNESS, TEXT_COLOR, image)

    assertEquals(5, drawFragment.commands.size)
    assertEquals(DrawFilledRoundRectangle(0, BORDER_RECT, BACKGROUND_COLOR), drawFragment.commands[0])
    assertEquals(DrawRoundRectangle(1, BORDER_RECT, FRAME_COLOR, FRAME_THICKNESS), drawFragment.commands[1])
    assertDrawCommandsEqual(DrawNavScreen(IMAGE_RECT, image), drawFragment.commands[2])
    assertEquals(DrawRectangle(3, IMAGE_RECT, IMAGE_BORDER_COLOR, IMAGE_BORDER_WIDTH), drawFragment.commands[3])
    assertEquals(DrawTruncatedText(4, "Activity", TEXT_RECT, TEXT_COLOR, FONT, true), drawFragment.commands[4])
  }
}
