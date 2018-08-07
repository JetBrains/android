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
import com.android.tools.idea.naveditor.model.includeFileName
import com.android.tools.idea.naveditor.scene.*
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.util.ui.JBUI
import java.awt.Font


// Swing defines rounded rectangle corners in terms of arc diameters instead of corner radii, so use 2x the desired radius value
@NavCoordinate
private val NAVIGATION_ARC_SIZE = JBUI.scale(12)

/**
 * [SceneDecorator] for the whole of a navigation flow (that is, the root component).
 */
class NavigationDecorator : SceneDecorator() {

  override fun addBackground(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {}

  override fun addFrame(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {}

  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    if (isDisplayRoot(sceneContext, component)) {
      return
    }

    @SwingCoordinate val drawRectangle = Coordinates.getSwingRect(sceneContext, component.fillDrawRect(0, null))
    @SwingCoordinate val arcSize = Coordinates.getSwingDimension(sceneContext, NAVIGATION_ARC_SIZE)
    list.add(DrawFilledRectangle(DRAW_FRAME_LEVEL, drawRectangle, sceneContext.colorSet.componentBackground, arcSize))
    list.add(DrawRectangle(DRAW_FRAME_LEVEL, drawRectangle, frameColor(sceneContext, component), frameThickness(component), arcSize))

    var text = component.nlComponent.includeFileName ?: "Nested Graph"

    val font = scaledFont(sceneContext, Font.BOLD)
    list.add(DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, text, drawRectangle,
                               textColor(sceneContext, component), font, true))
  }

  override fun buildList(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    if (isDisplayRoot(sceneContext, component)) {
      super.buildList(list, time, sceneContext, component)
      return
    }

    val displayList = DisplayList()
    super.buildList(displayList, time, sceneContext, component)
    list.add(createDrawCommand(displayList, component))
  }

  override fun buildListChildren(list: DisplayList,
                                 time: Long,
                                 sceneContext: SceneContext,
                                 component: SceneComponent) {
    if (isDisplayRoot(sceneContext, component)) {
      super.buildListChildren(list, time, sceneContext, component)
      return
    }

    // TODO: Either set an appropriate clip here, or make this the default behavior in the base class
    for (child in component.children) {
      child.buildDisplayList(time, list, sceneContext)
    }
  }

  private fun isDisplayRoot(sceneContext: SceneContext, sceneComponent: SceneComponent): Boolean {
    return (sceneContext.surface as NavDesignSurface?)?.currentNavigation == sceneComponent.nlComponent
  }
}
