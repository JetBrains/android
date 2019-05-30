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

import com.android.tools.idea.common.scene.draw.ArrowDirection
import com.android.tools.idea.common.scene.draw.DrawArrow
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D

private val ARROW_LINE = Line2D.Float(50f, 106f, 340f, 106f)
private val ARROW_RECT = Rectangle2D.Float(340f, 100f, 10f, 12f)
private val ICON_RECT = Rectangle2D.Float(52f, 87f, 14f, 14f)
private val COLOR = Color.BLUE
private val STROKE = BasicStroke(JBUI.scale(3f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)

class DrawHorizontalActionTest : NavTestCase() {
  fun testDrawHorizontalAction() {
    val rectangle = Rectangle2D.Float(50f, 100f, 300f, 12f)

    var drawAction = DrawHorizontalAction(rectangle, COLOR, false)

    assertEquals(2, drawAction.commands.size)
    assertDrawLinesEqual(DrawShape(ARROW_LINE, COLOR, STROKE), drawAction.commands[0])
    assertEquals(drawAction.commands[1], DrawArrow(1, ArrowDirection.RIGHT, ARROW_RECT, COLOR))

    drawAction = DrawHorizontalAction(rectangle, COLOR, true)

    assertEquals(3, drawAction.commands.size)
    assertDrawLinesEqual(DrawShape(ARROW_LINE, COLOR, STROKE), drawAction.commands[0])
    assertEquals(drawAction.commands[1], DrawArrow(1, ArrowDirection.RIGHT, ARROW_RECT, COLOR))
    assertEquals(drawAction.commands[2], DrawIcon(ICON_RECT, DrawIcon.IconType.POP_ACTION, COLOR))

  }
}
