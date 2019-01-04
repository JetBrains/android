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

import com.android.tools.idea.common.scene.LerpFloat
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCircle
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawFilledCircle
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.point2DToString
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.common.scene.draw.stringToPoint2D
import com.android.tools.idea.naveditor.scene.HANDLE_STROKE
import java.awt.Color
import java.awt.geom.Point2D

data class DrawActionHandle(private val level: Int,
                            private val center: Point2D.Float,
                            private val initialOuterRadius: Float,
                            private val finalOuterRadius: Float,
                            private val initialInnerRadius: Float,
                            private val finalInnerRadius: Float,
                            private val duration: Int,
                            private val outerColor: Color,
                            private val innerColor: Color
) : CompositeDrawCommand() {
  private constructor(tokens: Array<String>)
    : this(tokens[0].toInt(),
           stringToPoint2D(tokens[1]),
           tokens[2].toFloat(),
           tokens[3].toFloat(),
           tokens[4].toFloat(),
           tokens[5].toFloat(),
           tokens[6].toInt(),
           stringToColor(tokens[7]),
           stringToColor(tokens[8]))

  constructor(serialized: String) : this(parse(serialized, 9))

  override fun getLevel(): Int = level

  override fun serialize(): String = buildString(javaClass.simpleName,
                                                 level,
                                                 point2DToString(center),
                                                 initialOuterRadius,
                                                 finalOuterRadius,
                                                 initialInnerRadius,
                                                 finalInnerRadius,
                                                 duration,
                                                 colorToString(outerColor),
                                                 colorToString(innerColor))

  override fun buildCommands(): List<DrawCommand> {
    val outerCircle = DrawFilledCircle(0, center, outerColor, LerpFloat(initialOuterRadius, finalOuterRadius, duration))
    val innerCircle = DrawCircle(1, center, innerColor, HANDLE_STROKE, LerpFloat(initialInnerRadius, finalInnerRadius, duration))
    return listOf(outerCircle, innerCircle)
  }
}