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
package com.android.tools.idea.uibuilder.scene.decorator

import com.android.SdkConstants
import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawComponentBackground
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler
import java.awt.*

class LinearLayoutDecorator : SceneDecorator() {

  class DrawLinearLayout : DrawComponentBackground {
    internal val paint: Paint?
    internal val vertical: Boolean

    constructor(
      @SwingCoordinate x: Int,
      @SwingCoordinate y: Int,
      @SwingCoordinate width: Int,
      @SwingCoordinate height: Int,
      paint: Paint? = null,
      vertical: Boolean = false,
      mode: Int = DrawComponentBackground.NORMAL
    ) : super(x, y, width, height, mode) {
      this.paint = paint
      this.vertical = vertical
    }

    @Suppress("unused") // Used by reflexion in test
    constructor(s: String) : super(s) {
      this.paint = null
      this.vertical = false
    }

    override fun paint(g: Graphics2D, sceneContext: SceneContext) {
      val transform = g.transform
      val savedPaint = g.paint

      g.paint = paint ?: savedPaint
      g.translate(x, y)
      if (vertical) {
        g.fillRect(0, 0, width, GRADIENT_SIZE.toInt())
        g.rotate(Math.PI, width / 2.0, height / 2.0)
        g.fillRect(0, 0, width, GRADIENT_SIZE.toInt())
      } else {
        g.fillRect(0, 0, GRADIENT_SIZE.toInt(), height)
        g.rotate(Math.PI, width / 2.0, height / 2.0)
        g.fillRect(0, 0, GRADIENT_SIZE.toInt(), height)
      }

      g.paint = savedPaint
      g.transform = transform
    }
  }

  override fun addBackground(
    list: DisplayList,
    sceneContext: SceneContext,
    component: SceneComponent
  ) {
    @AndroidDpCoordinate val rect = Rectangle()
    component.fillDrawRect(0, rect)
    @SwingCoordinate val l = sceneContext.getSwingXDip(rect.x.toFloat())
    @SwingCoordinate val t = sceneContext.getSwingYDip(rect.y.toFloat())
    @SwingCoordinate val w = sceneContext.getSwingDimensionDip(rect.width.toFloat())
    @SwingCoordinate val h = sceneContext.getSwingDimensionDip(rect.height.toFloat())

    var vertical = false
    sceneContext.surface?.project?.let {
      val handler =
        ViewHandlerManager.get(it).getHandler(SdkConstants.LINEAR_LAYOUT) {} as LinearLayoutHandler
      vertical = handler.isVertical(component.nlComponent)
    }

    val colorSet = sceneContext.colorSet
    val solid = colorSet.frames
    val baseRgb = solid.rgb
    val darkRgb = solid.darker().darker().rgb
    val transparent1 = Color((darkRgb and 0xFFFFFF) or 0x8F000000.toInt(), true)
    val transparent2 = Color(baseRgb and 0xFFFFFF, true)
    val paint =
      if (vertical) GradientPaint(0f, 0f, transparent1, 0f, GRADIENT_SIZE, transparent2)
      else GradientPaint(0f, 0f, transparent1, GRADIENT_SIZE, 0f, transparent2)
    list.add(LinearLayoutDecorator.DrawLinearLayout(l, t, w, h, paint, vertical))

    // Draw the regular background for hover only.
    if (component.drawState == SceneComponent.DrawState.HOVER) {
      super.addBackground(list, sceneContext, component)
    }
  }

  companion object {
    val GRADIENT_SIZE: Float = 12f
  }
}
