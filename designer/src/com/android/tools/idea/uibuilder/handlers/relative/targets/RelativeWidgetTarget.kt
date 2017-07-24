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
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate
import com.android.tools.idea.uibuilder.model.NlComponent
import com.android.tools.idea.uibuilder.model.getBaseline
import com.android.tools.idea.uibuilder.scene.SceneComponent
import com.android.tools.idea.uibuilder.scene.SceneContext
import com.android.tools.idea.uibuilder.scene.draw.DisplayList
import com.android.tools.idea.uibuilder.scene.draw.DrawRegion
import com.android.tools.idea.uibuilder.scene.target.Notch
import com.android.tools.idea.uibuilder.scene.target.Target
import com.intellij.ui.JBColor
import org.jetbrains.annotations.NotNull
import java.awt.Graphics2D
import java.util.ArrayList

const private val DEBUG = false
const private val NOTCH_GAP_SIZE = 6

/**
 * The target provided for creating relationship.
 */
class RelativeWidgetTarget(
    @param:NotNull val myType: Type,
    @param:AndroidDpCoordinate private val myX1: Int, @param:AndroidDpCoordinate private val myY1: Int,
    @param:AndroidDpCoordinate private val myX2: Int, @param:AndroidDpCoordinate private val myY2: Int) : BaseRelativeTarget() {

  enum class Type { LEFT, TOP, RIGHT, BOTTOM, BASELINE }

  override fun getPreferenceLevel() = Target.GUIDELINE_ANCHOR_LEVEL

  override fun fill(owner: SceneComponent, snappableComponent: SceneComponent,
                    horizontalNotches: ArrayList<Notch>, verticalNotches: ArrayList<Notch>) {
    // TODO: if the owner doesn't have ID, added it.

    if (hasDependency(owner, snappableComponent)) {
      // avoid cycling depedency
      return
    }

    when (myType) {
      Type.LEFT -> {
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, (myX1 + myX2) / 2, (myX1 + myX2) / 2,
            SdkConstants.ATTR_LAYOUT_ALIGN_START))
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, (myX1 + myX2) / 2 - snappableComponent.drawWidth, (myX1 + myX2) / 2,
            SdkConstants.ATTR_LAYOUT_TO_START_OF))
      }
      Type.RIGHT -> {
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, (myX1 + myX2) / 2, (myX1 + myX2) / 2,
            SdkConstants.ATTR_LAYOUT_TO_END_OF))
        horizontalNotches.add(createNotch(Notch::Horizontal, owner, (myX1 + myX2) / 2 - snappableComponent.drawWidth, (myX1 + myX2) / 2,
            SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT))
      }
      Type.TOP -> {
        verticalNotches.add(createNotch(Notch::Vertical, owner, (myY1 + myY2) / 2, (myY1 + myY2) / 2,
            SdkConstants.ATTR_LAYOUT_ALIGN_TOP))
        verticalNotches.add(createNotch(Notch::Vertical, owner, (myY1 + myY2) / 2 - snappableComponent.drawHeight, (myY1 + myY2) / 2,
            SdkConstants.ATTR_LAYOUT_ABOVE))
      }
      Type.BOTTOM -> {
        verticalNotches.add(createNotch(Notch::Vertical, owner, (myY1 + myY2) / 2, (myY1 + myY2) / 2,
            SdkConstants.ATTR_LAYOUT_BELOW))
        verticalNotches.add(createNotch(Notch::Vertical, owner, (myY1 + myY2) / 2 - snappableComponent.drawHeight, (myY1 + myY2) / 2,
            SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM))
      }
      Type.BASELINE -> {
        if (snappableComponent.nlComponent.getBaseline() != -1) {
          verticalNotches.add(createNotch(Notch::Vertical, owner, (myY1 + myY2) / 2 - snappableComponent.baseline, (myY1 + myY2) / 2,
              SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE))
        }
      }
    }
  }

  private fun createNotch(constructor: (SceneComponent, Int, Int, Notch.Action) -> Notch,
                          component: SceneComponent, value: Int, display: Int, attribute: String): Notch {
    return constructor(component, value, display, Notch.Action {
      it.setAndroidAttribute(attribute, SdkConstants.NEW_ID_PREFIX + component.nlComponent.id)
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

  override fun layout(context: SceneContext,
                      @AndroidDpCoordinate l: Int, @AndroidDpCoordinate t: Int,
                      @AndroidDpCoordinate r: Int, @AndroidDpCoordinate b: Int) = false

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    if (myIsHighlight) {
      list.add(TargetRegion(sceneContext.getSwingX(myX1.toFloat()), sceneContext.getSwingY(myY1.toFloat()),
          sceneContext.getSwingDimension((myX2 - myX1).toFloat()), sceneContext.getSwingDimension((myY2 - myY1).toFloat())))
    }
    if (DEBUG) {
      drawDebug(list, sceneContext)
    }
  }

  /**
   * Draw the debug graphics
   */
  private fun drawDebug(list: DisplayList, sceneContext: SceneContext) =
      list.addRect(sceneContext, myX1.toFloat(), myY1.toFloat(), myX2.toFloat(), myY2.toFloat(),
          if (myIsHighlight) JBColor.GREEN else if (myType == Type.BASELINE) JBColor.YELLOW else JBColor.RED)

  private class TargetRegion(@SwingCoordinate x: Int, @SwingCoordinate y: Int,
                             @SwingCoordinate width: Int, @SwingCoordinate height: Int) : DrawRegion(x, y, width, height) {
    override fun paint(g: Graphics2D, sceneContext: SceneContext) {
      g.color = sceneContext.colorSet.dragReceiverFrames
      g.fillRect(x, y, width, height)
    }
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