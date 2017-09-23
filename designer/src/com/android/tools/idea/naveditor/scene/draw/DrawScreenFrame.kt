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
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * [DrawScreenFrame] draws a rectangular frame around the fragment.
 */
class DrawScreenFrame : DrawDestinationFrame {

  constructor(@SwingCoordinate myRectangle: Rectangle, myIsSelected: Boolean, myIsHover: Boolean) : super(myRectangle, myIsSelected, myIsHover)

  constructor(s: String) : super(s)

  override fun drawFrame(g: Graphics2D, sceneContext: SceneContext, rectangle: Rectangle) {
    g.drawRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height)
  }
}