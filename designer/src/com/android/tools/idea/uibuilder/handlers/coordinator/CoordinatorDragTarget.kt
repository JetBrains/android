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
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.AttributesTransaction
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.DragBaseTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.model.viewHandler
import com.intellij.openapi.util.text.StringUtil
import java.awt.Color
import java.util.*

private const val DEBUG: Boolean = false

/**
 * Drag target for CoordinatorLayout
 */
class CoordinatorDragTarget : DragBaseTarget() {
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
    @Suppress("ConstantConditionIf")
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

  private fun restoreAttributes(transaction: AttributesTransaction) {
    for (attribute in myAttributes) {
      val value = myOriginalAttributes[attribute]
      transaction.setAttribute(SdkConstants.AUTO_URI, attribute, value)
    }
    transaction.apply()
  }

  private fun updateInteractionState(interactionState : CoordinatorLayoutHandler.InteractionState) {
    val handler = myComponent.parent?.nlComponent?.viewHandler ?: return
    if (handler is CoordinatorLayoutHandler) {
      handler.interactionState = interactionState
      myComponent.parent?.updateTargets()
    }
  }

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    super.mouseDown(x, y)
    if (myComponent.parent == null) {
      return
    }
    updateInteractionState(CoordinatorLayoutHandler.InteractionState.DRAGGING)
    rememberAttributes()
  }

  override fun mouseDrag(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTarget: List<Target>?) {
    if (myComponent.parent == null) {
      return
    }
    mySnapTarget?.setMouseHovered(false)
    mySnapTarget = null
    val snapTarget : Target? = closestTarget?.firstOrNull { it is CoordinatorSnapTarget }
    if (snapTarget is CoordinatorSnapTarget) {
      mySnapTarget = snapTarget
      snapTarget.setMouseHovered(true)
    }
    myComponent.isDragging = true
    myComponent.setPosition(x - myOffsetX, y - myOffsetY, false)
    myComponent.scene.repaint()
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTargets: List<Target>?) {
    super.mouseRelease(x, y, closestTargets)
    if (myChangedComponent) {
      myComponent.scene.needsLayout(Scene.IMMEDIATE_LAYOUT)
    }
    updateInteractionState(CoordinatorLayoutHandler.InteractionState.NORMAL)
  }

  override fun updateAttributes(attributes: AttributesTransaction, x: Int, y: Int) {
    if (mySnapTarget != null) {
      mySnapTarget!!.snap(attributes)
    }
    else {
      restoreAttributes(attributes)
    }
  }

  override fun cancel() {
    super.cancel()
    myComponent.setPosition(myFirstMouseX - myOffsetX, myFirstMouseY - myOffsetY)
    myComponent.scene.repaint()
  }

  fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, component: NlComponent) {
    myComponent.isDragging = false
    if (myComponent.parent != null) {
      val attributes = component.startAttributeTransaction()
      updateAttributes(attributes, x, y)
      attributes.apply()
      if (Math.abs(x - myFirstMouseX) > 1 || Math.abs(y - myFirstMouseY) > 1) {
        NlWriteCommandAction.run(component, "Dragged " + StringUtil.getShortName(component.tagName), { attributes.commit() })
      }
    }
    if (myChangedComponent) {
      myComponent.scene.needsLayout(Scene.IMMEDIATE_LAYOUT)
    }
    updateInteractionState(CoordinatorLayoutHandler.InteractionState.NORMAL)
  }
}
