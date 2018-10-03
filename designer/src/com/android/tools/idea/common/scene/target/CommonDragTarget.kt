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
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawBottom
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawLeft
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawRight
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawTop
import com.android.tools.idea.uibuilder.model.viewHandler
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Stroke

private const val DEBUG_RENDERER = false

private const val MIN_SIZE = 16

class CommonDragTarget @JvmOverloads constructor(sceneComponent: SceneComponent,
                                                 private val fromToolWindow: Boolean = false)
  : BaseTarget() {

  /**
   * list of dragged components. The first entry is the one which user start dragging.
   */
  private val draggedComponents: List<SceneComponent>

  /**
   * Mouse position when start dragging.
   */
  @AndroidDpCoordinate private val firstMouse = Point(-1, -1)

  /**
   * Offsets of every selected components. Their units are AndroidDpCoordinate
   */
  private val offsets: Array<Point>

  /**
   * The collected placeholder.
   * Needs to be lazy because [myComponent] doesn't exist when constructing the [Target].
   * But it will be set immediately after [Target] is created.
   */
  private val placeholders: List<Placeholder>
  private val dominatePlaceholders: List<Placeholder>
  private val recessivePlaceholders: List<Placeholder>
  private val placeholderHosts: Set<SceneComponent>

  private var currentSnappedPlaceholder: Placeholder? = null

  init {
    myComponent = sceneComponent

    val scene = component.scene
    val selection = scene.selection
    val selectedSceneComponents = selection.mapNotNull { scene.getSceneComponent(it) }
    // Make sure myComponent is the first one which is interacted with user.
    // Note that myComponent may not be the first one in selection, since user may drag the component which is not selected first.
    draggedComponents = sequenceOf(component).plus(selectedSceneComponents.filterNot { it == myComponent }).toList()
    offsets = Array(draggedComponents.size) { Point(-1, -1) }

    placeholders = component.scene.getPlaceholders(component).filter { it.host != component }
    dominatePlaceholders = placeholders.filter { it.dominate }
    recessivePlaceholders = placeholders.filterNot { it.dominate }
    placeholderHosts = placeholders.map { it.host }.toSet()
  }

  override fun setComponent(component: SceneComponent) {
    assert(myComponent == component)
    super.setComponent(component)
  }

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
      val snappedPlaceholder = currentSnappedPlaceholder
      if (snappedPlaceholder != null) {
        // Render dominate component only. Note that the snappedPlaceholder may be recessive one.
        for (ph in dominatePlaceholders) {
          // Someone got snapped
          when {
            ph == snappedPlaceholder -> {
              // Snapped Placeholder
              list.add(DrawSnappedPlaceholder(ph.region.left, ph.region.top, ph.region.right, ph.region.bottom))
            }
            ph.associatedComponent == snappedPlaceholder.associatedComponent -> {
              // Sibling of snapped Placeholder
              list.add(DrawSiblingsOfSnappedPlaceholder(ph.region.left, ph.region.top, ph.region.right, ph.region.bottom))
            }
            else -> Unit // Do nothing for Placeholders in different host
          }
        }
      }

      for (h in placeholderHosts) {
        // Render hosts
        if (h == snappedPlaceholder?.host) {
          list.add(DrawHoveredHost(h.drawLeft, h.drawTop, h.drawRight, h.drawBottom))
        }
        else {
          list.add(DrawNonHoveredHost(h.drawLeft, h.drawTop, h.drawRight, h.drawBottom))
        }
      }
    }
  }

  override fun getPreferenceLevel() = Target.DRAG_LEVEL

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    firstMouse.x = x
    firstMouse.y = y

    if (fromToolWindow) {
      if (!draggedComponents.isEmpty()) {
        // When dragging from Tool window, makes the primary dragged point is in the center of widget.
        // We assign the offset here because the component is not really clicked.
        val primaryComponent = draggedComponents[0]
        offsets[0].x = primaryComponent.drawWidth / 2
        offsets[0].y = primaryComponent.drawHeight / 2

        val primaryX = primaryComponent.getDrawX(System.currentTimeMillis())
        val primaryY = primaryComponent.getDrawY(System.currentTimeMillis())

        // Calculate distance between primary and other components.
        for (i in 1 until draggedComponents.size) {
          offsets[i].x = offsets[0].x + primaryX - draggedComponents[i].getDrawX(System.currentTimeMillis())
          offsets[i].y = offsets[0].y + primaryY - draggedComponents[i].getDrawY(System.currentTimeMillis())
        }
      }
    }
    else {
      // Drag inside the component, leave the dragged point same as the clicked point.
      draggedComponents.forEachIndexed { index, sceneComponent ->
        offsets[index].x = x - sceneComponent.getDrawX(System.currentTimeMillis())
        offsets[index].y = y - sceneComponent.getDrawY(System.currentTimeMillis())
      }
    }
    currentSnappedPlaceholder = null
  }

  override fun mouseDrag(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, unused: List<Target>) {
    draggedComponents.forEach { it.isDragging = true }
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
    // We use primary component to do snap
    val left = mouseX - offsets[0].x
    val top = mouseY - offsets[0].y
    val right = left + myComponent.drawWidth
    val bottom = top + myComponent.drawHeight

    var targetPlaceholder: Placeholder? = null
    var currentDistance: Double = Double.MAX_VALUE
    var snappedX = left
    var snappedY = top

    val xDouble by lazy { left.toDouble() }
    val yDouble by lazy { top.toDouble() }

    val retPoint = Point()

    fun doSnap(phs: List<Placeholder>) {
      for (ph in phs) {
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
    }

    doSnap(dominatePlaceholders)
    if (targetPlaceholder == null) {
      doSnap(recessivePlaceholders)
    }

    currentSnappedPlaceholder = targetPlaceholder
    if (currentSnappedPlaceholder?.dominate == true) {
      draggedComponents.forEach { it.setPosition(snappedX, snappedY) }
    }
    else {
      draggedComponents.forEachIndexed { index, it -> it.setPosition(mouseX - offsets[index].x, mouseY - offsets[index].y) }
    }
    return targetPlaceholder
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, unused: List<Target>) {
    if (!myComponent.isDragging) {
      // Ignore double clicking case.
      return
    }
    draggedComponents.forEach { it.isDragging = false }
    val ph = snap(x, y)
    if (ph != null) {
      applyPlaceholder(ph)
      myComponent.scene.needsLayout(Scene.ANIMATED_LAYOUT)
    }
    else {
      draggedComponents.forEachIndexed { index, sceneComponent ->
        sceneComponent.setPosition(firstMouse.x - offsets[index].x, firstMouse.y - offsets[index].y)
      }
    }
  }

  /**
   * Apply the given [Placeholder]. Returns true if succeed, false otherwise. If [commit] is true, the applied attributes will be
   * write to file directly.
   */
  private fun applyPlaceholder(placeholder: Placeholder, commit: Boolean = true): Boolean {
    val parent = placeholder.host.authoritativeNlComponent
    val primaryNlComponent = myComponent.authoritativeNlComponent
    val model = primaryNlComponent.model
    val componentsToAdd = draggedComponents.map { it.authoritativeNlComponent }
    val anchor = placeholder.nextComponent?.nlComponent

    if (model.canAddComponents(componentsToAdd, parent, anchor)) {
      val attributesTransactions = draggedComponents.map {
        val transaction = it.authoritativeNlComponent.startAttributeTransaction()
        placeholder.updateAttribute(it, transaction)
        transaction
      }
      if (commit) {
        NlWriteCommandAction.run(componentsToAdd, "Drag ${primaryNlComponent.tagName}") {
          attributesTransactions.forEach { it.commit() }
          model.addComponents(componentsToAdd, parent, anchor, InsertType.MOVE_WITHIN, myComponent.scene.designSurface)
        }
      }
      else {
        attributesTransactions.forEach { it.apply() }
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
    currentSnappedPlaceholder = null
  }

  override fun newSelection(): List<SceneComponent> = listOf(myComponent)

  override fun getMouseCursor(): Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

  override fun isHittable() = if (myComponent.isSelected) myComponent.canShowBaseline() || !myComponent.isDragging else true
}

private abstract class BasePlaceholderDrawRegion(@AndroidDpCoordinate private val x1: Int,
                                                 @AndroidDpCoordinate private val y1: Int,
                                                 @AndroidDpCoordinate private val x2: Int,
                                                 @AndroidDpCoordinate private val y2: Int)
  : DrawRegion() {

  final override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val defColor = g.color
    val defStroke = g.stroke

    setBounds(sceneContext.getSwingXDip(x1.toFloat()),
              sceneContext.getSwingYDip(y1.toFloat()),
              sceneContext.getSwingDimensionDip((x2 - x1).toFloat()),
              sceneContext.getSwingDimensionDip((y2 - y1).toFloat()))

    getBackgroundColor(sceneContext.colorSet)?.let {
      g.color = it
      g.fill(this)
    }

    g.stroke = getBorderStroke(sceneContext.colorSet)
    g.color = getBorderColor(sceneContext.colorSet)
    g.draw(this)

    g.color = defColor
    g.stroke = defStroke
  }

  abstract fun getBackgroundColor(colorSet: ColorSet): Color?

  abstract fun getBorderColor(colorSet: ColorSet): Color

  abstract fun getBorderStroke(colorSet: ColorSet): Stroke
}

private class DrawSnappedPlaceholder(@AndroidDpCoordinate x1: Int,
                                     @AndroidDpCoordinate y1: Int,
                                     @AndroidDpCoordinate x2: Int,
                                     @AndroidDpCoordinate y2: Int)
  : BasePlaceholderDrawRegion(x1, y1, x2, y2) {

  override fun getBackgroundColor(colorSet: ColorSet): Color? = colorSet.dragReceiverBackground

  override fun getBorderColor(colorSet: ColorSet): Color = colorSet.dragReceiverFrames

  override fun getBorderStroke(colorSet: ColorSet): Stroke = colorSet.dragReceiverStroke
}

private class DrawSiblingsOfSnappedPlaceholder(@AndroidDpCoordinate x1: Int,
                                               @AndroidDpCoordinate y1: Int,
                                               @AndroidDpCoordinate x2: Int,
                                               @AndroidDpCoordinate y2: Int)
  : BasePlaceholderDrawRegion(x1, y1, x2, y2) {

  override fun getBackgroundColor(colorSet: ColorSet): Color? = colorSet.dragReceiverSiblingBackground

  override fun getBorderColor(colorSet: ColorSet): Color = colorSet.dragReceiverSiblingBackground

  override fun getBorderStroke(colorSet: ColorSet): Stroke = colorSet.dragReceiverSiblingStroke
}

private class DrawHoveredHost(@AndroidDpCoordinate x1: Int,
                              @AndroidDpCoordinate y1: Int,
                              @AndroidDpCoordinate x2: Int,
                              @AndroidDpCoordinate y2: Int)
  : BasePlaceholderDrawRegion(x1, y1, x2, y2) {

  override fun getBackgroundColor(colorSet: ColorSet): Color? = null

  override fun getBorderColor(colorSet: ColorSet): Color = colorSet.dragReceiverFrames

  override fun getBorderStroke(colorSet: ColorSet): Stroke = colorSet.dragReceiverStroke
}

private class DrawNonHoveredHost(@AndroidDpCoordinate x1: Int,
                                 @AndroidDpCoordinate y1: Int,
                                 @AndroidDpCoordinate x2: Int,
                                 @AndroidDpCoordinate y2: Int)
  : BasePlaceholderDrawRegion(x1, y1, x2, y2) {

  override fun getBackgroundColor(colorSet: ColorSet): Color? = null

  override fun getBorderColor(colorSet: ColorSet): Color = colorSet.dragOtherReceiversFrame

  override fun getBorderStroke(colorSet: ColorSet): Stroke = colorSet.dragReceiverSiblingStroke
}
