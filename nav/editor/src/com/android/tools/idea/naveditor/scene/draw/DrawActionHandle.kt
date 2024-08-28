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
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand.TARGET_LEVEL
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.naveditor.scene.HANDLE_STROKE
import com.android.tools.idea.naveditor.scene.makeCircleLerp
import java.awt.Color

class DrawActionHandle(
  private val center: SwingPoint,
  private val initialOuterRadius: SwingLength,
  private val finalOuterRadius: SwingLength,
  private val initialInnerRadius: SwingLength,
  private val finalInnerRadius: SwingLength,
  private val duration: Int,
  private val outerColor: Color,
  private val innerColor: Color,
) : CompositeDrawCommand(TARGET_LEVEL) {
  override fun buildCommands(): List<DrawCommand> {
    val outerCircle = makeCircleLerp(center, initialOuterRadius, finalOuterRadius, duration)
    val innerCircle = makeCircleLerp(center, initialInnerRadius, finalInnerRadius, duration)
    return listOf(
      FillShape(outerCircle, outerColor),
      DrawShape(innerCircle, innerColor, HANDLE_STROKE),
    )
  }
}
