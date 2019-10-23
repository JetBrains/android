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

import com.google.common.annotations.VisibleForTesting
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommandBase
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.point2DToString
import com.android.tools.idea.common.scene.draw.stringToPoint2D
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Stroke
import java.awt.geom.Line2D
import java.awt.geom.Point2D

val LINE_TO_MOUSE_STROKE: Stroke = BasicStroke(JBUI.scale(3.0f))

data class DrawLineToMouse(private val level: Int,
                           @SwingCoordinate private val center: Point2D.Float) : DrawCommandBase() {
  private constructor(sp: Array<String>) : this(sp[0].toInt(), stringToPoint2D(sp[1]))

  @VisibleForTesting
  val line = Line2D.Float(center.x, center.y, 0f, 0f)

  constructor(s: String) : this(parse(s, 2))

  override fun getLevel() = level

  override fun serialize() = buildString(javaClass.simpleName, level, point2DToString(center))

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    line.x2 = sceneContext.mouseX.toFloat()
    line.y2 = sceneContext.mouseY.toFloat()
    g.color = SELECTED
    g.stroke = LINE_TO_MOUSE_STROKE
    g.draw(line)
  }
}