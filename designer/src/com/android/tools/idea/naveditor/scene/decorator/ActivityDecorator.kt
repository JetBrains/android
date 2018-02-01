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
import com.android.tools.idea.common.scene.draw.DrawFilledRectangle
import com.android.tools.idea.common.scene.draw.DrawRectangle
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.*
import java.awt.Font
import java.awt.Rectangle

/**
 * [SceneDecorator] responsible for creating draw commands for one activity in the navigation editor.
 */

// Swing defines rounded rectangle corners in terms of arc diameters instead of corner radii, so use 2x the desired radius value
@NavCoordinate private val ACTIVITY_ARC_SIZE = 12
@NavCoordinate private val ACTIVITY_PADDING = 8
@NavCoordinate private val ACTIVITY_TEXT_HEIGHT = 26
@NavCoordinate private val ACTIVITY_BORDER_THICKNESS = 2

class ActivityDecorator : NavScreenDecorator() {
  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)

    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneContext, component.fillDrawRect(0, null))
    val arcSize = Coordinates.getSwingDimension(sceneContext, ACTIVITY_ARC_SIZE)
    list.add(DrawFilledRectangle(DRAW_FRAME_LEVEL, drawRectangle, sceneContext.colorSet.componentBackground, arcSize))

    @SwingCoordinate val strokeThickness = strokeThickness(sceneContext, component, ACTIVITY_BORDER_THICKNESS)
    list.add(DrawRectangle(DRAW_FRAME_LEVEL, drawRectangle, frameColor(sceneContext, component), strokeThickness, arcSize))

    val imageRectangle = Rectangle(drawRectangle)

    @SwingCoordinate val activityPadding = Coordinates.getSwingDimension(sceneContext, ACTIVITY_PADDING)
    imageRectangle.grow(-activityPadding, -activityPadding)

    @SwingCoordinate val activityTextHeight = Coordinates.getSwingDimension(sceneContext, ACTIVITY_TEXT_HEIGHT)
    imageRectangle.height -= (activityTextHeight - activityPadding)

    drawImage(list, sceneContext, component, imageRectangle)

    val textRectangle = Rectangle(drawRectangle.x, imageRectangle.y + imageRectangle.height, drawRectangle.width, activityTextHeight)
    list.add(DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, "Activity", textRectangle, textColor(sceneContext, component),
        scaledFont(sceneContext, Font.BOLD), true))
  }
}
