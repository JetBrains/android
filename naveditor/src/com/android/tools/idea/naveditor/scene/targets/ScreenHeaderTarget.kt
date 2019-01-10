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
package com.android.tools.idea.naveditor.scene.targets

import com.android.SdkConstants
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.model.isStartDestination
import com.android.tools.idea.naveditor.model.uiName
import com.android.tools.idea.naveditor.scene.HEADER_HEIGHT
import com.android.tools.idea.naveditor.scene.draw.DrawHeader
import java.awt.geom.Rectangle2D

/**
 * [ScreenHeaderTarget] draws the header above the frame.
 * It consists of an optional start destination icon, followed by
 * the label, followed by an optional deep link icon.
 */
class ScreenHeaderTarget(component: SceneComponent) : NavBaseTarget(component) {
  override fun getPreferenceLevel(): Int {
    return Target.ANCHOR_LEVEL
  }

  override fun layout(sceneTransform: SceneContext,
                      @NavCoordinate l: Int,
                      @NavCoordinate t: Int,
                      @NavCoordinate r: Int,
                      @NavCoordinate b: Int): Boolean {
    layoutRectangle(l, t - HEADER_HEIGHT.toInt(), r, t)
    return false
  }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    val view = component.scene.designSurface.currentSceneView ?: return

    @SwingCoordinate val l = Coordinates.getSwingX(view, myLeft)
    @SwingCoordinate val t = Coordinates.getSwingY(view, myTop)
    @SwingCoordinate val r = Coordinates.getSwingX(view, myRight)
    @SwingCoordinate val b = Coordinates.getSwingY(view, myBottom)

    @SwingCoordinate val rectangle = Rectangle2D.Float(l, t, r - l, b - t)
    val scale = sceneContext.scale.toFloat()
    val text = component.nlComponent.uiName

    val isStart = component.nlComponent.isStartDestination
    val hasDeepLink = component.nlComponent.children.any { it.tagName == SdkConstants.TAG_DEEP_LINK }

    list.add(DrawHeader(rectangle, scale, text, isStart, hasDeepLink))
  }

  override fun addHit(transform: SceneContext, picker: ScenePicker) {
    picker.addRect(this, 0, getSwingLeft(transform), getSwingTop(transform), getSwingRight(transform), getSwingBottom(transform))
  }

  override fun newSelection() = listOf(component)
}
