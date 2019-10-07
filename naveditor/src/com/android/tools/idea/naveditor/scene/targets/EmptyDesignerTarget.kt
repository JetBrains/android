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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.BaseTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.editor.NavActionManager
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.draw.DrawEmptyDesigner
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import icons.StudioIcons.NavEditor.Toolbar.ADD_DESTINATION
import org.intellij.lang.annotations.JdkConstants
import java.awt.Point

@SwingCoordinate
val WIDTH = 240

class EmptyDesignerTarget(private val surface: NavDesignSurface) : BaseTarget() {
  override fun getPreferenceLevel() = ACTION_LEVEL

  override fun layout(
    context: SceneContext,
    @NavCoordinate l: Int,
    @NavCoordinate t: Int,
    @NavCoordinate r: Int,
    @NavCoordinate b: Int
  ): Boolean {
    @NavCoordinate val width = Coordinates.getAndroidDimension(surface, WIDTH)
    @NavCoordinate val height = Coordinates.getAndroidDimension(surface, ADD_DESTINATION.iconHeight)

    myLeft = ((l + r - width) / 2).toFloat()
    myTop = ((t + b - height) / 2).toFloat()
    myRight = myLeft + width
    myBottom = myTop + height

    return false
  }

  override fun addHit(transform: SceneContext,
                      picker: ScenePicker,
                      @JdkConstants.InputEventMask modifiersEx: Int) {
    picker.addRect(
        this, 0,
        transform.getSwingX(myLeft.toInt()),
        transform.getSwingY(myTop.toInt()),
        transform.getSwingX(myRight.toInt()),
        transform.getSwingY(myBottom.toInt())
    )
  }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    @SwingCoordinate val x = sceneContext.getSwingX(myLeft.toInt())
    @SwingCoordinate val y = sceneContext.getSwingY(myBottom.toInt())
    list.add(DrawEmptyDesigner(Point(x, y)))
  }

  override fun mouseRelease(x: Int, y: Int, closestTargets: MutableList<Target>) {
    val navActionManager = surface.actionManager as? NavActionManager
    navActionManager?.addDestinationMenu?.show()
  }
}