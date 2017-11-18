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
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.naveditor.model.NavCoordinate
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * [DrawNavigationFrame] is a DrawCommand that draws a rounded
 * rectangle around the navigation component.
 */
class DrawNavigationFrame : DrawDestinationFrame {

  constructor(@SwingCoordinate myRectangle: Rectangle, myIsSelected: Boolean, myIsHover: Boolean)
      : super(myRectangle, myIsSelected, myIsHover)

  constructor(s: String) : super(s)

  override fun drawFrame(g: Graphics2D, sceneContext: SceneContext, rectangle: Rectangle) {
    val arc = Coordinates.getSwingDimension(sceneContext, CORNER_RADIUS)
    g.drawRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arc, arc)
  }

  companion object {
    @NavCoordinate const val CORNER_RADIUS = 6
  }
}