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

import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.idea.common.model.scaledAndroidLength
import com.android.tools.idea.common.model.times
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.inlineDrawRect
import com.android.tools.idea.common.scene.inlineScale
import com.android.tools.idea.naveditor.scene.draw.DrawActivity

/**
 * [SceneDecorator] responsible for creating draw commands for one activity in the navigation editor.
 */

private val ACTIVITY_PADDING = scaledAndroidLength(8f)
private val ACTIVITY_TEXT_HEIGHT = scaledAndroidLength(26f)

object ActivityDecorator : NavScreenDecorator() {
  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)

    val sceneView = sceneContext.surface?.focusedSceneView ?: return
    val drawRectangle = component.inlineDrawRect(sceneView)
    addHeader(list, sceneContext, drawRectangle, component)

    val scale = sceneContext.inlineScale
    val padding = ACTIVITY_PADDING * scale
    val textHeight = ACTIVITY_TEXT_HEIGHT * scale

    val x = drawRectangle.x + padding
    val y = drawRectangle.y + padding
    val width = drawRectangle.width - padding * 2
    val height = drawRectangle.height - padding - textHeight
    val imageRectangle = SwingRectangle(x, y, width, height)

    val frameColor = frameColor(component)
    val frameThickness = frameThickness(component)
    val textColor = textColor(component)
    val image = buildImage(sceneContext, component, imageRectangle)

    list.add(DrawActivity(drawRectangle, imageRectangle, scale, frameColor, frameThickness, textColor, image))
  }
}
