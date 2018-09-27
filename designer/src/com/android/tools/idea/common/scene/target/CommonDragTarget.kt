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
package com.android.tools.idea.common.scene.target

import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.scene.*
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawRegion
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler
import com.android.tools.idea.uibuilder.model.viewHandler
import com.intellij.ui.JBColor
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point

private const val DEBUG_RENDERER = false

private const val MIN_SIZE = 16

class CommonDragTarget : BaseTarget() {

  @AndroidDpCoordinate private var offsetX: Int = 0
  @AndroidDpCoordinate private var offsetY: Int = 0

  @AndroidDpCoordinate private var firstMouseX: Int = 0
  @AndroidDpCoordinate private var firstMouseY: Int = 0

  /**
   * The collected placeholder.
   * Needs to be lazy because [myComponent] doesn't exist when constructing the [Target].
   * But it will be set immediately after [Target] is created.
   */
  private val placeholders by lazy { myComponent.scene.getPlaceholders(myComponent).filter { it.host != myComponent } }

  override fun canChangeSelection() = true

  override fun layout(sceneTransform: SceneContext,
                      @AndroidDpCoordinate l: Int,
                      @AndroidDpCoordinate t: Int,
                      @AndroidDpCoordinate r: Int,
                      @AndroidDpCoordinate b: Int): Boolean {
    val left: Int
    val right: Int
    if (r - l < MIN_SIZE) {
      val d = (MIN_SIZE - (r - l)) / 2
      left = l - d
      right = r + d
    }
    else {
      left = l
      right = r
    }

    val top: Int
    val bottom: Int
    if (b - t < MIN_SIZE) {
      val d = (MIN_SIZE - (b - t)) / 2
      top = t - d
      bottom = b + d
    }
    else {
      top = t
      bottom = b
    }

    myLeft = left.toFloat()
    myTop = top.toFloat()
    myRight = right.toFloat()
    myBottom = bottom.toFloat()
    return false
  }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    @Suppress("ConstantConditionIf")
    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, if (mIsOver) JBColor.yellow else JBColor.green)
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, JBColor.red)
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, JBColor.red)
    }

    if (myComponent.isDragging) {
      for (ph in placeholders) {
        val region = ph.region
        list.add(DrawPlaceholder(sceneContext, region.left, region.top, region.right, region.bottom))
      }
    }
  }

  override fun getPreferenceLevel() = Target.DRAG_LEVEL

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    firstMouseX = x
    firstMouseY = y
    offsetX = x - myComponent.getDrawX(System.currentTimeMillis())
    offsetY = y - myComponent.getDrawY(System.currentTimeMillis())
  }

  override fun mouseDrag(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, unused: List<Target>) {
    myComponent.isDragging = true
    val ph = snap(x, y)
    if (myComponent.scene.isLiveRenderingEnabled
        && ph?.host?.authoritativeNlComponent?.viewHandler is ConstraintLayoutHandler
        && myComponent.parent == ph.host) {
      // For Live Rendering in ConstraintLayout. Live Rendering only works when component is dragged in the same ConstraintLayout
      // TODO: Makes Live Rendering works when dragging widget between different ViewGroups
      applyPlaceholder(ph, commit = false)
      myComponent.authoritativeNlComponent.fireLiveChangeEvent()
      myComponent.scene.needsLayout(Scene.IMMEDIATE_LAYOUT)
    }
    else {
      myComponent.scene.repaint()
    }
  }

  /**
   * Snap the component to the [placeholders].
   * This function applies the snapped placeholder and adjust the position of [myComponent].
   * When placeholder is overlapped, the higher [Region.level] get snapped.
   */
  private fun snap(@AndroidDpCoordinate mouseX: Int, @AndroidDpCoordinate mouseY: Int): Placeholder? {
    val left = mouseX - offsetX
    val top = mouseY - offsetY
    val right = left + myComponent.drawWidth
    val bottom = top + myComponent.drawHeight

    var targetPlaceholder: Placeholder? = null
    var currentDistance: Double = Double.MAX_VALUE
    var snappedX = left
    var snappedY = top

    val xDouble by lazy { left.toDouble() }
    val yDouble by lazy { top.toDouble() }

    val retPoint = Point()
    for (ph in placeholders) {
      val currentPlaceholderLevel = targetPlaceholder?.region?.level ?: -1
      // ignore the placeholders of myComponent and its children.
      if (ph.region.level < currentPlaceholderLevel) {
        continue
      }

      if (ph.snap(left, top, right, bottom, retPoint)) {
        val distance = retPoint.distance(xDouble, yDouble)
        if (distance < currentDistance || ph.region.level > currentPlaceholderLevel) {
          targetPlaceholder = ph
          currentDistance = distance
          snappedX = retPoint.x
          snappedY = retPoint.y
        }
      }
    }
    myComponent.setPosition(snappedX, snappedY)
    return targetPlaceholder
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, unused: List<Target>) {
    if (!myComponent.isDragging) {
      // Ignore double clicking case.
      return
    }
    myComponent.isDragging = false
    val ph = snap(x, y)
    if (ph != null) {
      applyPlaceholder(ph)
      myComponent.scene.needsLayout(Scene.ANIMATED_LAYOUT)
    }
    else {
      myComponent.setPosition(firstMouseX - offsetX, firstMouseY - offsetY)
    }
  }

  /**
   * Apply the given [Placeholder]. Returns true if succeed, false otherwise. If [commit] is true, the applied attributes will be
   * write to file directly.
   */
  private fun applyPlaceholder(placeholder: Placeholder, commit: Boolean = true): Boolean {
    val parent = placeholder.host.authoritativeNlComponent
    val nlComponent = myComponent.authoritativeNlComponent
    val model = nlComponent.model
    val componentsToAdd = listOf(nlComponent)
    val anchor = placeholder.nextComponent?.nlComponent

    if (model.canAddComponents(componentsToAdd, parent, anchor)) {
      val attributes = nlComponent.startAttributeTransaction()
      placeholder.updateAttribute(myComponent, attributes)
      if (commit) {
        model.addComponents(componentsToAdd, parent, anchor, InsertType.MOVE_WITHIN, myComponent.scene.designSurface) {
          attributes.commit()
        }
      }
      else {
        attributes.apply()
      }
      return true
    }
    return false
  }

  /**
   * Reset the status when the dragging is canceled.
   */
  fun cancel() {
    myComponent.isDragging = false
    myComponent.scene.needsLayout(Scene.IMMEDIATE_LAYOUT)
  }

  override fun newSelection(): List<SceneComponent> = listOf(myComponent)

  override fun getMouseCursor(): Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

  override fun isHittable() = if (myComponent.isSelected) myComponent.canShowBaseline() || !myComponent.isDragging else true
}

class DrawPlaceholder(context: SceneContext,
                      @AndroidDpCoordinate x1: Int,
                      @AndroidDpCoordinate y1: Int,
                      @AndroidDpCoordinate x2: Int,
                      @AndroidDpCoordinate y2: Int)
  : DrawRegion() {

  init {
    setBounds(context.getSwingXDip(x1.toFloat()),
              context.getSwingYDip(y1.toFloat()),
              context.getSwingDimensionDip((x2 - x1).toFloat()),
              context.getSwingDimensionDip((y2 - y1).toFloat()))
  }

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val defColor = g.color

    val colorSet = sceneContext.colorSet
    g.color = colorSet.dragReceiverBackground
    g.fill(this)
    g.color = colorSet.dragReceiverFrames
    g.draw(this)

    g.color = defColor
  }
}
