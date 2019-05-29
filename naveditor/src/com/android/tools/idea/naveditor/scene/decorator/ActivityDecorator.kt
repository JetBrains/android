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
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.draw.DrawActivity
import com.android.tools.idea.naveditor.scene.growRectangle
import com.intellij.util.ui.JBUI
import java.awt.geom.Rectangle2D

/**
 * [SceneDecorator] responsible for creating draw commands for one activity in the navigation editor.
 */

@NavCoordinate
private val ACTIVITY_PADDING = JBUI.scale(8f)
@NavCoordinate
private val ACTIVITY_TEXT_HEIGHT = JBUI.scale(26f)

object ActivityDecorator : NavScreenDecorator() {
  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)

    val sceneView = sceneContext.surface?.currentSceneView ?: return
    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneView, component.fillDrawRect2D(0, null))
    addHeader(list, sceneView, drawRectangle, component)

    @SwingCoordinate val imageRectangle = drawRectangle.let { Rectangle2D.Float(it.x, it.y, it.width, it.height) }

    @SwingCoordinate val padding = Coordinates.getSwingDimension(sceneView, ACTIVITY_PADDING)
    growRectangle(imageRectangle, -padding, -padding)

    @SwingCoordinate val textHeight = Coordinates.getSwingDimension(sceneView, ACTIVITY_TEXT_HEIGHT)
    imageRectangle.height -= (textHeight - padding)

    val scale = sceneContext.scale.toFloat()
    val frameColor = frameColor(component)
    val frameThickness = frameThickness(component)
    val textColor = textColor(component)
    val image = buildImage(sceneContext, component, imageRectangle)

    list.add(DrawActivity(drawRectangle, imageRectangle, scale, frameColor, frameThickness, textColor, image))
  }
}
