/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.adtui.common.SwingShape
import com.android.tools.adtui.common.SwingStroke
import com.android.tools.idea.common.scene.AnimatedValue
import com.android.tools.idea.common.scene.ConstantValue
import com.android.tools.idea.common.scene.SceneContext
import com.google.common.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Graphics2D

class DrawShape(
  @VisibleForTesting val shape: AnimatedValue<SwingShape>,
  @VisibleForTesting val color: Color,
  @VisibleForTesting val stroke: SwingStroke,
  level: Int = 0,
) : DrawCommandBase(level) {
  constructor(
    shape: SwingShape,
    color: Color,
    stroke: SwingStroke,
    level: Int = 0,
  ) : this(ConstantValue<SwingShape>(shape), color, stroke, level)

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    g.setRenderingHints(HQ_RENDERING_HINTS)
    g.color = color
    g.stroke = stroke.value

    val time = sceneContext.time
    g.draw(shape.getValue(time).value)

    if (!shape.isComplete(time)) {
      sceneContext.repaint()
    }
  }
}
