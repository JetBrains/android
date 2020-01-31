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
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.NavColors.COMPONENT_BACKGROUND
import com.android.tools.idea.naveditor.scene.regularFont
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

class DrawNestedGraphTest : NavTestCase() {
  fun testDrawNestedGraph() {
    val rect = Rectangle2D.Float(10f, 20f, 30f, 40f)
    val scale = 1.5f
    val frameColor = Color.RED
    val frameThickness = 1f
    val text = "text"
    val textColor = Color.BLUE

    val drawNestedNavigation = DrawNestedGraph(rect, scale, frameColor, frameThickness, text, textColor)

    val rectangle = Rectangle2D.Float(rect.x, rect.y, rect.width, rect.height)
    val arcSize = NAVIGATION_ARC_SIZE * scale
    val roundRectangle = RoundRectangle2D.Float(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arcSize, arcSize)
    val font = regularFont(scale, Font.BOLD)

    assertEquals(3, drawNestedNavigation.commands.size)
    assertDrawCommandsEqual(FillShape(roundRectangle, COMPONENT_BACKGROUND), drawNestedNavigation.commands[0])
    assertDrawCommandsEqual(DrawShape(roundRectangle, frameColor, BasicStroke(frameThickness)), drawNestedNavigation.commands[1])
    assertEquals(drawNestedNavigation.commands[2], DrawTruncatedText(2, text, rectangle, textColor, font, true))
  }
}
