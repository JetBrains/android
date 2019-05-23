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
import com.android.tools.idea.common.scene.draw.DrawLine
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.ACTION_STROKE
import java.awt.Color
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

class DrawHorizontalActionTest : NavTestCase() {
  fun testDrawActionHandle() {
    val rectangle = Rectangle2D.Float(50f, 100f, 300f, 12f)
    val color = Color.BLUE

    var drawAction = DrawHorizontalAction(rectangle, color, false)

    assertEquals(drawAction.commands[0],
                 DrawLine(0, Point2D.Float(50f, 106f), Point2D.Float(340f, 106f), color, ACTION_STROKE))
    assertEquals(drawAction.commands[1],
                 DrawArrow(1, ArrowDirection.RIGHT, Rectangle2D.Float(340f, 100f, 10f, 12f), color))

    drawAction = DrawHorizontalAction(rectangle, color, true)

    assertEquals(drawAction.commands[0],
                 DrawLine(0, Point2D.Float(50f, 106f), Point2D.Float(340f, 106f), color, ACTION_STROKE))
    assertEquals(drawAction.commands[1],
                 DrawArrow(1, ArrowDirection.RIGHT, Rectangle2D.Float(340f, 100f, 10f, 12f), color))
    assertEquals(drawAction.commands[2],
                 DrawIcon(Rectangle2D.Float(52f, 87f, 14f, 14f), DrawIcon.IconType.POP_ACTION, color))

  }
}
