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
import com.android.tools.idea.common.scene.SceneComponent.DrawState
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawRectangle
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.DRAW_FRAME_LEVEL
import com.android.tools.idea.naveditor.scene.frameColor
import java.awt.Rectangle

/**
 * [SceneDecorator] responsible for creating draw commands for one fragment in the navigation editor.
 */

@NavCoordinate private val FRAGMENT_BORDER_SPACING = 2
@NavCoordinate private val FRAGMENT_OUTER_BORDER_THICKNESS = 2

class FragmentDecorator : NavScreenDecorator() {
  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)

    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneContext, component.fillDrawRect(0, null))
    list.add(DrawRectangle(DRAW_FRAME_LEVEL, drawRectangle, sceneContext.colorSet.frames, 1))

    val imageRectangle = Rectangle(drawRectangle)
    imageRectangle.grow(-1, -1)
    drawImage(list, sceneContext, component, imageRectangle)

    when (component.drawState) {
      DrawState.DRAG, DrawState.SELECTED, DrawState.HOVER -> {
        @SwingCoordinate val borderSpacing = Coordinates.getSwingDimension(sceneContext, FRAGMENT_BORDER_SPACING)
        @SwingCoordinate val outerBorderThickness = Coordinates.getSwingDimension(sceneContext, FRAGMENT_OUTER_BORDER_THICKNESS)

        val outerRectangle = Rectangle(drawRectangle)
        outerRectangle.grow(2 * borderSpacing, 2 * borderSpacing)

        list.add(DrawRectangle(DRAW_FRAME_LEVEL, outerRectangle, frameColor(sceneContext, component), outerBorderThickness,2 * borderSpacing)
        )
      }
    }
  }
}
