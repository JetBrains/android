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

import com.android.tools.idea.common.scene.LerpFloat
import com.android.tools.idea.common.scene.draw.DrawCircle
import com.android.tools.idea.common.scene.draw.DrawFilledCircle
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.HANDLE_STROKE
import java.awt.Color
import java.awt.geom.Point2D

private const val CENTER_X = 100f
private const val CENTER_Y = 150f

private const val INITIAL_OUTER_RADIUS = 10f
private const val FINAL_OUTER_RADIUS = 20f
private const val INITIAL_INNER_RADIUS = 1f
private const val FINAL_INNER_RADIUS = 2f
private const val DURATION = 30

private val OUTER_COLOR = Color.RED
private val INNER_COLOR = Color.BLUE

class DrawActionHandleTest : NavTestCase() {
  fun testDrawActionHandle() {
    val center = Point2D.Float(CENTER_X, CENTER_Y)
    val drawHandle = DrawActionHandle(center,
                                      INITIAL_OUTER_RADIUS,
                                      FINAL_OUTER_RADIUS,
                                      INITIAL_INNER_RADIUS,
                                      FINAL_INNER_RADIUS,
                                      DURATION,
                                      OUTER_COLOR,
                                      INNER_COLOR)

    assertEquals(drawHandle.commands[0],
                 DrawFilledCircle(0, center, OUTER_COLOR, LerpFloat(INITIAL_OUTER_RADIUS, FINAL_OUTER_RADIUS, DURATION)))
    assertEquals(drawHandle.commands[1],
                 DrawCircle(1, center, INNER_COLOR, HANDLE_STROKE, LerpFloat(INITIAL_INNER_RADIUS, FINAL_INNER_RADIUS, DURATION)))
  }
}
