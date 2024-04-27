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

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.BaseTarget
import com.android.tools.idea.common.scene.target.MultiComponentTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.uibuilder.scene.target.TargetSnapper
import com.google.common.collect.ImmutableList
import com.intellij.ui.JBColor
import org.intellij.lang.annotations.JdkConstants
import java.awt.Cursor
import java.awt.Point
import kotlin.math.absoluteValue

/**
 * Implements a target allowing dragging a nav editor screen
 */
class ScreenDragTarget(component: SceneComponent) : BaseTarget(), MultiComponentTarget {
  private val DEBUG_RENDERER = false

  @AndroidDpCoordinate
  private var offsetX = 0
  @AndroidDpCoordinate
  private var offsetY = 0
  @AndroidDpCoordinate
  private var firstMouseX = 0
  @AndroidDpCoordinate
  private var firstMouseY = 0
  private var changedComponent = false

  private val targetSnapper: TargetSnapper = TargetSnapper()
  private val childOffsets: Array<Point?>

  init {
    setComponent(component)
    childOffsets = arrayOfNulls(component.children.size)
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  override fun newSelection(): List<SceneComponent?>? {
    if (changedComponent) {
      val selection = getComponent().scene.selection
      return if (selection.size == 1) {
        ImmutableList.of(getComponent())
      }
      else {
        val scene = myComponent.scene
        selection.stream().map { c: NlComponent? ->
          scene.getSceneComponent(c)
        }.collect(ImmutableList.toImmutableList())
      }
    }
    return null
  }

  override fun layout(sceneTransform: SceneContext,
                      @AndroidDpCoordinate l: Int,
                      @AndroidDpCoordinate t: Int,
                      @AndroidDpCoordinate r: Int,
                      @AndroidDpCoordinate b: Int): Boolean {
    var l = l
    var t = t
    var r = r
    var b = b
    val minWidth = 16
    val minHeight = 16
    if (r - l < minWidth) {
      val d = (minWidth - (r - l)) / 2
      l -= d
      r += d
    }
    if (b - t < minHeight) {
      val d = (minHeight - (b - t)) / 2
      t -= d
      b += d
    }
    myLeft = l.toFloat()
    myTop = t.toFloat()
    myRight = r.toFloat()
    myBottom = b.toFloat()
    return false
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, if (mIsOver) JBColor.yellow else JBColor.green)
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, JBColor.red)
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, JBColor.red)
    }
    targetSnapper.renderSnappedNotches(list, sceneContext, myComponent)
  }

  override fun isHittable(): Boolean {
    return if (myComponent.isSelected) {
      myComponent.canShowBaseline() || !myComponent.isDragging
    }
    else true
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  override fun getPreferenceLevel(): Int {
    return DRAG_LEVEL
  }

  override fun mouseDown(@NavCoordinate x: Int, @NavCoordinate y: Int) {
    if (myComponent.parent == null) {
      return
    }
    firstMouseX = x
    firstMouseY = y
    offsetX = x - myComponent.getDrawX(System.currentTimeMillis())
    offsetY = y - myComponent.getDrawY(System.currentTimeMillis())
    changedComponent = false
    targetSnapper.reset()
    targetSnapper.gatherNotches(myComponent)
    component.children.forEachIndexed { i, child -> childOffsets[i] = Point(x - child.drawX, y - child.drawY) }
  }

  override fun mouseDrag(@NavCoordinate x: Int, @NavCoordinate y: Int, closestTarget: List<Target>, context: SceneContext) {
    val parent = myComponent.parent ?: return

    myComponent.isDragging = true
    val dx = x - offsetX
    val dy = y - offsetY

    if (dx < parent.drawX || dx + myComponent.drawWidth > parent.drawX + parent.drawWidth) {
      return
    }

    if (dy < parent.drawY || dy + myComponent.drawHeight > parent.drawY + parent.drawHeight) {
      return
    }

    myComponent.setPosition(dx, dy)
    changedComponent = true

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
      if ((x - firstMouseX).absoluteValue <= 1 && (y - firstMouseY).absoluteValue <= 1) {
        return
      }
      (myComponent.scene.sceneManager as NavSceneManager).save(listOf(myComponent))
    }
    if (changedComponent) {
      myComponent.scene.markNeedsLayout(Scene.IMMEDIATE_LAYOUT)
    }
  }

  /**
   * Reset the status when the dragging is canceled.
   */
  override fun mouseCancel() {
    val originalX = firstMouseX - offsetX
    val originalY = firstMouseY - offsetY
    myComponent.setPosition(originalX, originalY)

    // rollback the transaction. The value may be temporarily changed by live rendering.
    val component = myComponent.getAuthoritativeNlComponent()
    val transaction = component.startAttributeTransaction()
    transaction.rollback()
    component.fireLiveChangeEvent()
    myComponent.isDragging = false
    targetSnapper.reset()
    changedComponent = false
    myComponent.scene.markNeedsLayout(Scene.IMMEDIATE_LAYOUT)
  }

  override fun getMouseCursor(@JdkConstants.InputEventMask modifiersEx: Int): Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
