/*
 * Copyright (C) 2017, 2018 The Android Open Source Project
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
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.AttributesTransaction
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.target.DragBaseTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.api.actions.ToggleAutoConnectAction
import com.android.tools.idea.uibuilder.scene.target.TargetSnapper
import com.intellij.openapi.util.text.StringUtil

/**
 * Target to handle the drag of RelativeLayout's children
 */
class RelativeDragTarget : DragBaseTarget() {

  private lateinit var dropHandler: RelativeDropHandler

  override fun setComponent(component: SceneComponent) {
    super.setComponent(component)
    dropHandler = RelativeDropHandler(myComponent)
  }

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    if (myComponent.parent == null) {
      return
    }
    super.mouseDown(x, y)
  }

  override fun mouseDrag(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTargets: List<Target>, ignored: SceneContext) {
    myComponent.isDragging = true

    val attributes = myComponent.authoritativeNlComponent.startAttributeTransaction()
    updateAttributes(attributes, x, y)
    attributes.apply()

    myChangedComponent = true
  }

  override fun updateAttributes(attributes: NlAttributesHolder, x: Int, y: Int) {
    dropHandler.updateAttributes(attributes, x - myOffsetX, y - myOffsetY)
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTarget: List<Target>) {
    if (!myComponent.isDragging) return
    myComponent.isDragging = false

    if (myComponent.parent != null) {
      val component = myComponent.authoritativeNlComponent

      val attributes = component.startAttributeTransaction()
      updateAttributes(attributes, x, y)
      dropHandler.hideHighlightTargets()
      attributes.apply()

      if (Math.abs(x - myFirstMouseX) > 1 || Math.abs(y - myFirstMouseY) > 1) {
        NlWriteCommandActionUtil.run(component, "Dragged " + StringUtil.getShortName(component.tagName), { attributes.commit() })
      }
    }

    if (myChangedComponent) {
      myComponent.scene.markNeedsLayout(Scene.IMMEDIATE_LAYOUT)
    }
  }
}

class RelativeDropHandler(val myComponent: SceneComponent) {

  private val targetSnapper = TargetSnapper()

  private var targetX: BaseRelativeTarget? = null
  private var targetY: BaseRelativeTarget? = null

  private var hasHorizontalConstraint = hasHorizontalConstraint(myComponent)
  private var hasVerticalConstraint = hasVerticalConstraint(myComponent)

  init {
    preProcessAttribute(myComponent.authoritativeNlComponent.startAttributeTransaction())
    targetSnapper.reset()
    targetSnapper.gatherNotches(myComponent)
  }

  private fun isAutoConnectionEnabled() = ToggleAutoConnectAction.isAutoconnectOn()

  /**
   * Split [SdkConstants.ATTR_LAYOUT_MARGIN] and [SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT] to multiple attributes.<br>
   * This makes the determine function simple while dragging.
   */
  private fun preProcessAttribute(attributes: AttributesTransaction) {
    // Ignore SdkConstants.ATTR_LAYOUT_MARGIN_START and SdkConstants.ATTR_LAYOUT_MARGIN_END here.
    // RTL attributes will be added (if needed) when attribute is set.
    val margin = attributes.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN)
    if (margin != null) {
      MARGINS_WITHOUT_RTL
        .flatMap { getProperAttributesForLayout(myComponent, it) }
        .forEach { attributes.setAndroidAttribute(it, margin) }
    }

    with (attributes) {
      if (getAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT) == SdkConstants.VALUE_TRUE) {
        removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT)
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL, SdkConstants.VALUE_TRUE)
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL, SdkConstants.VALUE_TRUE)
      }
    }
  }

  fun updateAttributes(attributes: NlAttributesHolder, @AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    val newX = processHorizontalAttributes(attributes, x)
    val newY = processVerticalAttributes(attributes, y)

    myComponent.setPosition(newX, newY)
  }

  // TODO: remove this function if possible
  fun hideHighlightTargets() {
    targetX?.myIsHighlight = false
    targetY?.myIsHighlight = false
  }

  /**
   * Update the horizontal constraints and return the position of component on X-axis.
   */
  @AndroidDpCoordinate
  private fun processHorizontalAttributes(attributes: NlAttributesHolder, @AndroidDpCoordinate x: Int): Int {
    val parent = myComponent.parent!!
    targetX?.myIsHighlight = false

    // Calculate horizontal constraint(s)
    if (hasHorizontalConstraint) {
      updateCurrentHorizontalConstraints(attributes)
      return x
    }

    clearHorizontalConstrains(attributes)
    attributes.removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL)
    val dx = Math.max(parent.drawLeft, Math.min(x, parent.drawRight - myComponent.drawWidth))
    if (!isAutoConnectionEnabled()) {
      return dx
    }

    val snappedX = targetSnapper.trySnapHorizontal(dx)
    if (snappedX.isPresent) {
      targetSnapper.applyNotches(attributes)
      targetX = targetSnapper.snappedHorizontalTarget as BaseRelativeTarget?
      targetX?.myIsHighlight = true

      targetSnapper.clearSnappedNotches()
      return snappedX.asInt
    }
    else {
      addHorizontalParentConstraints(attributes, dx)
      return dx
    }
  }

  /**
   * Calculate the align and margin on x axis.
   * This functions should only be used when there is no Notch on X axis
   */
  private fun addHorizontalParentConstraints(attributes: NlAttributesHolder, @AndroidDpCoordinate x: Int) {
    val parent = myComponent.parent!!
    if (x + myComponent.drawWidth / 2 < parent.drawCenterX) {
      // near to the left side
      getProperAttributesForLayout(myComponent, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT).forEach {
        attributes.setAndroidAttribute(it, SdkConstants.VALUE_TRUE)
      }
      getProperAttributesForLayout(myComponent, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT).forEach {
        attributes.setAndroidAttribute(it, maxOf(x - parent.drawLeft, 0).toDpString())
      }
    }
    else {
      // near to the right side
      getProperAttributesForLayout(myComponent, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT).forEach {
        attributes.setAndroidAttribute(it, SdkConstants.VALUE_TRUE)
      }
      getProperAttributesForLayout(myComponent, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT).forEach {
        attributes.setAndroidAttribute(it, maxOf(parent.drawRight - (x + myComponent.drawWidth), 0).toDpString())
      }
    }
  }

  /**
   * Update the exist horizontal constraints to make them match the position of SceneComponent
   */
  private fun updateCurrentHorizontalConstraints(attributes: NlAttributesHolder) {
    updateAlignAttributeIfNeed(attributes, LEFT_ALIGN_ATTRIBUTES, myComponent.drawLeft, LEFT_ATTRIBUTE_RULES)
    updateAlignAttributeIfNeed(attributes, RIGHT_ALIGN_ATTRIBUTES, myComponent.drawRight, RIGHT_ATTRIBUTE_RULES)

    val isRtl = myComponent.scene.isInRTL
    updateAlignAttributeIfNeed(attributes,
                               START_ALIGN_ATTRIBUTES,
                               if (isRtl)  myComponent.drawRight else myComponent.drawLeft,
                               if (isRtl) RTL_START_ATTRIBUTE_RULES else START_ATTRIBUTE_RULES)
    updateAlignAttributeIfNeed(attributes,
                               END_ALIGN_ATTRIBUTES,
                               if (isRtl) myComponent.drawLeft else myComponent.drawRight,
                               if (isRtl) RTL_END_ATTRIBUTE_RULES else END_ATTRIBUTE_RULES)
  }

  /**
   * Update the vertical constraints and return the position of component on Y-axis.
   */
  @AndroidDpCoordinate
  private fun processVerticalAttributes(attributes: NlAttributesHolder, @AndroidDpCoordinate y: Int): Int {
    val parent = myComponent.parent!!
    targetY?.myIsHighlight = false

    // Calculate vertical constraint(s)
    if (hasVerticalConstraint) {
      updateVerticalConstraints(attributes)
      return y
    }

    clearVerticalConstrains(attributes)
    attributes.removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL)
    val dy = Math.max(parent.drawTop, Math.min(y, parent.drawBottom - myComponent.drawHeight))
    if (!isAutoConnectionEnabled()) {
      return dy
    }

    val snappedY = targetSnapper.trySnapVertical(dy)
    if (snappedY.isPresent) {
      targetSnapper.applyNotches(attributes)
      targetY = targetSnapper.snappedVerticalTarget as BaseRelativeTarget?
      targetY?.myIsHighlight = true

      targetSnapper.clearSnappedNotches()

      return snappedY.asInt
    }
    else {
      addVerticalParentConstraint(attributes, dy)
      return dy
    }
  }

  /**
   * Calculate the align and margin on y axis.
   * This functions should only be used when there is no Notch on Y axis
   */
  private fun addVerticalParentConstraint(attributes: NlAttributesHolder, @AndroidDpCoordinate y: Int) {
    val parent = myComponent.parent!!
    if (y + myComponent.drawHeight / 2 < parent.drawCenterY) {
      // near to the top side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP, SdkConstants.VALUE_TRUE)
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_TOP, maxOf(y - parent.drawTop, 0).toDpString())
    }
    else {
      // near to the bottom side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, SdkConstants.VALUE_TRUE)
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                                     maxOf(parent.drawBottom - (y + myComponent.drawHeight), 0).toDpString())
    }
  }

  /**
   * Update the exist vertical constraints to make them match the position of SceneComponent
   */
  private fun updateVerticalConstraints(attributes: NlAttributesHolder) {
    updateAlignAttributeIfNeed(attributes, TOP_ALIGN_ATTRIBUTES, myComponent.drawTop, TOP_ATTRIBUTE_RULES)
    updateAlignAttributeIfNeed(attributes, BOTTOM_ALIGN_ATTRIBUTES, myComponent.drawBottom, BOTTOM_ATTRIBUTE_RULES)
  }

  private fun updateAlignAttributeIfNeed(attributes: NlAttributesHolder,
                                         attributesToUpdate: Array<String>,
                                         coordinateToUpdate: Int,
                                         rules: AlignAttributeRules) {
    if (attributesToUpdate.any { attributes.getAndroidAttribute(it) != null }) {
      updateAlignAttribute(myComponent, attributes, coordinateToUpdate, rules)
    }
  }
}

private fun hasHorizontalConstraint(component: SceneComponent): Boolean {
  val nlComponent = component.authoritativeNlComponent
  return HORIZONTAL_ALIGNING_ATTRIBUTE_NAMES.any { nlComponent.getAndroidAttribute(it) != null }
}

private fun hasVerticalConstraint(component: SceneComponent): Boolean {
  val nlComponent = component.authoritativeNlComponent
  return VERTICAL_ALIGNING_ATTRIBUTE_NAMES.any { nlComponent.getAndroidAttribute(it) != null }
}

private fun clearHorizontalConstrains(attributes: NlAttributesHolder) =
  HORIZONTAL_ALIGNING_ATTRIBUTE_NAMES.map { attributes.removeAndroidAttribute(it) }

private fun clearVerticalConstrains(attributes: NlAttributesHolder) =
  VERTICAL_ALIGNING_ATTRIBUTE_NAMES.map { attributes.removeAndroidAttribute(it) }

private val MARGINS_WITHOUT_RTL = arrayOf(
  SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
  SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
  SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
  SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
)

/**
 * The horizontal constraints. Note that this doesn't include [SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL] and
 * [SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT] because they didn't apply margin attribute, which we need special rule to handle.
 */
private val HORIZONTAL_ALIGNING_ATTRIBUTE_NAMES = arrayOf(
  SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
  SdkConstants.ATTR_LAYOUT_MARGIN_START,
  SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
  SdkConstants.ATTR_LAYOUT_MARGIN_END,
  SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT,
  SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
  SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
  SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
  SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE,
  SdkConstants.ATTR_LAYOUT_ALIGN_LEFT,
  SdkConstants.ATTR_LAYOUT_ALIGN_START,
  SdkConstants.ATTR_LAYOUT_ALIGN_END,
  SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM,
  SdkConstants.ATTR_LAYOUT_TO_LEFT_OF,
  SdkConstants.ATTR_LAYOUT_TO_START_OF,
  SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF,
  SdkConstants.ATTR_LAYOUT_TO_END_OF
)

/**
 * The vertical constraints. Note that this doesn't include [SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL] and
 * [SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT] because they didn't apply margin attribute, which we need special rule to handle.
 */
private val VERTICAL_ALIGNING_ATTRIBUTE_NAMES = arrayOf(
  SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
  SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
  SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP,
  SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
  SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE,
  SdkConstants.ATTR_LAYOUT_ALIGN_TOP,
  SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM,
  SdkConstants.ATTR_LAYOUT_ABOVE,
  SdkConstants.ATTR_LAYOUT_BELOW
)
