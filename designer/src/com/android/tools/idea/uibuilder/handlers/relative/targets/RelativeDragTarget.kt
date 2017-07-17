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
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate
import com.android.tools.idea.uibuilder.model.AttributesTransaction
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.target.DragBaseTarget
import com.android.tools.idea.uibuilder.scene.target.Target

/**
 * Target to handle the drag of RelativeLayout's children
 */
class RelativeDragTarget : DragBaseTarget() {
  private var myClosestX: RelativeParentTarget? = null
  private var myClosestY: RelativeParentTarget? = null

  private val ALIGNING_ATTRIBUTE_NAMES: Array<String> = arrayOf(
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

  override fun updateAttributes(attributes: AttributesTransaction, @AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    if (myComponent?.parent == null) return

    // TODO: Update correct relationship to other components
    clearAlignAttributes(attributes)

    // Update the relationships to parent
    when (myClosestX?.myType) {
      RelativeParentTarget.Type.LEFT -> attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START, "true")
      RelativeParentTarget.Type.RIGHT -> attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END, "true")
      RelativeParentTarget.Type.CENTER_HORIZONTAL -> attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL, "true")
      null -> updateMarginOnX(attributes, x)
      else -> assert(false) { "${myClosestX!!.myType} should not be the type of myClosestX" }
    }

    when (myClosestY?.myType) {
      RelativeParentTarget.Type.TOP -> attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP, "true")
      RelativeParentTarget.Type.BOTTOM -> attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, "true")
      RelativeParentTarget.Type.CENTER_VERTICAL -> {
        if (myClosestX?.myType == RelativeParentTarget.Type.CENTER_HORIZONTAL) {
          // CenterVertical and CenterHorizontal are true, use layout_center_in_parent instead.
          attributes.removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL)
          attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT, "true")
        }
        else {
          attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL, "true")
        }
      }
      null -> updateMarginOnY(attributes, y)
      else -> assert(false) { "${myClosestY!!.myType} should not be the type of myClosestY" }
    }

    myComponent.updateTargets(false)
  }

  /**
   * Calculate the align and margin on x axis.
   * Note that if the x already has Notch on X axis then don't need to call this function.
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
   * Note that if the y already has Notch on Y axis then don't need to call this function
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

  private fun clearAlignAttributes(attributes: AttributesTransaction) {
    for (name in ALIGNING_ATTRIBUTE_NAMES) {
      attributes.removeAndroidAttribute(name)
    }
  }

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
    // In RelativeLayout we need to use the edges of component as conditionThe rather than current dragging point (the mouse point)
    // So we ignore closestTargets here
    val sceneParent = myComponent?.parent ?: return
    val snapper = targetNotchSnapper
    myComponent.isDragging = true

    val dragX = x - myOffsetX
    val dragY = y - myOffsetY
    var nx = Math.max(sceneParent.drawX, Math.min(dragX, sceneParent.drawX + sceneParent.drawWidth - myComponent.drawWidth))
    var ny = Math.max(sceneParent.drawY, Math.min(dragY, sceneParent.drawY + sceneParent.drawHeight - myComponent.drawHeight))
    nx = snapper.trySnapX(nx)
    ny = snapper.trySnapY(ny)
    myComponent.setPosition(nx, ny, false)

    val closestXTarget = snapper.snappedNotchX?.target
    val closestYTarget = snapper.snappedNotchY?.target
    if (myClosestX !== closestXTarget) {
      myClosestX?.myIsHighlight = false

      // TODO: Modify here when supporting relationship to other components
      myClosestX = closestXTarget as RelativeParentTarget?
      myClosestX?.myIsHighlight = true
    }

    if (myClosestY !== closestYTarget) {
      myClosestY?.myIsHighlight = false
      // TODO: Modify here when supporting relationship to other components
      myClosestY = closestYTarget as RelativeParentTarget?
      myClosestY?.myIsHighlight = true
    }
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTarget: List<Target>?) {
    super.mouseRelease(x, y, closestTarget)
    myComponent.setModelUpdateAuthorized(true)
    myComponent.isDragging = false

    myClosestX?.myIsHighlight = false
    myClosestY?.myIsHighlight = false
  }
}
