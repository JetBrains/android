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

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommand
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * [DrawDestinationFrame] draws a frame around the destination.
 * The thickness is increased if the component is either selected,
 * or the mouse is over it.
 */
abstract class DrawDestinationFrame(@SwingCoordinate private val myRectangle: Rectangle,
                                    private val myIsSelected: Boolean,
                                    private val myIsHover: Boolean) : NavBaseDrawCommand() {

  private constructor(sp: Array<String>) : this(NavBaseDrawCommand.stringToRect(sp[0]), sp[1].toBoolean(), sp[2].toBoolean())

  constructor(s: String) : this(parse(s, 3))

  override fun getLevel(): Int {
    return DrawCommand.COMPONENT_LEVEL
  }

  override fun getProperties(): Array<Any> {
    return arrayOf(NavBaseDrawCommand.rectToString(myRectangle), myIsSelected, myIsHover)
  }

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    val colorSet = sceneContext.colorSet
    g.color = if (myIsSelected) colorSet.selectedFrames else colorSet.frames

    val strokeWidth = if (myIsSelected || myIsHover) THICK_STROKE_WIDTH else THIN_STROKE_WIDTH
    g.stroke = BasicStroke(sceneContext.getSwingDimensionDip(strokeWidth).toFloat())

    drawFrame(g, sceneContext, myRectangle)
  }

  protected abstract fun drawFrame(g: Graphics2D, sceneContext: SceneContext, rectangle: Rectangle)

  private companion object {
    val THIN_STROKE_WIDTH = 1.0f
    val THICK_STROKE_WIDTH = 2.5f
  }
}