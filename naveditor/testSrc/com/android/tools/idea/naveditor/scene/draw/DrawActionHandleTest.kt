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

import com.android.tools.idea.common.scene.LerpEllipse
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.HANDLE_STROKE
import java.awt.Color
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D

private val CENTER = Point2D.Float(100f, 150f)

private const val INITIAL_OUTER_RADIUS = 10f
private const val FINAL_OUTER_RADIUS = 20f
private const val INITIAL_INNER_RADIUS = 1f
private const val FINAL_INNER_RADIUS = 2f
private const val DURATION = 30

private val OUTER_CIRCLE = LerpEllipse(Ellipse2D.Float(90f, 140f, 20f, 20f),
                                       Ellipse2D.Float(80f, 130f, 40f, 40f),
                                       DURATION)

private val INNER_CIRCLE = LerpEllipse(Ellipse2D.Float(99f, 149f, 2f, 2f),
                                       Ellipse2D.Float(98f, 148f, 4f, 4f),
                                       DURATION)

private val OUTER_COLOR = Color.RED
private val INNER_COLOR = Color.BLUE

class DrawActionHandleTest : NavTestCase() {
  fun testDrawActionHandle() {
    val drawHandle = DrawActionHandle(CENTER,
                                      INITIAL_OUTER_RADIUS,
                                      FINAL_OUTER_RADIUS,
                                      INITIAL_INNER_RADIUS,
                                      FINAL_INNER_RADIUS,
                                      DURATION,
                                      OUTER_COLOR,
                                      INNER_COLOR)

    assertEquals(drawHandle.commands.size, 2)
    assertDrawCommandsEqual(FillShape(OUTER_CIRCLE, OUTER_COLOR), drawHandle.commands[0])
    assertDrawCommandsEqual(DrawShape(INNER_CIRCLE, INNER_COLOR, HANDLE_STROKE), drawHandle.commands[1])
  }
}
