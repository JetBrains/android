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

import com.android.SdkConstants.*
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.AttributesTransaction
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.AnchorTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnchor
import com.android.tools.idea.uibuilder.model.TextDirection
import com.google.common.collect.ImmutableList

private const val DRAGGING_ANCHOR = "dragging_anchor"

/**
 * Target offers the anchors in RelativeLayout.
 */
class RelativeAnchorTarget(type: Type, private val isParent: Boolean) : AnchorTarget(type) {

  /**
   * If this Anchor is dragging.
   * Note that this doesn't mean the associated component is dragging. This means Anchor itself is dragging.
   */
  private var isDragging = false
  private var rtlDirection = TextDirection.LEFT_TO_RIGHT
  /**
   * Used to record the aligned component ids.
   */
  private val alignedComponentIds = mutableSetOf<String>()

  override fun setComponent(component: SceneComponent) {
    super.setComponent(component)
    rtlDirection = if (component.scene.isInRTL) TextDirection.RIGHT_TO_LEFT else TextDirection.LEFT_TO_RIGHT

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

      if (isDragging) {
        list.addConnection(sceneContext, centerX, centerY, myLastX.toFloat(), myLastY.toFloat(), type.ordinal)
      }
    }
  }

  override fun getDrawMode(): DrawAnchor.Mode {
    return if (!myComponent.isSelected) {
      val canConnectToMe = isConnectible(myComponent.scene.filterType)
      if (canConnectToMe) DrawAnchor.Mode.CAN_CONNECT else DrawAnchor.Mode.DO_NOT_DRAW
    } else if (mIsOver) {
      DrawAnchor.Mode.OVER
    } else if (!isConnected) {
      DrawAnchor.Mode.NORMAL
    } else {
      DrawAnchor.Mode.NORMAL
    }
  }

  /**
   * If this target can become the destination of current dragging anchor.
   * Returns true if this is not dragging but aligning to the dragging component.
   */
  fun isConnectible(filterType: Scene.FilterType): Boolean {
    val draggingAnchorTarget = getDraggingAnchor() ?: return false
    if (isAlignedTo(draggingAnchorTarget.myComponent)) {
      return false
    }
    return when (filterType) {
      Scene.FilterType.HORIZONTAL_ANCHOR -> type == Type.LEFT || type == Type.RIGHT
      Scene.FilterType.VERTICAL_ANCHOR -> type == Type.TOP || type == Type.BOTTOM
      else -> false
    }
  }

  fun getPreferredFilterType() = when (type) {
    Type.LEFT, Type.RIGHT -> Scene.FilterType.HORIZONTAL_ANCHOR
    Type.TOP, Type.BOTTOM -> Scene.FilterType.VERTICAL_ANCHOR
    Type.BASELINE -> Scene.FilterType.BASELINE_ANCHOR
  }

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    super.mouseDown(x, y)
    if (isParent) {
      return
    }
    myComponent.parent?.setExpandTargetArea(true)

    updateDraggingAnchor(this)

    isDragging = false
    myComponent.scene.needsLayout(Scene.ANIMATED_LAYOUT)
  }

  override fun mouseDrag(x: Int, y: Int, ignored: MutableList<Target>) {
    super.mouseDrag(x, y, ignored)
    if (isParent) {
      return
    }

    isDragging = true
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTargets: List<Target>) {
    super.mouseRelease(x, y, closestTargets)
    if (isParent) {
      return
    }

    val parent = myComponent.parent
    if (parent != null) {
      parent.setExpandTargetArea(false)
      val transactions = myComponent.authoritativeNlComponent.startAttributeTransaction()
      if (this in closestTargets) {
        handleConstraintDeletion(transactions)
      }
      else {
        closestTargets.filterIsInstance<RelativeAnchorTarget>().firstOrNull()?.let { handleConstraintConnection(transactions, it) }
      }
    }

    updateDraggingAnchor(null)
    isDragging = false
  }

  private fun handleConstraintDeletion(attributesTransaction: AttributesTransaction) {
    val nlComponent = myComponent.authoritativeNlComponent
    clearAssociatedAttribute(attributesTransaction)
    val message = "Remove constraint from ${myType.name} of ${nlComponent.tagName}"
    NlWriteCommandAction.run(nlComponent, message) { attributesTransaction.commit() }
    myComponent.scene.needsLayout(Scene.ANIMATED_LAYOUT)
    updateAlignedComponentIds()
  }

  private fun handleConstraintConnection(attributesTransaction: AttributesTransaction, target: RelativeAnchorTarget) {
    val nlComponent = myComponent.authoritativeNlComponent
    connectTo(target, attributesTransaction)
    val message = "Create constraint between ${myType.name} of ${nlComponent.tagName} " +
        "and ${target.myType} of ${target.myComponent.authoritativeNlComponent.tagName}"
    NlWriteCommandAction.run(nlComponent, message) { attributesTransaction.commit() }
    myComponent.scene.needsLayout(Scene.ANIMATED_LAYOUT)
    updateAlignedComponentIds()
  }

  private fun updateDraggingAnchor(anchorTarget: RelativeAnchorTarget?) {
    // Only put the dragging component's information to root component to improve the performance.
    myComponent.scene.root?.authoritativeNlComponent?.putClientProperty(DRAGGING_ANCHOR, anchorTarget)
  }

  private fun getDraggingAnchor() =
    myComponent.scene.root?.authoritativeNlComponent?.getClientProperty(DRAGGING_ANCHOR) as RelativeAnchorTarget?

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

  /**
   * Get the proper attributes for real layout file, which consider minimal SDK and target SDK.
   */
  private fun getProperAttributesForLayout(attribute: String?): List<String> {
    if (attribute == null) {
      return emptyList()
    }
    val builder = ImmutableList.Builder<String>()

    val viewEditor = myComponent.scene.sceneManager.viewEditor
    if (viewEditor.minSdkVersion.apiLevel < RtlSupportProcessor.RTL_TARGET_SDK_START) {
      builder.add(attribute)
    }

    if (viewEditor.targetSdkVersion.apiLevel >= RtlSupportProcessor.RTL_TARGET_SDK_START) {
      val rtlAttribute = when (attribute) {
        ATTR_LAYOUT_ALIGN_PARENT_LEFT -> rtlDirection.attrAlignParentLeft
        ATTR_LAYOUT_ALIGN_PARENT_RIGHT -> rtlDirection.attrAlignParentRight
        ATTR_LAYOUT_ALIGN_LEFT -> rtlDirection.attrLeft
        ATTR_LAYOUT_ALIGN_RIGHT -> rtlDirection.attrRight
        ATTR_LAYOUT_TO_LEFT_OF -> rtlDirection.attrLeftOf
        ATTR_LAYOUT_TO_RIGHT_OF -> rtlDirection.attrRightOf
        ATTR_LAYOUT_MARGIN_LEFT -> rtlDirection.attrMarginLeft
        ATTR_LAYOUT_MARGIN_RIGHT -> rtlDirection.attrMarginRight
        else -> attribute
      }
      builder.add(rtlAttribute)
    }

    return builder.build()
  }
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
