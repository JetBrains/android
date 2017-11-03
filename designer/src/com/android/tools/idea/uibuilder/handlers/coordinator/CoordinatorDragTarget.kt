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
package com.android.tools.idea.uibuilder.handlers.coordinator

import com.android.SdkConstants
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.scene.Scene.ANIMATED_LAYOUT
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.BaseTarget
import com.android.tools.idea.common.scene.target.Target
import java.awt.Color
import java.util.*

/**
 * Drag target for CoordinatorLayout
 */
class CoordinatorDragTarget : BaseTarget() {

  private val DEBUG: Boolean = false

  private var myFirstMouseX: Int = 0
  private var myFirstMouseY: Int = 0

  private var  myOffsetX: Int = 0
  private var  myOffsetY: Int = 0

  private var myChangedComponent: Boolean = false
  private var mySnapTarget: CoordinatorSnapTarget? = null

  override fun getPreferenceLevel(): Int = Target.DRAG_LEVEL

  private var myAttributes = listOf(SdkConstants.ATTR_LAYOUT_ANCHOR, SdkConstants.ATTR_LAYOUT_ANCHOR_GRAVITY)
  private var myOriginalAttributes = HashMap<String, String>()

  override fun layout(context: SceneContext, left: Int, top: Int, right: Int, bottom: Int): Boolean {
    val minWidth = 16
    val minHeight = 16
    var l = left
    var t = top
    var r = right
    var b = bottom
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

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    if (DEBUG) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, if (mIsOver) Color.yellow else Color.green)
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, Color.red)
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, Color.red)
    }
  }

  private fun rememberAttributes() {
    myOriginalAttributes.clear()
    for (attribute in myAttributes) {
      val value = myComponent.nlComponent.getLiveAttribute(SdkConstants.AUTO_URI, attribute)
      if (value != null) {
        myOriginalAttributes.put(attribute, value)
      }
    }
  }

  private fun restoreAttributes() {
    val transaction = myComponent.nlComponent.startAttributeTransaction()
    for (attribute in myAttributes) {
      val value = myOriginalAttributes[attribute]
      transaction.setAttribute(SdkConstants.AUTO_URI, attribute, value)
    }
    transaction.apply()
  }

  private fun updateInteractionState(interactionState : CoordinatorLayoutHandler.InteractionState) {
    val provider = myComponent.parent?.targetProvider
    if (provider is CoordinatorLayoutHandler) {
      provider.interactionState = interactionState
      myComponent.parent?.updateTargets(true)
    }
  }

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    if (myComponent.parent == null) {
      return
    }
    myFirstMouseX = x
    myFirstMouseY = y
    myOffsetX = x - myComponent.getDrawX(System.currentTimeMillis())
    myOffsetY = y - myComponent.getDrawY(System.currentTimeMillis())
    myChangedComponent = false
    updateInteractionState(CoordinatorLayoutHandler.InteractionState.DRAGGING)
    rememberAttributes()
  }

  override fun mouseDrag(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTarget: List<Target>?) {
    if (myComponent.parent == null) {
      return
    }
    mySnapTarget = null
    val snapTarget : Target? = closestTarget?.filter { it is CoordinatorSnapTarget }?.firstOrNull()
    snapTarget?.setOver(true)
    if (snapTarget is CoordinatorSnapTarget) {
      mySnapTarget = snapTarget
    }
    myComponent.isDragging = true
    myComponent.setPosition(x - myOffsetX, y - myOffsetY, false)
    myComponent.scene.repaint()
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTarget: List<Target>?) {
    updateInteractionState(CoordinatorLayoutHandler.InteractionState.NORMAL)
    if (!myComponent.isDragging) {
      return
    }
    myComponent.isDragging = false
    if (myComponent.parent == null) {
      return
    }
    if (mySnapTarget == null) {
      restoreAttributes()
    } else {
      val attributes = myComponent.nlComponent.startAttributeTransaction()
      mySnapTarget!!.snap(attributes)
    }
    myComponent.scene.needsLayout(ANIMATED_LAYOUT)
  }

  override fun setComponentSelection(selection: Boolean) {

  }

}
