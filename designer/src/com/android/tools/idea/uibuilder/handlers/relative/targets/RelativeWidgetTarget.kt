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

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.model.getBaseline
import com.android.tools.idea.uibuilder.scene.target.Notch
import com.intellij.ui.JBColor
import java.util.*

const private val DEBUG = false
const private val NOTCH_GAP_SIZE = 6

/**
 * The target provided for creating relationship.
 */
class RelativeWidgetTarget(val type: Type) : BaseRelativeTarget() {

  enum class Type { LEFT, TOP, RIGHT, BOTTOM, BASELINE }

  private var x1 = Int.MIN_VALUE
  private var y1 = Int.MIN_VALUE
  private var x2 = Int.MIN_VALUE
  private var y2 = Int.MIN_VALUE

  override fun getPreferenceLevel() = Target.GUIDELINE_ANCHOR_LEVEL

  override fun layout(context: SceneContext, l: Int, t: Int, r: Int, b: Int): Boolean {
    val parent = myComponent.parent ?: return false
    when (type) {
      Type.LEFT -> {
        x1 = myComponent.drawX
        y1 = parent.drawY
        x2 = x1
        y2 = y1 + parent.drawHeight
      }
      Type.TOP -> {
        x1 = parent.drawX
        y1 = myComponent.drawY
        x2 = x1 + parent.drawHeight
        y2 = y1
      }
      Type.RIGHT -> {
        x1 = myComponent.drawX + myComponent.drawWidth
        y1 = parent.drawY
        x2 = x1
        y2 = y1 + parent.drawHeight
      }
      Type.BOTTOM -> {
        x1 = parent.drawX
        y1 = myComponent.drawY + myComponent.drawHeight
        x2 = x1 + parent.drawHeight
        y2 = y1
      }
      Type.BASELINE -> {
        x1 = parent.drawX
        y1 = myComponent.drawY + myComponent.baseline
        x2 = x1 + parent.drawHeight
        y2 = y1
      }
    }
    return false
  }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    if (myIsHighlight) {
      list.addLine(sceneContext, x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), sceneContext.colorSet.dragReceiverFrames)
    }
    @Suppress("ConstantConditionIf")
    if (DEBUG) {
      drawDebug(list, sceneContext)
    }
  }

  /**
   * Draw the debug graphics
   */
  private fun drawDebug(list: DisplayList, sceneContext: SceneContext) =
      list.addRect(sceneContext, x1.toFloat(), x1.toFloat(), x2.toFloat(), y2.toFloat(),
          if (myIsHighlight) JBColor.GREEN else if (type == Type.BASELINE) JBColor.YELLOW else JBColor.RED)

  override fun fill(owner: SceneComponent, snappableComponent: SceneComponent,
                    horizontalNotches: MutableList<Notch>, verticalNotches: MutableList<Notch>) {
    // TODO: if the owner doesn't have ID, added it.

    if (hasDependency(owner, snappableComponent)) {
      // avoid cycling depedency
      return
    }

    when (type) {
      Type.LEFT -> {
        val value = myComponent.drawX
        val shift = snappableComponent.drawWidth
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, value, value, ATTR_LAYOUT_ALIGN_START))
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, value - shift, value, ATTR_LAYOUT_TO_START_OF))
      }
      Type.TOP -> {
        val value = myComponent.drawY
        val shift = snappableComponent.drawHeight
        verticalNotches.add(createNotch(Notch::Vertical, owner, value, value, ATTR_LAYOUT_ALIGN_TOP))
        verticalNotches.add(createNotch(Notch::Vertical, owner, value - shift, value, ATTR_LAYOUT_ABOVE))
      }
      Type.RIGHT -> {
        val value = myComponent.drawX + myComponent.drawWidth
        val shift = snappableComponent.drawWidth
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, value, value, ATTR_LAYOUT_TO_END_OF))
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, value - shift, value, ATTR_LAYOUT_ALIGN_END))
      }
      Type.BOTTOM -> {
        val value = myComponent.drawY + myComponent.drawHeight
        val shift = snappableComponent.drawHeight
        verticalNotches.add(createNotch(Notch::Vertical, owner, value, value, ATTR_LAYOUT_BELOW))
        verticalNotches.add(createNotch(Notch::Vertical, owner, value - shift, value, ATTR_LAYOUT_ALIGN_BOTTOM))
      }
      Type.BASELINE -> {
        if (snappableComponent.nlComponent.getBaseline() != -1) {
          val value = owner.drawY + owner.baseline
          val shift = snappableComponent.baseline
          verticalNotches.add(createNotch(Notch::Vertical, owner, value - shift, value, ATTR_LAYOUT_ALIGN_BASELINE))
        }
      }
    }
  }

  private fun createNotch(notchConstructor: (SceneComponent, Int, Int, Notch.Action) -> Notch,
                          alignedComponent: SceneComponent, value: Int, display: Int, alignAttributeName: String): Notch {
    val alignedNlComponent = alignedComponent.nlComponent
    val (shiftAttribute, shiftValue) = getMarginShift(alignedNlComponent, alignAttributeName)

    val notch = notchConstructor(alignedComponent, value, display, Notch.Action {
      it.setAndroidAttribute(alignAttributeName, NEW_ID_PREFIX + alignedNlComponent.id)
      if (shiftAttribute != null) {
        it.setAndroidAttribute(shiftAttribute, shiftValue)
      }
    })
    notch.setGap(NOTCH_GAP_SIZE)
    notch.target = this@RelativeWidgetTarget
    return notch
  }

  /**
   * Used to calculate the shift of margin attribute.<br>
   * The actual aligning position is "Widget position + margin value". To make the result same as snappble one, we need to add margin
   * value to shift the position of dragged component. The value is calculated by the aligned component.
   *
   * @return If need shift, return [Pair] continas attribute to value. Return null to null [Pair] otherwise.
   */
  private fun getMarginShift(alignedNlComponent: NlComponent, alignedAttributeName: String): Pair<String?, String?> {
    // Get the attribute should be retrieved from aligned component.
    val rule = SHIFT_MAP[alignedAttributeName]
    if (rule != null) {
      // android:layout_margin has higher priority than other margin.
      val alignedMargin = alignedNlComponent.getLiveAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN) ?:
          alignedNlComponent.getLiveAttribute(ANDROID_URI, rule.alignedAttribute)
      if (alignedMargin != null) {
        return rule.shiftAttribute to rule.shiftValueCalculator(alignedMargin)
      }
    }
    return (null to null)
  }

  /**
   * Check if [owner] is dependent on [snappableComponent]
   */
  private fun hasDependency(owner: SceneComponent, snappableComponent: SceneComponent): Boolean {
    val id = snappableComponent.nlComponent.id ?: return false
    return DEPENDENT_ATTRIBUTES
        .map { owner.nlComponent.getAndroidAttribute(it) }
        .any { NlComponent.extractId(it) == id }
  }
}

private val DEPENDENT_ATTRIBUTES = arrayOf(
    ATTR_LAYOUT_ALIGN_BASELINE,
    ATTR_LAYOUT_ALIGN_LEFT,
    ATTR_LAYOUT_ALIGN_START,
    ATTR_LAYOUT_ALIGN_TOP,
    ATTR_LAYOUT_ALIGN_RIGHT,
    ATTR_LAYOUT_ALIGN_END,
    ATTR_LAYOUT_ALIGN_BOTTOM,
    ATTR_LAYOUT_TO_LEFT_OF,
    ATTR_LAYOUT_TO_START_OF,
    ATTR_LAYOUT_ABOVE,
    ATTR_LAYOUT_TO_RIGHT_OF,
    ATTR_LAYOUT_TO_END_OF,
    ATTR_LAYOUT_BELOW
)

/**
 * Rule to shift the margin.
 */
private class ShiftRule(val alignedAttribute: String,
                        val shiftAttribute: String,
                        val shiftValueCalculator: (alignedValue: String) -> String)

private val SHIFT_MAP = mapOf(
    ATTR_LAYOUT_TO_LEFT_OF to ShiftRule(ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT, { dp -> "-$dp" }),
    ATTR_LAYOUT_TO_START_OF to ShiftRule(ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END, { dp -> "-$dp" }),
    ATTR_LAYOUT_ABOVE to ShiftRule(ATTR_LAYOUT_MARGIN_TOP, ATTR_LAYOUT_MARGIN_BOTTOM, { dp -> "-$dp" }),
    ATTR_LAYOUT_TO_RIGHT_OF to ShiftRule(ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_LEFT, { dp -> "-$dp" }),
    ATTR_LAYOUT_TO_END_OF to ShiftRule(ATTR_LAYOUT_MARGIN_END, ATTR_LAYOUT_MARGIN_START, { dp -> "-$dp" }),
    ATTR_LAYOUT_BELOW to ShiftRule(ATTR_LAYOUT_MARGIN_BOTTOM, ATTR_LAYOUT_MARGIN_TOP, { dp -> "-$dp" })
)
