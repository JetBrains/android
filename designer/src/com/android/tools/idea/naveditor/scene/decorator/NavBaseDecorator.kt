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

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.scene.targets.ActionHandleTarget
import com.intellij.ui.scale.JBUIScale
import java.awt.Color

@SwingCoordinate
val REGULAR_FRAME_THICKNESS = JBUIScale.scale(1f)
@SwingCoordinate
val HIGHLIGHTED_FRAME_THICKNESS = JBUIScale.scale(2f)


abstract class NavBaseDecorator : SceneDecorator() {
  override fun addFrame(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }

  override fun addBackground(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }

  fun frameColor(context: SceneContext, component: SceneComponent): Color {
    val colorSet = context.colorSet

    return when (component.drawState) {
      SceneComponent.DrawState.SELECTED -> colorSet.selectedFrames
      SceneComponent.DrawState.HOVER ->
        if (ActionHandleTarget.isDragCreateInProgress(component.nlComponent) && !component.id.isNullOrEmpty()) colorSet.selectedFrames
        else colorSet.highlightedFrames
      SceneComponent.DrawState.DRAG -> colorSet.highlightedFrames
      else -> colorSet.frames
    }
  }

  fun textColor(context: SceneContext, component: SceneComponent): Color {
    val colorSet = context.colorSet

    return if (component.isSelected) {
      colorSet.selectedText
    }
    else {
      colorSet.text
    }
  }

  fun frameThickness(component: SceneComponent): Float {
    return if (isHighlighted(component)) HIGHLIGHTED_FRAME_THICKNESS else REGULAR_FRAME_THICKNESS
  }

  fun isHighlighted(component: SceneComponent): Boolean {
    return when (component.drawState) {
      SceneComponent.DrawState.SELECTED, SceneComponent.DrawState.HOVER, SceneComponent.DrawState.DRAG -> true
      else -> false
    }
  }

}