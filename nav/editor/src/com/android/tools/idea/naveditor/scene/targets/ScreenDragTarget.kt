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

import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.target.DragBaseTarget
import com.android.tools.idea.common.scene.target.LegacyDragTarget
import com.android.tools.idea.common.scene.target.MultiComponentTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.NavSceneManager
import org.intellij.lang.annotations.JdkConstants
import java.awt.Cursor
import java.awt.Point
import kotlin.math.absoluteValue

/**
 * Implements a target allowing dragging a nav editor screen
 */
class ScreenDragTarget(component: SceneComponent) : DragBaseTarget(), MultiComponentTarget, LegacyDragTarget {
  private val childOffsets: Array<Point?>

  init {
    setComponent(component)
    childOffsets = arrayOfNulls(component.children.size)
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  override fun updateAttributes(attributes: NlAttributesHolder, x: Int, y: Int) {
    // Nothing
  }

  override fun mouseDown(@NavCoordinate x: Int, @NavCoordinate y: Int) {
    super<DragBaseTarget>.mouseDown(x, y)
    component.children.forEachIndexed { i, child -> childOffsets[i] = Point(x - child.drawX, y - child.drawY) }
  }

  override fun mouseDrag(@NavCoordinate x: Int, @NavCoordinate y: Int, closestTarget: List<Target>, context: SceneContext) {
    val parent = myComponent.parent ?: return

    myComponent.isDragging = true
    val dx = x - myOffsetX
    val dy = y - myOffsetY

    if (dx < parent.drawX || dx + myComponent.drawWidth > parent.drawX + parent.drawWidth) {
      return
    }

    if (dy < parent.drawY || dy + myComponent.drawHeight > parent.drawY + parent.drawHeight) {
      return
    }

    myComponent.setPosition(dx, dy)
    myChangedComponent = true

    childOffsets.forEachIndexed { i, offset ->
      offset?.let { component.getChild(i).setPosition(x - it.x, y - it.y) }
    }
  }

  override fun mouseRelease(@NavCoordinate x: Int, @NavCoordinate y: Int, closestTargets: List<Target>) {
    if (!myComponent.isDragging) {
      return
    }
    myComponent.isDragging = false
    if (myComponent.parent != null) {
      if ((x - myFirstMouseX).absoluteValue <= 1 && (y - myFirstMouseY).absoluteValue <= 1) {
        return
      }
      (myComponent.scene.sceneManager as NavSceneManager).save(listOf(myComponent))
    }
    if (myChangedComponent) {
      myComponent.scene.markNeedsLayout(Scene.IMMEDIATE_LAYOUT)
    }
  }

  override fun getMouseCursor(@JdkConstants.InputEventMask modifiersEx: Int): Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
