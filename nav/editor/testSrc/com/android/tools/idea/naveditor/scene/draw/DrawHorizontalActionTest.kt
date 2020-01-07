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

import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.SwingStroke
import com.android.tools.adtui.common.scaledSwingLength
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.naveditor.NavTestCase
import java.awt.BasicStroke
import java.awt.Color
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

private val ARROW_LINE = Line2D.Float(50f, 106f, 340f, 106f)
private val ICON_RECT = SwingRectangle(Rectangle2D.Float(52f, 87f, 14f, 14f))
private val SCALE = Scale(1.0)
private val COLOR = Color.BLUE
private val STROKE = SwingStroke(scaledSwingLength(3f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)

class DrawHorizontalActionTest : NavTestCase() {
  fun testDrawHorizontalAction() {
    val rectangle = SwingRectangle(Rectangle2D.Float(50f, 100f, 300f, 12f))

    val arrowPath = Path2D.Float()
    arrowPath.moveTo(340f, 100f)
    arrowPath.lineTo(350f, 106f)
    arrowPath.lineTo(340f, 112f)
    arrowPath.closePath()

    var drawAction = DrawHorizontalAction(rectangle, SCALE, COLOR, false)

    assertEquals(2, drawAction.commands.size)
    assertDrawLinesEqual(DrawShape(ARROW_LINE, COLOR, STROKE), drawAction.commands[0])
    assertFillPathEqual(FillShape(arrowPath, COLOR), drawAction.commands[1])

    drawAction = DrawHorizontalAction(rectangle, SCALE, COLOR, true)

    assertEquals(3, drawAction.commands.size)
    assertDrawLinesEqual(DrawShape(ARROW_LINE, COLOR, STROKE), drawAction.commands[0])
    assertFillPathEqual(FillShape(arrowPath, COLOR), drawAction.commands[1])
    assertEquals(drawAction.commands[2], DrawIcon(ICON_RECT, DrawIcon.IconType.POP_ACTION, COLOR))
  }
}
