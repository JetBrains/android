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

import com.android.tools.idea.common.scene.draw.DrawFilledRectangle
import com.android.tools.idea.common.scene.draw.DrawLine
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BACKGROUND
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BORDER
import com.android.tools.idea.naveditor.scene.decorator.REGULAR_FRAME_THICKNESS
import java.awt.BasicStroke
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

private const val X = 100f
private const val Y = 200f
private const val WIDTH = 80f
private const val HEIGHT = 120f

class DrawPlaceholderTest : NavTestCase() {
  fun testDrawPlaceholder() {
    val rectangle = Rectangle2D.Float(X, Y, WIDTH, HEIGHT)
    val drawPlaceholder = DrawPlaceholder(rectangle)
    val stroke = BasicStroke(REGULAR_FRAME_THICKNESS)

    assertContainsOrdered(drawPlaceholder.commands.toList(),
                          DrawFilledRectangle(0, Rectangle2D.Float(X, Y, WIDTH, HEIGHT), PLACEHOLDER_BACKGROUND),
                          DrawLine(1, Point2D.Float(X, Y), Point2D.Float(X + WIDTH, Y + HEIGHT),
                                   PLACEHOLDER_BORDER, stroke),
                          DrawLine(2, Point2D.Float(X, Y + HEIGHT), Point2D.Float(X + WIDTH, Y),
                                   PLACEHOLDER_BORDER, stroke))
  }
}
