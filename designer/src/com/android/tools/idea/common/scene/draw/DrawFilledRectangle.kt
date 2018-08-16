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
package com.android.tools.idea.common.scene.draw

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D

class DrawFilledRectangle(
  private val level: Int,
  @SwingCoordinate private val rectangle: RoundRectangle2D.Float,
  @SwingCoordinate private val color: Color
) : DrawCommandBase() {

  private constructor(sp: Array<String>)
    : this(sp[0].toInt(), stringToRoundRect2D(sp[1]), stringToColor(sp[2]))

  constructor(s: String) : this(parse(s, 3))

  override fun getLevel(): Int = level

  override fun serialize(): String = buildString(javaClass.simpleName, level, roundRect2DToString(rectangle), colorToString(color))

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    g.color = color
    g.setRenderingHints(HQ_RENDERING_HINTS)
    g.fill(rectangle)
  }
}