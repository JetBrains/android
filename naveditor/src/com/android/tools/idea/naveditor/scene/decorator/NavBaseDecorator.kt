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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.SdkConstants
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.model.isStartDestination
import com.android.tools.idea.naveditor.model.uiName
import com.android.tools.idea.naveditor.scene.NavColors.FRAME
import com.android.tools.idea.naveditor.scene.NavColors.HIGHLIGHTED_FRAME
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import com.android.tools.idea.naveditor.scene.NavColors.TEXT
import com.android.tools.idea.naveditor.scene.draw.DrawHeader
import com.android.tools.idea.naveditor.scene.getHeaderRect
import com.android.tools.idea.naveditor.scene.targets.ActionHandleTarget
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.geom.Rectangle2D

@SwingCoordinate
val REGULAR_FRAME_THICKNESS = JBUI.scale(1f)
val REGULAR_FRAME_STROKE = BasicStroke(REGULAR_FRAME_THICKNESS)
@SwingCoordinate
val HIGHLIGHTED_FRAME_THICKNESS = JBUI.scale(2f)
val HIGHLIGHTED_FRAME_STROKE = BasicStroke(HIGHLIGHTED_FRAME_THICKNESS)


abstract class NavBaseDecorator : SceneDecorator() {
  override fun addFrame(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }

  override fun addBackground(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }

  protected fun addHeader(list: DisplayList, sceneView: SceneView, rectangle: Rectangle2D.Float, component: SceneComponent) {
    val headerRect = getHeaderRect(sceneView, rectangle)
    val scale = sceneView.scale.toFloat()
    val text = component.nlComponent.uiName
    val isStart = component.nlComponent.isStartDestination
    val hasDeepLink = component.nlComponent.children.any { it.tagName == SdkConstants.TAG_DEEP_LINK }

    list.add(DrawHeader(headerRect, scale, text, isStart, hasDeepLink))
  }

  fun frameColor(component: SceneComponent): Color =
    when (component.drawState) {
      SceneComponent.DrawState.SELECTED -> SELECTED
      SceneComponent.DrawState.HOVER ->
        if (ActionHandleTarget.isDragCreateInProgress(component.nlComponent) && !component.id.isNullOrEmpty()) SELECTED
        else HIGHLIGHTED_FRAME
      SceneComponent.DrawState.DRAG -> HIGHLIGHTED_FRAME
      else -> FRAME
    }


  fun textColor(component: SceneComponent): Color = if (component.isSelected) SELECTED else TEXT

  fun frameThickness(component: SceneComponent): Float =
    if (isHighlighted(component)) HIGHLIGHTED_FRAME_THICKNESS else REGULAR_FRAME_THICKNESS

  fun isHighlighted(component: SceneComponent): Boolean =
    when (component.drawState) {
      SceneComponent.DrawState.SELECTED, SceneComponent.DrawState.HOVER, SceneComponent.DrawState.DRAG -> true
      else -> false
    }
}