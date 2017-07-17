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

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate
import com.android.tools.idea.uibuilder.scene.SceneComponent
import com.android.tools.idea.uibuilder.scene.SceneContext
import com.android.tools.idea.uibuilder.scene.ScenePicker
import com.android.tools.idea.uibuilder.scene.draw.DisplayList
import com.android.tools.idea.uibuilder.scene.draw.DrawRegion
import com.android.tools.idea.uibuilder.scene.target.BaseTarget
import com.android.tools.idea.uibuilder.scene.target.Notch
import com.intellij.ui.JBColor
import org.jetbrains.annotations.NotNull
import java.awt.Graphics2D

import java.util.ArrayList

/**
 * The Target for providing Notches of RelativeLayout.
 */
class RelativeParentTarget(
    @param:NotNull val myType: Type,
    @param:AndroidDpCoordinate private val myX1: Int, @param:AndroidDpCoordinate private val myY1: Int,
    @param:AndroidDpCoordinate private val myX2: Int, @param:AndroidDpCoordinate private val myY2: Int) : BaseTarget(), Notch.Provider {

  companion object {
    const private val DEBUG = false
    const private val NOTCH_GAP_SIZE = 10
  }

  enum class Type {
    LEFT, TOP, RIGHT, BOTTOM, CENTER_HORIZONTAL, CENTER_VERTICAL
  }

  var myIsHighlight: Boolean = false

  override fun getPreferenceLevel() = GUIDELINE_ANCHOR_LEVEL

  override fun layout(context: SceneContext,
                      @AndroidDpCoordinate l: Int, @AndroidDpCoordinate t: Int,
                      @AndroidDpCoordinate r: Int, @AndroidDpCoordinate b: Int): Boolean {
    myLeft = myX1.toFloat()
    myTop = myY1.toFloat()
    myRight = myX2.toFloat()
    myBottom = myY2.toFloat()
    return false
  }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    if (myIsHighlight) {
      list.add(ParentEdgeRegion(sceneContext.getSwingX(myLeft), sceneContext.getSwingY(myTop),
          sceneContext.getSwingDimension(myRight - myLeft), sceneContext.getSwingDimension(myBottom - myTop)))
    }
    if (DEBUG) {
      drawDebug(list, sceneContext)
    }
  }

  /**
   * Draw the debug graphics
   */
  private fun drawDebug(list: DisplayList, sceneContext: SceneContext) =
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, if (myIsHighlight) JBColor.GREEN else JBColor.RED)

  override fun addHit(transform: SceneContext, picker: ScenePicker) =
      picker.addRect(this, 10,
          transform.getSwingX(myLeft), transform.getSwingY(myTop), transform.getSwingX(myRight), transform.getSwingY(myBottom))

  override fun fill(owner: SceneComponent, snappableComponent: SceneComponent,
                    horizontalNotches: ArrayList<Notch>, verticalNotches: ArrayList<Notch>) {
    // TODO: set action for notch to update the attribute if needed
    val notch: Notch
    when (myType) {
      RelativeParentTarget.Type.LEFT -> {
        notch = Notch.Horizontal(owner, owner.drawX, owner.drawX)
        horizontalNotches.add(notch)
      }
      RelativeParentTarget.Type.TOP -> {
        notch = Notch.Vertical(owner, owner.drawY, owner.drawY)
        verticalNotches.add(notch)
      }
      RelativeParentTarget.Type.RIGHT -> {
        notch = Notch.Horizontal(owner, owner.drawX + owner.drawWidth - snappableComponent.drawWidth, owner.drawX + owner.drawWidth)
        horizontalNotches.add(notch)
      }
      RelativeParentTarget.Type.BOTTOM -> {
        notch = Notch.Vertical(owner, owner.drawY + owner.drawHeight - snappableComponent.drawHeight, owner.drawY + owner.drawHeight)
        verticalNotches.add(notch)
      }
      RelativeParentTarget.Type.CENTER_HORIZONTAL -> {
        notch = Notch.Horizontal(owner, owner.drawX + (owner.drawWidth - snappableComponent.drawWidth) / 2,
            owner.drawX + owner.drawWidth / 2)
        horizontalNotches.add(notch)
      }
      RelativeParentTarget.Type.CENTER_VERTICAL -> {
        notch = Notch.Vertical(owner, owner.drawY + (owner.drawHeight - snappableComponent.drawHeight) / 2,
            owner.drawY + owner.drawHeight / 2)
        verticalNotches.add(notch)
      }
    }

    notch.setGap(NOTCH_GAP_SIZE)
    notch.target = this
  }

  private class ParentEdgeRegion(@SwingCoordinate x: Int, @SwingCoordinate y: Int,
                                 @SwingCoordinate width: Int, @SwingCoordinate height: Int) : DrawRegion(x, y, width, height) {
    override fun paint(g: Graphics2D, sceneContext: SceneContext) {
      g.color = sceneContext.colorSet.dragReceiverFrames
      g.fillRect(x, y, width, height)
    }
  }
}
