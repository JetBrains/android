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

import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.common.scene.LerpFloat
import com.android.tools.idea.common.scene.draw.DrawFilledCircle
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import java.awt.geom.Point2D

private const val CENTER_X = 100f
private const val CENTER_Y = 150f

private const val INITIAL_OUTER_RADIUS = 10f
private const val FINAL_OUTER_RADIUS = 20f
private const val INNER_RADIUS = 1f
private const val DURATION = 30

class DrawActionHandleDragTest : NavTestCase() {
  fun testDrawActionHandleDrag() {
    val center = Point2D.Float(CENTER_X, CENTER_Y)
    val drawHandle = DrawActionHandleDrag(0, center, INITIAL_OUTER_RADIUS, FINAL_OUTER_RADIUS, INNER_RADIUS, DURATION)

    assertEquals(drawHandle.commands[0],
                 DrawFilledCircle(0, center, primaryPanelBackground, LerpFloat(INITIAL_OUTER_RADIUS, FINAL_OUTER_RADIUS, DURATION)))
    assertEquals(drawHandle.commands[1],
                 DrawFilledCircle(1, center, SELECTED, LerpFloat(INNER_RADIUS, INNER_RADIUS, 0)))
    assertEquals(drawHandle.commands[2],
                 DrawLineToMouse(2, center))
  }
}
