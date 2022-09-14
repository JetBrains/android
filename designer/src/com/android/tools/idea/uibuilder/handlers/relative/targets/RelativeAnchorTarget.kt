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
package com.android.tools.idea.uibuilder.handlers.relative.targets

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_ABOVE
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP
import com.android.SdkConstants.ATTR_LAYOUT_BELOW
import com.android.SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL
import com.android.SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT
import com.android.SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP
import com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF
import com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF
import com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF
import com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.VALUE_N_DP
import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.AttributesTransaction
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.AnchorTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnchor

/**
 * Target offers the anchors in RelativeLayout.
 */
class RelativeAnchorTarget(type: Type, private val isParent: Boolean) : AnchorTarget(type, isParent) {

  /**
   * Used to record the aligned component ids.
   */
  private val alignedComponentIds = mutableSetOf<String>()

  override fun setComponent(component: SceneComponent) {
    super.setComponent(component)
    updateAlignedComponentIds()
  }

  private fun updateAlignedComponentIds() {
    alignedComponentIds.clear()
    val nlComponent = component.authoritativeNlComponent

    SIBLING_ALIGNMENT_ATTRIBUTES
      .mapNotNull { nlComponent.getLiveAttribute(ANDROID_URI, it) }
      .mapNotNull { NlComponent.extractId(it) }
      .toCollection(alignedComponentIds)
  }

  override fun isConnected() = findRelatedAlignmentAttributes()
    .flatMap { getProperAttributesForLayout(it) }
    .any { myComponent.authoritativeNlComponent.getLiveAttribute(ANDROID_URI, it) != null }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    if (!isParent) {
      super.render(list, sceneContext)

      if (myIsDragging) {
        list.addConnection(sceneContext, centerX, centerY, myLastX.toFloat(), myLastY.toFloat(), type.ordinal)
      }
    }
  }

  override fun isEnabled(): Boolean {
    if (!super.isEnabled()) {
      return false
    }
    if (myComponent.scene.selection.size > 1) {
      return false
    }
    if (myComponent.isSelected) {
      return !myComponent.isDragging
    }

    val filterType = myComponent.scene.filterType
    if (isConnectible(filterType) || filterType == Scene.FilterType.ALL) {
      return true
    }
    return false
  }

  override fun getDrawMode(): DrawAnchor.Mode {
    return if (!myComponent.isSelected) {
      val canConnectToMe = isConnectible(myComponent.scene.filterType)
      if (canConnectToMe) {
        if (mIsOver) DrawAnchor.Mode.OVER else DrawAnchor.Mode.NORMAL
      } else DrawAnchor.Mode.DO_NOT_DRAW
    } else if (mIsOver) {
       if (isConnected) DrawAnchor.Mode.DELETE else DrawAnchor.Mode.OVER
    } else {
      DrawAnchor.Mode.NORMAL
    }
  }

  override fun isConnectible(dest: AnchorTarget): Boolean {
    if (dest !is RelativeAnchorTarget) {
      return false
    }
    val sameDirection = when (myType) {
      Type.LEFT, Type.RIGHT -> dest.type == Type.LEFT || dest.type == Type.RIGHT
      Type.TOP, Type.BOTTOM -> dest.type == Type.TOP || dest.type == Type.BOTTOM
      Type.BASELINE -> dest.type == Type.BASELINE
    }
    if (!sameDirection) {
      return false
    }
    return if (dest.isEdge) component.parent === dest.component else component.parent === dest.component.parent
  }

  /**
   * If this target can become the destination of current dragging anchor.
   * Returns true if this is not dragging but aligning to the dragging component.
   */
  private fun isConnectible(filterType: Scene.FilterType): Boolean {
    val draggingAnchorTarget = myComponent.scene.interactingTarget as? RelativeAnchorTarget ?: return false
    if (isAlignedTo(draggingAnchorTarget.myComponent)) {
      return false
    }
    return when (filterType) {
      Scene.FilterType.HORIZONTAL_ANCHOR -> type == Type.LEFT || type == Type.RIGHT
      Scene.FilterType.VERTICAL_ANCHOR -> type == Type.TOP || type == Type.BOTTOM
      else -> false
    }
  }

  private fun getPreferredFilterType() = when (type) {
    Type.LEFT, Type.RIGHT -> Scene.FilterType.HORIZONTAL_ANCHOR
    Type.TOP, Type.BOTTOM -> Scene.FilterType.VERTICAL_ANCHOR
    Type.BASELINE -> Scene.FilterType.BASELINE_ANCHOR
  }

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    super.mouseDown(x, y)
    component.scene.filterType = getPreferredFilterType()

    if (isParent) {
      return
    }
    myIsDragging = false
    myComponent.scene.markNeedsLayout(Scene.ANIMATED_LAYOUT)
  }

  override fun mouseDrag(x: Int, y: Int, ignored: List<Target>, sceneContext: SceneContext) {
    super.mouseDrag(x, y, ignored, sceneContext)
    if (isParent) {
      return
    }

    myIsDragging = true
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTargets: List<Target>) {
    super.mouseRelease(x, y, closestTargets)
    if (isParent) {
      return
    }

    val parent = myComponent.parent
    if (parent != null) {
      val transactions = myComponent.authoritativeNlComponent.startAttributeTransaction()
      if (this in closestTargets) {
        handleConstraintDeletion(transactions)
      }
      else {
        closestTargets.filterIsInstance<RelativeAnchorTarget>().firstOrNull()?.let { handleConstraintConnection(transactions, it) }
      }
    }

    myIsDragging = false
  }

  private fun handleConstraintDeletion(attributesTransaction: AttributesTransaction) {
    val nlComponent = myComponent.authoritativeNlComponent
    clearAssociatedAttribute(attributesTransaction)
    val message = "Remove constraint from ${myType.name} of ${nlComponent.tagName}"
    NlWriteCommandActionUtil.run(nlComponent, message) { attributesTransaction.commit() }
    myComponent.scene.markNeedsLayout(Scene.ANIMATED_LAYOUT)
    updateAlignedComponentIds()
  }

  private fun handleConstraintConnection(attributesTransaction: AttributesTransaction, target: RelativeAnchorTarget) {
    val nlComponent = myComponent.authoritativeNlComponent
    connectTo(target, attributesTransaction)
    val message = "Create constraint between ${myType.name} of ${nlComponent.tagName} " +
        "and ${target.myType} of ${target.myComponent.authoritativeNlComponent.tagName}"
    NlWriteCommandActionUtil.run(nlComponent, message) { attributesTransaction.commit() }
    myComponent.scene.markNeedsLayout(Scene.ANIMATED_LAYOUT)
    updateAlignedComponentIds()
  }

  private fun isAlignedTo(other: SceneComponent) = other.id in alignedComponentIds

  private fun connectTo(other: RelativeAnchorTarget, transaction: AttributesTransaction) {
    val typePair = Pair(type, other.type)
    val alignmentAttribute: String
    val alignmentValue: String

    if (other.myComponent == myComponent.parent) {
      alignmentAttribute = PARENT_CONNECTION_TYPES[typePair] ?: return
      alignmentValue = VALUE_TRUE
    }
    else {
      alignmentAttribute = SIBLING_CONNECTION_TYPES[typePair] ?: return
      // TODO: handle no id case?
      alignmentValue = NEW_ID_PREFIX + other.myComponent.id
    }

    clearAssociatedAttribute(transaction)

    getProperAttributesForLayout(alignmentAttribute).forEach { transaction.setAndroidAttribute(it, alignmentValue) }

    val (marginAttribute, marginValue) = calculateMargin(other, alignmentAttribute) ?: return
    getProperAttributesForLayout(marginAttribute).forEach { transaction.setAndroidAttribute(it, marginValue) }
  }

  /**
   * Remove the attributes related to this target. These include alignment and margin attributes.
   */
  private fun clearAssociatedAttribute(transaction: AttributesTransaction) {
    // Remove alignment attribute(s)
    findRelatedAlignmentAttributes()
      .flatMap { getProperAttributesForLayout(it) }
      .forEach { transaction.removeAndroidAttribute(it) }
    // Remove margin attribute(s)
    getProperAttributesForLayout(findRelatedMarginAttribute()).forEach { transaction.removeAndroidAttribute(it) }
  }

  private fun findRelatedAlignmentAttributes() = when (type) {
    Type.TOP -> arrayOf(
      ATTR_LAYOUT_CENTER_IN_PARENT,
      ATTR_LAYOUT_CENTER_VERTICAL,
      ATTR_LAYOUT_ALIGN_PARENT_TOP,
      ATTR_LAYOUT_ALIGN_TOP,
      ATTR_LAYOUT_BELOW
    )
    Type.LEFT -> arrayOf(
      ATTR_LAYOUT_CENTER_IN_PARENT,
      ATTR_LAYOUT_CENTER_HORIZONTAL,
      ATTR_LAYOUT_ALIGN_PARENT_LEFT,
      ATTR_LAYOUT_ALIGN_LEFT,
      ATTR_LAYOUT_TO_RIGHT_OF
    )
    Type.BOTTOM -> arrayOf(
      ATTR_LAYOUT_CENTER_IN_PARENT,
      ATTR_LAYOUT_CENTER_VERTICAL,
      ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
      ATTR_LAYOUT_ALIGN_BOTTOM,
      ATTR_LAYOUT_ABOVE
    )
    Type.RIGHT -> arrayOf(
      ATTR_LAYOUT_CENTER_IN_PARENT,
      ATTR_LAYOUT_CENTER_HORIZONTAL,
      ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
      ATTR_LAYOUT_ALIGN_RIGHT,
      ATTR_LAYOUT_TO_LEFT_OF
    )
    Type.BASELINE -> arrayOf(ATTR_LAYOUT_ALIGN_BASELINE)
  }

  private fun findRelatedMarginAttribute() = when (type) {
    Type.TOP -> ATTR_LAYOUT_MARGIN_TOP
    Type.LEFT -> ATTR_LAYOUT_MARGIN_LEFT
    Type.BOTTOM -> ATTR_LAYOUT_MARGIN_BOTTOM
    Type.RIGHT -> ATTR_LAYOUT_MARGIN_RIGHT
    Type.BASELINE -> null
  }

  private fun getEdgeCoordinate() = when (type) {
    Type.TOP -> myComponent.drawY
    Type.LEFT -> myComponent.drawX
    Type.BOTTOM -> myComponent.drawY + myComponent.drawHeight
    Type.RIGHT -> myComponent.drawX + myComponent.drawWidth
    Type.BASELINE -> myComponent.drawY + myComponent.baseline
  }

  private fun calculateMargin(other: RelativeAnchorTarget, alignAttribute: String): Pair<String, String>? {
    val marginAttribute = when (alignAttribute) {
      ATTR_LAYOUT_ALIGN_TOP, ATTR_LAYOUT_BELOW, ATTR_LAYOUT_ALIGN_PARENT_TOP -> ATTR_LAYOUT_MARGIN_TOP
      ATTR_LAYOUT_ALIGN_LEFT, ATTR_LAYOUT_TO_RIGHT_OF, ATTR_LAYOUT_ALIGN_PARENT_LEFT -> ATTR_LAYOUT_MARGIN_LEFT
      ATTR_LAYOUT_ALIGN_BOTTOM, ATTR_LAYOUT_ABOVE, ATTR_LAYOUT_ALIGN_PARENT_BOTTOM -> ATTR_LAYOUT_MARGIN_BOTTOM
      ATTR_LAYOUT_ALIGN_RIGHT, ATTR_LAYOUT_TO_LEFT_OF, ATTR_LAYOUT_ALIGN_PARENT_RIGHT -> ATTR_LAYOUT_MARGIN_RIGHT
      else -> return null
    }

    val marginValue = when (alignAttribute) {
      ATTR_LAYOUT_ALIGN_PARENT_TOP, ATTR_LAYOUT_ALIGN_PARENT_LEFT,
      ATTR_LAYOUT_ALIGN_TOP, ATTR_LAYOUT_ALIGN_LEFT,
      ATTR_LAYOUT_BELOW, ATTR_LAYOUT_TO_RIGHT_OF -> getEdgeCoordinate() - other.getEdgeCoordinate()
      else -> other.getEdgeCoordinate() - getEdgeCoordinate()
    }

    return Pair(marginAttribute, String.format(VALUE_N_DP, marginValue))
  }

  private fun getProperAttributesForLayout(attribute: String?) = getProperAttributesForLayout(myComponent, attribute)
}

private val SIBLING_ALIGNMENT_ATTRIBUTES = listOf(
  ATTR_LAYOUT_ALIGN_TOP,
  ATTR_LAYOUT_ALIGN_LEFT,
  ATTR_LAYOUT_ALIGN_BOTTOM,
  ATTR_LAYOUT_ALIGN_RIGHT,
  ATTR_LAYOUT_BELOW,
  ATTR_LAYOUT_ABOVE,
  ATTR_LAYOUT_TO_LEFT_OF,
  ATTR_LAYOUT_TO_RIGHT_OF,
  ATTR_LAYOUT_ALIGN_START,
  ATTR_LAYOUT_ALIGN_END,
  ATTR_LAYOUT_TO_START_OF,
  ATTR_LAYOUT_TO_END_OF
)

private val PARENT_CONNECTION_TYPES = mapOf(
  Pair(AnchorTarget.Type.TOP, AnchorTarget.Type.TOP) to ATTR_LAYOUT_ALIGN_PARENT_TOP,
  Pair(AnchorTarget.Type.LEFT, AnchorTarget.Type.LEFT) to ATTR_LAYOUT_ALIGN_PARENT_LEFT,
  Pair(AnchorTarget.Type.BOTTOM, AnchorTarget.Type.BOTTOM) to ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
  Pair(AnchorTarget.Type.RIGHT, AnchorTarget.Type.RIGHT) to ATTR_LAYOUT_ALIGN_PARENT_RIGHT
)

private val SIBLING_CONNECTION_TYPES = mapOf(
  Pair(AnchorTarget.Type.TOP, AnchorTarget.Type.TOP) to ATTR_LAYOUT_ALIGN_TOP,
  Pair(AnchorTarget.Type.TOP, AnchorTarget.Type.BOTTOM) to ATTR_LAYOUT_BELOW,
  Pair(AnchorTarget.Type.BOTTOM, AnchorTarget.Type.TOP) to ATTR_LAYOUT_ABOVE,
  Pair(AnchorTarget.Type.BOTTOM, AnchorTarget.Type.BOTTOM) to ATTR_LAYOUT_ALIGN_BOTTOM,
  Pair(AnchorTarget.Type.LEFT, AnchorTarget.Type.LEFT) to ATTR_LAYOUT_ALIGN_LEFT,
  Pair(AnchorTarget.Type.LEFT, AnchorTarget.Type.RIGHT) to ATTR_LAYOUT_TO_RIGHT_OF,
  Pair(AnchorTarget.Type.RIGHT, AnchorTarget.Type.LEFT) to ATTR_LAYOUT_TO_LEFT_OF,
  Pair(AnchorTarget.Type.RIGHT, AnchorTarget.Type.RIGHT) to ATTR_LAYOUT_ALIGN_RIGHT
)
