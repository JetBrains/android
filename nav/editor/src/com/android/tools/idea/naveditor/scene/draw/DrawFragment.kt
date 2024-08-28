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
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.SwingRoundRectangle
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.model.times
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand.COMPONENT_LEVEL
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.naveditor.scene.FRAGMENT_BORDER_SPACING
import com.android.tools.idea.naveditor.scene.NavColors
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.android.tools.idea.naveditor.scene.createDrawImageCommand
import com.android.tools.idea.naveditor.scene.decorator.HIGHLIGHTED_FRAME_STROKE
import com.android.tools.idea.naveditor.scene.decorator.REGULAR_FRAME_STROKE
import java.awt.Color

class DrawFragment(
  private val rectangle: SwingRectangle,
  private val scale: Scale,
  private val highlightColor: Color?,
  private val image: RefinableImage? = null,
) : CompositeDrawCommand(COMPONENT_LEVEL) {
  override fun buildCommands(): List<DrawCommand> {
    val list = mutableListOf<DrawCommand>()
    list.add(DrawShape(rectangle, NavColors.FRAME, REGULAR_FRAME_STROKE))

    val imageRectangle = rectangle.growRectangle(SwingLength(-1f), SwingLength(-1f))
    list.add(createDrawImageCommand(imageRectangle, image))

    if (highlightColor != null) {
      val spacing = 2 * FRAGMENT_BORDER_SPACING * scale
      val roundRectangle =
        SwingRoundRectangle(rectangle.growRectangle(spacing, spacing), spacing, spacing)
      list.add(DrawShape(roundRectangle, highlightColor, HIGHLIGHTED_FRAME_STROKE))
    }

    return list
  }
}
