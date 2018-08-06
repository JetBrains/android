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
import com.android.tools.idea.common.model.Coordinates.getSwingDimension
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawFilledRectangle
import com.android.tools.idea.common.scene.draw.DrawRectangle
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.*
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D

/**
 * [SceneDecorator] responsible for creating draw commands for one activity in the navigation editor.
 */

// Swing defines rounded rectangle corners in terms of arc diameters instead of corner radii, so use 2x the desired radius value
@NavCoordinate
private val ACTIVITY_ARC_SIZE = JBUI.scale(12f)
@NavCoordinate
private val ACTIVITY_PADDING = JBUI.scale(8f)
@NavCoordinate
private val ACTIVITY_TEXT_HEIGHT = JBUI.scale(26f)
@SwingCoordinate
private val ACTIVITY_BORDER_WIDTH = JBUI.scale(1f)

object ActivityDecorator : NavScreenDecorator() {
  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)

    val sceneView = sceneContext.surface?.currentSceneView ?: return

    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneView, component.fillDrawRect2D(0, null))
    setArcSize(sceneView, drawRectangle, ACTIVITY_ARC_SIZE)
    list.add(DrawFilledRectangle(DRAW_FRAME_LEVEL, drawRectangle, sceneContext.colorSet.componentBackground))
    list.add(DrawRectangle(DRAW_FRAME_LEVEL, drawRectangle, frameColor(sceneContext, component), frameThickness(component)))

    val imageRectangle = RoundRectangle2D.Float(drawRectangle.x, drawRectangle.y,
                                                drawRectangle.width, drawRectangle.height,
                                                0f, 0f)

    @SwingCoordinate val activityPadding = Coordinates.getSwingDimension(sceneView, ACTIVITY_PADDING)
    growRectangle(imageRectangle, -activityPadding, -activityPadding)

    @SwingCoordinate val activityTextHeight = getSwingDimension(sceneView, ACTIVITY_TEXT_HEIGHT)
    imageRectangle.height -= (activityTextHeight - activityPadding)

    drawImage(list, sceneContext, component, roundRect2DToRect(imageRectangle))

    val imageBorder = RoundRectangle2D.Float()
    imageBorder.setRoundRect(imageRectangle)

    growRectangle(imageBorder, ACTIVITY_BORDER_WIDTH, ACTIVITY_BORDER_WIDTH)
    list.add(DrawRectangle(DRAW_ACTIVITY_BORDER_LEVEL, imageRectangle, NavColorSet.ACTIVITY_BORDER_COLOR, ACTIVITY_BORDER_WIDTH))

    val textRectangle = Rectangle(drawRectangle.x.toInt(), (imageRectangle.y + imageRectangle.height).toInt(), drawRectangle.width.toInt(),
                                  activityTextHeight.toInt())
    list.add(DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, "Activity", textRectangle, textColor(sceneContext, component),
                               scaledFont(sceneContext, Font.BOLD), true))
  }
}
