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

import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.uibuilder.handlers.grid.getGridBarriers
import java.awt.Graphics2D

/**
 * Decorator for GridLayout.
 * TODO: support RTL
 */
open class GridLayoutDecorator : SceneDecorator() {

  override fun addBackground(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
    with(getGridBarriers(sceneContext, component)) {
      columns.forEach { x -> if (x in left ..right) list.add(DrawLineCommand(x, top, x, bottom)) }
      rows.forEach { y -> if (y in top ..bottom) list.add(DrawLineCommand(left, y, right, y)) }
    }

    super.addBackground(list, sceneContext, component)
  }
}

internal class DrawLineCommand(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : DrawCommand {

  override fun getLevel() = DrawCommand.CLIP_LEVEL

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    g.color = sceneContext.colorSet.constraints
    g.drawLine(x1, y1, x2, y2)
  }

  override fun serialize(): String = "com.android.tools.idea.uibuilder.handlers.grid.draw.DrawLineCommand: ($x1, $y1) - ($x2, $y2)"
}
