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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.adtui.common.SwingLength
import com.android.tools.adtui.common.SwingPoint
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand.TARGET_LEVEL
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import com.android.tools.idea.naveditor.scene.makeCircle
import com.android.tools.idea.naveditor.scene.makeCircleLerp

class DrawActionHandleDrag(
  private val center: SwingPoint,
  private val initialOuterRadius: SwingLength,
  private val finalOuterRadius: SwingLength,
  private val innerRadius: SwingLength,
  private val duration: Int,
) : CompositeDrawCommand(TARGET_LEVEL) {
  override fun buildCommands(): List<DrawCommand> {
    val outerCircle = makeCircleLerp(center, initialOuterRadius, finalOuterRadius, duration)
    val innerCircle = makeCircle(center, innerRadius)
    return listOf(
      FillShape(outerCircle, primaryPanelBackground),
      FillShape(innerCircle, SELECTED),
      DrawLineToMouse(center),
    )
  }
}
