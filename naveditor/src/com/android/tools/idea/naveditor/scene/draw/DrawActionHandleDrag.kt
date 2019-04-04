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
import com.android.tools.idea.common.scene.LerpFloat
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawFilledCircle
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.point2DToString
import com.android.tools.idea.common.scene.draw.stringToPoint2D
import com.android.tools.idea.naveditor.scene.NavColors.BACKGROUND
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import java.awt.geom.Point2D

data class DrawActionHandleDrag(private val level: Int,
                                @SwingCoordinate private val center: Point2D.Float,
                                @SwingCoordinate private val initialOuterRadius: Float,
                                @SwingCoordinate private val finalOuterRadius: Float,
                                @SwingCoordinate private val innerRadius: Float,
                                private val duration: Int) : CompositeDrawCommand() {
  private constructor(sp: Array<String>) : this(sp[0].toInt(), stringToPoint2D(sp[1]), sp[2].toFloat(), sp[3].toFloat(), sp[4].toFloat(),
                                                sp[5].toInt())

  constructor(s: String) : this(parse(s, 6))

  override fun getLevel() = level

  override fun serialize() = buildString(javaClass.simpleName, level, point2DToString(center), initialOuterRadius, finalOuterRadius,
                                         innerRadius, duration)

  override fun buildCommands(): List<DrawCommand> {
    val outerCircle = DrawFilledCircle(0, center, BACKGROUND, LerpFloat(initialOuterRadius, finalOuterRadius, duration))
    val innerCircle = DrawFilledCircle(1, center, SELECTED, innerRadius)
    val lineToMouse = DrawLineToMouse(2, center)
    return listOf(outerCircle, innerCircle, lineToMouse)
  }
}