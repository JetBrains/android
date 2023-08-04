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
package com.android.tools.idea.uibuilder.handlers.grid.draw

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.uibuilder.handlers.grid.getGridBarriers
import java.awt.Graphics2D

/**
 * Decorator for GridLayout.
 *
 * TODO: support RTL
 */
open class GridLayoutDecorator : SceneDecorator() {

  override fun addBackground(
    list: DisplayList,
    sceneContext: SceneContext,
    component: SceneComponent
  ) {
    super.addBackground(list, sceneContext, component)
    with(getGridBarriers(component)) {
      // Add barrier lines
      columnIndices
        .mapNotNull { getColumnValue(it) }
        .distinct()
        .filter { it in left..right }
        .forEach { list.add(DrawLineCommand(it, top, it, bottom)) }
      rowIndices
        .mapNotNull { getRowValue(it) }
        .distinct()
        .filter { it in top..bottom }
        .forEach { list.add(DrawLineCommand(left, it, right, it)) }
    }
  }
}

private class DrawLineCommand(
  @AndroidDpCoordinate val x1: Int,
  @AndroidDpCoordinate val y1: Int,
  @AndroidDpCoordinate val x2: Int,
  @AndroidDpCoordinate val y2: Int
) : DrawCommand {

  override fun getLevel() = DrawCommand.CLIP_LEVEL

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val swingX1 = sceneContext.getSwingXDip(x1.toFloat())
    val swingY1 = sceneContext.getSwingYDip(y1.toFloat())
    val swingX2 = sceneContext.getSwingXDip(x2.toFloat())
    val swingY2 = sceneContext.getSwingYDip(y2.toFloat())
    g.color = sceneContext.colorSet.constraints
    g.drawLine(swingX1, swingY1, swingX2, swingY2)
  }

  // TODO: fix this serialize
  override fun serialize(): String =
    "com.android.tools.idea.uibuilder.handlers.grid.draw.DrawLineCommand: ($x1, $y1) - ($x2, $y2)"
}
