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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawRectangle
import com.android.tools.idea.common.scene.draw.DrawRoundRectangle
import com.android.tools.idea.naveditor.scene.DRAW_FRAME_LEVEL
import com.android.tools.idea.naveditor.scene.FRAGMENT_BORDER_SPACING
import com.android.tools.idea.naveditor.scene.NavColors.FRAME
import com.android.tools.idea.naveditor.scene.convertToRoundRect
import com.android.tools.idea.naveditor.scene.growRectangle
import java.awt.geom.Rectangle2D

/**
 * [SceneDecorator] responsible for creating draw commands for one fragment in the navigation editor.
 */

object FragmentDecorator : NavScreenDecorator() {
  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)

    val sceneView = sceneContext.surface?.currentSceneView ?: return

    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneView, component.fillDrawRect2D(0, null))
    list.add(DrawRectangle(DRAW_FRAME_LEVEL, drawRectangle, FRAME, REGULAR_FRAME_THICKNESS))

    @SwingCoordinate val imageRectangle = Rectangle2D.Float()
    imageRectangle.setRect(drawRectangle)
    growRectangle(imageRectangle, -1f, -1f)
    drawScreen(list, sceneContext, component, imageRectangle)

    if (isHighlighted(component)) {
      @SwingCoordinate val borderSpacing = Coordinates.getSwingDimension(sceneView, FRAGMENT_BORDER_SPACING)

      @SwingCoordinate val outerRectangle = convertToRoundRect(sceneView, drawRectangle, 2 * FRAGMENT_BORDER_SPACING)
      growRectangle(outerRectangle, 2 * borderSpacing, 2 * borderSpacing)

      list.add(
        DrawRoundRectangle(
          DRAW_FRAME_LEVEL,
          outerRectangle,
          frameColor(component),
          HIGHLIGHTED_FRAME_THICKNESS
        )
      )
    }
  }
}
