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
package com.android.tools.idea.uibuilder.handlers.relative.targets

import com.android.SdkConstants
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.AttributesTransaction
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.target.DragBaseTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.util.text.StringUtil
import java.awt.Point

/**
 * Target to handle the drag of RelativeLayout's children
 */
class RelativeDragTarget : DragBaseTarget() {
  private var myTargetX: BaseRelativeTarget? = null
  private var myTargetY: BaseRelativeTarget? = null

  private val mySnappedPoint = Point()

  /**
   * Calculate the align and margin on x axis.
   * This functions should only be used when there is no Notch on X axis
   */
  private fun updateMarginOnX(attributes: AttributesTransaction, @AndroidDpCoordinate x: Int) {
    val parent = myComponent.parent!!
    if (myComponent.centerX < parent.drawX + parent.drawWidth / 2) {
      // near to left side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START, "true")
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_START,
          String.format(SdkConstants.VALUE_N_DP, Math.max(x - parent.drawX, 0)))
    }
    else {
      // near to right side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END, "true")
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_END,
          String.format(SdkConstants.VALUE_N_DP, Math.max(parent.drawWidth - (x - parent.drawX) - myComponent.drawWidth, 0)))
    }
  }

  /**
   * Calculate the align and margin on y axis.
   * This functions should only be used when there is no Notch on Y axis
   */
  private fun updateMarginOnY(attributes: AttributesTransaction, @AndroidDpCoordinate y: Int) {
    val parent = myComponent.parent!!
    if (myComponent.centerY < parent.drawY + parent.drawHeight / 2) {
      // near to top side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP, "true")
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
          String.format(SdkConstants.VALUE_N_DP, Math.max(y - parent.drawY, 0)))
    }
    else {
      // near to bottom side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, "true")
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
          String.format(SdkConstants.VALUE_N_DP, Math.max(parent.drawHeight - (y - parent.drawY) - myComponent.drawHeight, 0)))
    }
  }

  private fun clearAlignAttributes(attributes: AttributesTransaction) =
      ALIGNING_ATTRIBUTE_NAMES.map { attributes.removeAndroidAttribute(it) }

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    val parent = myComponent?.parent ?: return
    // Need to call this to update the targetsProvider when moving from one layout to another during a drag
    // but we should have a better scenario to recreate the targets
    (parent.scene.sceneManager as LayoutlibSceneManager).addTargets(myComponent)
    parent.updateTargets(true)

    super.mouseDown(x, y)
    myComponent.setModelUpdateAuthorized(false)
  }

  override fun mouseDrag(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTargets: List<Target>?) {
    myComponent.isDragging = true
    trySnap(x, y)
    myComponent.setPosition(mySnappedPoint.x, mySnappedPoint.y, false)

    myTargetX?.myIsHighlight = false
    myTargetX = targetNotchSnapper.snappedNotchX?.target as BaseRelativeTarget?
    myTargetX?.myIsHighlight = true

    myTargetY?.myIsHighlight = false
    myTargetY = targetNotchSnapper.snappedNotchY?.target as BaseRelativeTarget?
    myTargetY?.myIsHighlight = true
  }

  private fun trySnap(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    val sceneParent = myComponent?.parent ?: return

    val dragX = x - myOffsetX
    val dragY = y - myOffsetY
    val nx = Math.max(sceneParent.drawX, Math.min(dragX, sceneParent.drawX + sceneParent.drawWidth - myComponent.drawWidth))
    val ny = Math.max(sceneParent.drawY, Math.min(dragY, sceneParent.drawY + sceneParent.drawHeight - myComponent.drawHeight))
    mySnappedPoint.x = targetNotchSnapper.trySnapX(nx)
    mySnappedPoint.y = targetNotchSnapper.trySnapY(ny)
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTarget: List<Target>?) {
    if (!myComponent.isDragging) return

    myComponent.isDragging = false

    if (myComponent.parent != null) {
      val component = myComponent.authoritativeNlComponent
      val attributes = component.startAttributeTransaction()
      trySnap(x, y)
      myComponent.setPosition(mySnappedPoint.x, mySnappedPoint.y, false)

      updateAttributes(attributes, mySnappedPoint.x, mySnappedPoint.y)
      attributes.apply()

      if (!(Math.abs(x - myFirstMouseX) <= 1 && Math.abs(y - myFirstMouseY) <= 1)) {
        NlWriteCommandAction.run(component, "Dragged " + StringUtil.getShortName(component.tagName), { attributes.commit() })
      }
    }

    myComponent.setModelUpdateAuthorized(true)
    myComponent.updateTargets(false)

    myTargetX?.myIsHighlight = false
    myTargetY?.myIsHighlight = false

    if (myChangedComponent) {
      myComponent.scene.needsLayout(Scene.IMMEDIATE_LAYOUT)
    }
  }

  override fun updateAttributes(attributes: AttributesTransaction, @AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    if (myComponent?.parent == null) return

    clearAlignAttributes(attributes)

    targetNotchSnapper.snappedNotchX?.applyAction(attributes) ?: updateMarginOnX(attributes, x)
    targetNotchSnapper.snappedNotchY?.applyAction(attributes) ?: updateMarginOnY(attributes, y)

    if (attributes.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL) == "true"
        && attributes.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL) == "true") {
      attributes.removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL)
      attributes.removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL)
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT, "true")
    }
  }
}

private val ALIGNING_ATTRIBUTE_NAMES = arrayOf(
    SdkConstants.ATTR_LAYOUT_MARGIN,
    SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
    SdkConstants.ATTR_LAYOUT_MARGIN_START,
    SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
    SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
    SdkConstants.ATTR_LAYOUT_MARGIN_END,
    SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
    SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL,
    SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL,
    SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT,
    SdkConstants.ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING,
    SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE,
    SdkConstants.ATTR_LAYOUT_ALIGN_LEFT,
    SdkConstants.ATTR_LAYOUT_ALIGN_START,
    SdkConstants.ATTR_LAYOUT_ALIGN_TOP,
    SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT,
    SdkConstants.ATTR_LAYOUT_ALIGN_END,
    SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_ABOVE,
    SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF,
    SdkConstants.ATTR_LAYOUT_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_BELOW
)