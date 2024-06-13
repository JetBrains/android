/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.draw

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawRegion
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Stroke

/** Draws animated frames but without directions covering one side. */
class DrawAnimatedFrameNoDirection(
  @SwingCoordinate x: Int,
  @SwingCoordinate y: Int,
  @SwingCoordinate width: Int,
  @SwingCoordinate height: Int,
) : DrawRegion(x, y, width, height) {

  companion object {
    private const val REPAT_MS = 1000
    private const val PATERN_LENGTH = 20
    private val myAnimationStroke: Stroke =
      BasicStroke(
        2f,
        BasicStroke.CAP_SQUARE,
        BasicStroke.JOIN_MITER,
        1f,
        floatArrayOf(10f, 10f),
        0f,
      )

    @JvmStatic
    fun add(list: DisplayList, @AndroidDpCoordinate rect: Rectangle) {
      list.add(DrawAnimatedFrameNoDirection(rect.x, rect.y, rect.width, rect.height))
    }
  }

  private val mXPoints = IntArray(6)
  private val mYPoints = IntArray(6)

  override fun getLevel(): Int {
    return TOP_LEVEL
  }

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val colorSet = sceneContext.colorSet
    sceneContext.repaint()
    val previousStroke = g.stroke
    val previousColor = g.color
    var shift = (sceneContext.time % REPAT_MS).toInt()
    shift /= REPAT_MS / PATERN_LENGTH

    mXPoints[0] = x + width / 2 - Math.min(shift, width / 2)
    mYPoints[0] = y + height
    mXPoints[1] = x
    mYPoints[1] = y + height
    mXPoints[2] = x
    mYPoints[2] = y
    mXPoints[3] = x + width
    mYPoints[3] = y
    mXPoints[4] = x + width
    mYPoints[4] = y + height
    mXPoints[5] = x + width / 2
    mYPoints[5] = y + height

    g.stroke = myAnimationStroke
    g.color = colorSet.highlightedFrames
    g.drawPolyline(mXPoints, mYPoints, mXPoints.size)

    g.stroke = previousStroke
    g.color = previousColor
  }
}
