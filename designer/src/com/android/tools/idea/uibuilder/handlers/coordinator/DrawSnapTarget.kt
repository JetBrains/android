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
package com.android.tools.idea.uibuilder.handlers.coordinator

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawRegion
import com.android.tools.idea.uibuilder.handlers.constraint.animation.Animation
import java.awt.Color
import java.awt.Graphics2D

/**
 * Draw a snap target
 */
class DrawSnapTarget : DrawRegion {
  private var myMode: Int = 0

  constructor(s: String) {
    val sp = s.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    var c = 0
    c = super.parse(sp, c)
    myMode = Integer.parseInt(sp[c++])
  }

  constructor(@SwingCoordinate x: Int,
              @SwingCoordinate y: Int,
              @SwingCoordinate width: Int,
              @SwingCoordinate height: Int,
              mode: Int) : super(x, y, width, height) {
    myMode = mode
  }

  override fun getLevel(): Int {
    return DrawCommand.TARGET_LEVEL
  }

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val colorSet = sceneContext.colorSet
    val progress = (sceneContext.time % 2000).toInt() / 2000f
    val alpha = (Animation.getPulsatingAlpha(progress.toDouble()) * 0.7).toInt()
    val arc = 12
    if (myMode == OVER) {
      g.color = colorSet.highlightedSnapGuides
    } else {
      g.color = colorSet.highlightedSnapGuides.withAlpha(alpha)
    }
    g.fillRoundRect(x, y, width, height, arc, arc)
    sceneContext.repaint()
  }

  override fun serialize(): String {
    return this.javaClass.simpleName + "," + x + "," + y + "," + width + "," + height + "," + myMode
  }

  companion object {
    const val NORMAL: Int = 0
    const val OVER: Int = 1

    fun add(list: DisplayList,
            transform: SceneContext,
            @AndroidDpCoordinate left: kotlin.Float,
            @AndroidDpCoordinate top: kotlin.Float,
            @AndroidDpCoordinate right: kotlin.Float,
            @AndroidDpCoordinate bottom: kotlin.Float,
            isOver: Boolean) {
      @SwingCoordinate val l = transform.getSwingXDip(left)
      @SwingCoordinate val t = transform.getSwingYDip(top)
      @SwingCoordinate val w = transform.getSwingDimensionDip(right - left)
      @SwingCoordinate val h = transform.getSwingDimensionDip(bottom - top)
      list.add(DrawSnapTarget(l, t, w, h, if (isOver) OVER else NORMAL))
    }
  }
}

private fun Color.withAlpha(alpha: Int): Color {
  return Color(this.red, this.green, this.blue, alpha)
}
