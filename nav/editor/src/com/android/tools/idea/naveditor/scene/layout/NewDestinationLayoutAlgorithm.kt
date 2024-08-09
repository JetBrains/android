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
package com.android.tools.idea.naveditor.scene.layout

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.intellij.util.ui.JBUI
import java.awt.Point

const val NEW_DESTINATION_MARKER_PROPERTY = "new.destination"

@SwingCoordinate private val INITIAL_OFFSET = JBUI.scale(40)
@SwingCoordinate private val INCREMENTAL_OFFSET = JBUI.scale(30)
@SwingCoordinate private val TOLERANCE = JBUI.scale(5)


class NewDestinationLayoutAlgorithm : SingleComponentLayoutAlgorithm() {
  override fun doLayout(component: SceneComponent): Boolean {
    if (component.nlComponent.getClientProperty(NEW_DESTINATION_MARKER_PROPERTY) != true) {
      return false
    }
    component.nlComponent.removeClientProperty(NEW_DESTINATION_MARKER_PROPERTY)

    val surface = component.scene.designSurface
    @SwingCoordinate val swingPoint = Point(surface.pannable.scrollPosition)
    swingPoint.translate(INITIAL_OFFSET, INITIAL_OFFSET)

    val view = surface.focusedSceneView ?: return false
    @NavCoordinate val point = Coordinates.getAndroidCoordinate(view, swingPoint)
    @NavCoordinate val incrementalOffset = Coordinates.getAndroidDimension(view, INCREMENTAL_OFFSET)
    @NavCoordinate val tolerance = Coordinates.getAndroidDimension(view, TOLERANCE)

    val children = surface.scene?.root?.children!!

    while (children.any {
        Math.abs(it.getDrawX(0) - point.x) < tolerance && Math.abs(it.getDrawY(0) - point.y) < tolerance
      }) {
      point.translate(incrementalOffset, incrementalOffset)
    }
    component.setPosition(point.x, point.y)
    return true
  }
}