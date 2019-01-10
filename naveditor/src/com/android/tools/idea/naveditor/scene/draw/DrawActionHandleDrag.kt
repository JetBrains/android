/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.common.scene.LerpFloat
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand.TARGET_LEVEL
import com.android.tools.idea.common.scene.draw.DrawFilledCircle
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.point2DToString
import com.android.tools.idea.common.scene.draw.stringToPoint2D
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import java.awt.geom.Point2D

data class DrawActionHandleDrag(@SwingCoordinate private val center: Point2D.Float,
                                @SwingCoordinate private val initialOuterRadius: Float,
                                @SwingCoordinate private val finalOuterRadius: Float,
                                @SwingCoordinate private val innerRadius: Float,
                                private val duration: Int) : CompositeDrawCommand(TARGET_LEVEL) {
  private constructor(tokens: Array<String>) : this(stringToPoint2D(tokens[0]), tokens[1].toFloat(), tokens[2].toFloat(),
                                                    tokens[3].toFloat(), tokens[4].toInt())

  constructor(s: String) : this(parse(s, 5))

  override fun serialize() = buildString(javaClass.simpleName, point2DToString(center), initialOuterRadius, finalOuterRadius,
                                         innerRadius, duration)

  override fun buildCommands(): List<DrawCommand> {
    val outerCircle = DrawFilledCircle(0, center, primaryPanelBackground, LerpFloat(initialOuterRadius, finalOuterRadius, duration))
    val innerCircle = DrawFilledCircle(1, center, SELECTED, innerRadius)
    val lineToMouse = DrawLineToMouse(center)
    return listOf(outerCircle, innerCircle, lineToMouse)
  }
}