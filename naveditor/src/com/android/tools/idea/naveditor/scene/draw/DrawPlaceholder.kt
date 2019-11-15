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

import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.SwingStroke
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BACKGROUND
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BORDER
import com.android.tools.idea.naveditor.scene.decorator.REGULAR_FRAME_THICKNESS
import com.google.common.annotations.VisibleForTesting
import java.awt.geom.Line2D

private val STROKE = SwingStroke(REGULAR_FRAME_THICKNESS)

class DrawPlaceholder(@VisibleForTesting val rectangle: SwingRectangle) : CompositeDrawCommand() {
  override fun serialize(): String = ""

  override fun buildCommands(): List<DrawCommand> {
    val rect = FillShape(rectangle.value, PLACEHOLDER_BACKGROUND)

    val x1 = rectangle.x.value
    val x2 = x1 + rectangle.width.value
    val y1 = rectangle.y.value
    val y2 = y1 + rectangle.height.value

    val line1 = DrawShape(Line2D.Float(x1, y1, x2, y2), PLACEHOLDER_BORDER, STROKE)
    val line2 = DrawShape(Line2D.Float(x1, y2, x2, y1), PLACEHOLDER_BORDER, STROKE)

    return listOf(rect, line1, line2)
  }
}