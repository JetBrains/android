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
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.model.effectiveDestination
import com.android.tools.idea.naveditor.model.getActionType
import com.android.tools.idea.naveditor.model.getEffectiveSource
import com.android.tools.idea.naveditor.model.popUpTo
import com.android.tools.idea.naveditor.scene.NavColors.ACTION
import com.android.tools.idea.naveditor.scene.NavColors.HIGHLIGHTED_ACTION
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import com.android.tools.idea.naveditor.scene.draw.DrawAction
import com.android.tools.idea.naveditor.scene.draw.DrawHorizontalAction
import com.android.tools.idea.naveditor.scene.draw.DrawSelfAction
import java.awt.Color

const val HIGHLIGHTED_CLIENT_PROPERTY = "actionHighlighted"

/**
 * [ActionDecorator] responsible for creating draw commands for actions.
 */
object ActionDecorator : NavBaseDecorator() {
  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    val nlComponent = component.nlComponent
    val color = actionColor(sceneContext, component)
    val view = component.scene.designSurface.currentSceneView ?: return
    val actionType = nlComponent.getActionType(component.scene.root?.nlComponent)
    val isPopAction = nlComponent.popUpTo != null
    when (actionType) {
      ActionType.NONE -> return
      ActionType.GLOBAL, ActionType.EXIT -> {
        @SwingCoordinate val drawRect = Coordinates.getSwingRectDip(view, component.fillDrawRect2D(0, null))
        list.add(DrawHorizontalAction(drawRect, color, isPopAction))
      }
      else -> {
        val scene = component.scene

        val sourceNlComponent = scene.root?.nlComponent?.let { nlComponent.getEffectiveSource(it) } ?: return
        val sourceSceneComponent = scene.getSceneComponent(sourceNlComponent) ?: return
        val sourceRect = Coordinates.getSwingRectDip(view, sourceSceneComponent.fillDrawRect2D(0, null))

        if (actionType == ActionType.SELF) {
          DrawSelfAction.buildDisplayList(list, view, sourceRect, color, isPopAction)
        }
        else {
          val targetNlComponent = nlComponent.effectiveDestination ?: return
          val destinationSceneComponent = scene.getSceneComponent(targetNlComponent) ?: return
          val destRect = Coordinates.getSwingRectDip(view, destinationSceneComponent.fillDrawRect2D(0, null))

          DrawAction.buildDisplayList(list, view, actionType, isPopAction, sourceRect, destRect, color)
        }
      }
    }
  }

  private fun actionColor(context: SceneContext, component: SceneComponent): Color {
    return when {
      component.isSelected || component.nlComponent.getClientProperty(HIGHLIGHTED_CLIENT_PROPERTY) == true -> SELECTED
      component.drawState == SceneComponent.DrawState.HOVER || component.targets.any { it.isMouseHovered } -> HIGHLIGHTED_ACTION
      else -> ACTION
    }
  }
}