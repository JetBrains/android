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
                    horizontalNotches: ArrayList<Notch>, verticalNotches: ArrayList<Notch>) {
    // TODO: if the owner doesn't have ID, added it.
    // FIXME: if owner already align to the edges of parent, snappableComponent cannot "insert" into owner and the edge of parent.

    if (hasDependency(owner, snappableComponent)) {
      // avoid cycling depedency
      return
    }

    when (type) {
      Type.LEFT -> {
        val value = myComponent.drawX
        val shift = snappableComponent.drawWidth
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, value, value, SdkConstants.ATTR_LAYOUT_ALIGN_START))
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, value - shift, value, SdkConstants.ATTR_LAYOUT_TO_START_OF))
      }
      Type.TOP -> {
        val value = myComponent.drawY
        val shift = snappableComponent.drawHeight
        verticalNotches.add(createNotch(Notch::Vertical, owner, value, value, SdkConstants.ATTR_LAYOUT_ALIGN_TOP))
        verticalNotches.add(createNotch(Notch::Vertical, owner, value - shift, value, SdkConstants.ATTR_LAYOUT_ABOVE))
      }
      Type.RIGHT -> {
        val value = myComponent.drawX + myComponent.drawWidth
        val shift = snappableComponent.drawWidth
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, value, value, SdkConstants.ATTR_LAYOUT_TO_END_OF))
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, value - shift, value, SdkConstants.ATTR_LAYOUT_ALIGN_END))
      }
      Type.BOTTOM -> {
        val value = myComponent.drawY + myComponent.drawHeight
        val shift = snappableComponent.drawHeight
        verticalNotches.add(createNotch(Notch::Vertical, owner, value, value, SdkConstants.ATTR_LAYOUT_BELOW))
        verticalNotches.add(createNotch(Notch::Vertical, owner, value - shift, value, SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM))
      }
      Type.BASELINE -> {
        if (snappableComponent.nlComponent.getBaseline() != -1) {
          val value = owner.drawY + owner.baseline
          val shift = snappableComponent.baseline
          verticalNotches.add(createNotch(Notch::Vertical, owner, value - shift, value, SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE))
        }
      }
    }
  }

  private fun createNotch(notchConstructor: (SceneComponent, Int, Int, Notch.Action) -> Notch,
                          component: SceneComponent, value: Int, display: Int, alignAttributeName: String): Notch {
    return notchConstructor(component, value, display, Notch.Action {
      it.setAndroidAttribute(alignAttributeName, SdkConstants.NEW_ID_PREFIX + component.nlComponent.id)
    }).apply {
      this.setGap(NOTCH_GAP_SIZE)
      this.target = this@RelativeWidgetTarget
    }
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