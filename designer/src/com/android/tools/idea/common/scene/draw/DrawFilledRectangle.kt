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
import java.awt.Rectangle

class DrawFilledRectangle(
  private val level: Int,
  @SwingCoordinate private val rectangle: Rectangle,
  @SwingCoordinate private val color: Color,
  @SwingCoordinate private val arcSize: Int = 0
) : DrawCommand {

  private constructor(sp: Array<String>)
      : this(sp[0].toInt(), stringToRect(sp[1]), stringToColor(sp[2]), sp[3].toInt())

  constructor(s: String) : this(parse(s, 4))

  override fun getLevel(): Int = level

  override fun serialize(): String = buildString(javaClass.simpleName, level, rectToString(rectangle), colorToString(color), arcSize)

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val g2 = g.create()

    g2.color = color
    g2.fillRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arcSize, arcSize)

    g2.dispose()
  }
}