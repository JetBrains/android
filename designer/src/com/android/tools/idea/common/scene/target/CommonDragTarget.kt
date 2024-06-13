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

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.addComponentsAndSelectedIfCreated
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.common.scene.draw.ColorSet
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawRegion
import com.android.tools.idea.uibuilder.api.actions.ToggleAutoConnectAction
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutPlaceholder
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawBottom
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawLeft
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawRight
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawTop
import com.android.tools.idea.uibuilder.scene.target.TargetSnapper
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.intellij.ui.JBColor
import org.intellij.lang.annotations.JdkConstants
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Stroke
import java.awt.event.InputEvent
import kotlin.math.abs

private const val DEBUG_RENDERER = false

private const val MIN_SIZE = 16

class CommonDragTarget
@JvmOverloads
constructor(sceneComponent: SceneComponent, private val fromToolWindow: Boolean = false) :
  BaseTarget() {

  /** List of dragged components. The first entry is the one which user start dragging. */
  private lateinit var draggedComponents: List<SceneComponent>

  /** List of new selected components. This is a list of new selection after mouse interaction. */
  private var newSelectedComponents: List<SceneComponent> = emptyList()

  /** List of initial positions of dragged components */
  private lateinit var initialPositions: List<Point>

  /** Offsets of every selected components. Their units are AndroidDpCoordinate */
  private lateinit var offsets: List<Point>

  /** Mouse position when start dragging. */
  @AndroidDpCoordinate private val firstMouse = Point(-1, -1)

  /**
   * The collected placeholder. Needs to be lazy because [myComponent] doesn't exist when
   * constructing the [Target]. But it will be set immediately after [Target] is created.
   */
  private lateinit var placeholders: List<Placeholder>
  private lateinit var dominatePlaceholders: List<Placeholder>
  private lateinit var recessivePlaceholders: List<Placeholder>

  /** Host of placeholders. This is used for rendering. */
  private var placeholderHosts: Set<SceneComponent> = emptySet()

  private var currentSnappedPlaceholder: Placeholder? = null

  var insertType: InsertType = InsertType.MOVE

  /** To handle Live Rendering case. */
  private val targetSnapper = TargetSnapper()

  init {
    myComponent = sceneComponent
  }

  override fun setComponent(component: SceneComponent) {
    assert(myComponent == component)
    super.setComponent(component)
  }

  override fun layout(
    sceneTransform: SceneContext,
    @AndroidDpCoordinate l: Int,
    @AndroidDpCoordinate t: Int,
    @AndroidDpCoordinate r: Int,
    @AndroidDpCoordinate b: Int,
  ): Boolean {
    val left: Int
    val right: Int
    if (r - l < MIN_SIZE) {
      val d = (MIN_SIZE - (r - l)) / 2
      left = l - d
      right = r + d
    } else {
      left = l
      right = r
    }

    val top: Int
    val bottom: Int
    if (b - t < MIN_SIZE) {
      val d = (MIN_SIZE - (b - t)) / 2
      top = t - d
      bottom = b + d
    } else {
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
      list.addRect(
        sceneContext,
        myLeft,
        myTop,
        myRight,
        myBottom,
        if (mIsOver) JBColor.yellow else JBColor.green,
      )
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
              list.add(
                DrawSnappedPlaceholder(
                  ph.region.left,
                  ph.region.top,
                  ph.region.right,
                  ph.region.bottom,
                )
              )
            }
            ph.associatedComponent == snappedPlaceholder.associatedComponent -> {
              // Sibling of snapped Placeholder
              list.add(
                DrawSiblingsOfSnappedPlaceholder(
                  ph.region.left,
                  ph.region.top,
                  ph.region.right,
                  ph.region.bottom,
                )
              )
            }
            else -> Unit // Do nothing for Placeholders in different host
          }
        }
      }

      for (h in placeholderHosts) {
        // Render hosts
        if (h == snappedPlaceholder?.host) {
          list.add(DrawHoveredHost(h.drawLeft, h.drawTop, h.drawRight, h.drawBottom))
        } else {
          list.add(DrawNonHoveredHost(h.drawLeft, h.drawTop, h.drawRight, h.drawBottom))
        }
      }

      targetSnapper.renderSnappedNotches(list, sceneContext, myComponent)
    }
  }

  override fun getPreferenceLevel() = Target.DRAG_LEVEL

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    firstMouse.x = x
    firstMouse.y = y

    val scene = component.scene
    val selection = scene.selection
    val selectedSceneComponents = selection.mapNotNull { scene.getSceneComponent(it) }
    draggedComponents =
      if (myComponent !in selectedSceneComponents) {
        // In case the dragging is started without selecting first. This happens when dragging an
        // unselected component.
        listOf(component)
      } else {
        // Make sure myComponent is the first one which is interacted with user.
        // Note that myComponent may not be the first one in selection, since user may drag the
        // component which is not selected first.
        sequenceOf(component).plus(selectedSceneComponents.filterNot { it == myComponent }).toList()
      }

    placeholders =
      component.scene.getPlaceholders(component, draggedComponents).filter { it.host != component }

    val dominateBuilder = ImmutableList.builder<Placeholder>()
    val recessiveBuilder = ImmutableList.builder<Placeholder>()
    placeholders.forEach { (if (it.dominate) dominateBuilder else recessiveBuilder).add(it) }
    dominatePlaceholders = dominateBuilder.build()
    recessivePlaceholders = recessiveBuilder.build()

    placeholderHosts = placeholders.asSequence().map { it.host }.toSet()

    initialPositions = draggedComponents.map { Point(it.drawX, it.drawY) }
    offsets = draggedComponents.map { Point(-1, -1) }

    if (fromToolWindow) {
      if (!draggedComponents.isEmpty()) {
        // When dragging from Tool window, makes the primary dragged point is in the center of
        // widget.
        // We assign the offset here because the component is not really clicked.
        val primaryComponent = draggedComponents[0]
        offsets[0].x = primaryComponent.drawWidth / 2
        offsets[0].y = primaryComponent.drawHeight / 2

        val primaryX = primaryComponent.getDrawX(System.currentTimeMillis())
        val primaryY = primaryComponent.getDrawY(System.currentTimeMillis())

        // Calculate distance between primary and other components.
        for (i in 1 until draggedComponents.size) {
          offsets[i].x =
            offsets[0].x + primaryX - draggedComponents[i].getDrawX(System.currentTimeMillis())
          offsets[i].y =
            offsets[0].y + primaryY - draggedComponents[i].getDrawY(System.currentTimeMillis())
        }
      }
    } else {
      // Drag inside the component, leave the dragged point same as the clicked point.
      draggedComponents.forEachIndexed { index, sceneComponent ->
        offsets[index].x = x - sceneComponent.getDrawX(System.currentTimeMillis())
        offsets[index].y = y - sceneComponent.getDrawY(System.currentTimeMillis())
      }
    }
    currentSnappedPlaceholder = null

    targetSnapper.reset()
    targetSnapper.gatherNotches(myComponent)
  }

  override fun mouseDrag(
    @AndroidDpCoordinate x: Int,
    @AndroidDpCoordinate y: Int,
    unused: List<Target>,
    ignored: SceneContext,
  ) {
    draggedComponents.forEach { it.isDragging = true }
    snap(x, y)
    myComponent.scene.repaint()
  }

  /**
   * Snap the component to the [placeholders]. This function applies the snapped placeholder and
   * adjust the position of [myComponent]. When placeholder is overlapped, the higher [Region.level]
   * get snapped.
   */
  private fun snap(
    @AndroidDpCoordinate mouseX: Int,
    @AndroidDpCoordinate mouseY: Int,
  ): Placeholder? {
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

        if (ph.snap(SnappingInfo(left, top, right, bottom), retPoint)) {
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

    val ph = targetPlaceholder
    currentSnappedPlaceholder = ph

    val targetSnapperX = targetSnapper.trySnapHorizontal(snappedX).orElse(snappedX)
    val targetSnapperY = targetSnapper.trySnapVertical(snappedY).orElse(snappedY)

    // TODO: Makes Live Rendering works when dragging widget between different ViewGroups
    if (isPlaceholderLiveUpdatable(ph)) {
      // For Live Rendering in ConstraintLayout. Live Rendering only works when component is dragged
      // in the same ConstraintLayout
      draggedComponents.forEachIndexed { index, it ->
        val expectedX =
          if (index == 0) targetSnapperX else targetSnapperX + offsets[0].x - offsets[index].x
        val expectedY =
          if (index == 0) targetSnapperY else targetSnapperY + offsets[0].y - offsets[index].y
        var modification = ComponentModification(it.authoritativeNlComponent, "Dragging component")
        ph!!.updateLiveAttribute(it, modification, expectedX, expectedY)
        modification.apply()
      }

      if (myComponent.scene.isLiveRenderingEnabled) {
        myComponent.authoritativeNlComponent.fireLiveChangeEvent()
      }
      myComponent.scene.markNeedsLayout(Scene.IMMEDIATE_LAYOUT)
    } else {
      if (currentSnappedPlaceholder?.dominate == true) {
        draggedComponents.forEach { it.setPosition(snappedX, snappedY) }
      } else {
        draggedComponents.forEachIndexed { index, it ->
          it.setPosition(mouseX - offsets[index].x, mouseY - offsets[index].y)
        }
      }
      myComponent.scene.markNeedsLayout(Scene.NO_LAYOUT)
    }
    return ph
  }

  override fun mouseRelease(
    @AndroidDpCoordinate x: Int,
    @AndroidDpCoordinate y: Int,
    unused: List<Target>,
  ) {
    if (!myComponent.isDragging) {
      val isClicked = abs(x - firstMouse.x) <= 1 && abs(y - firstMouse.y) <= 1
      if (isClicked) {
        // If the component is not being dragged and the mouse position almost not changed,
        // it means that the user clicked the component without dragging.
        newSelectedComponents = listOf(myComponent)
      }
    } else {
      draggedComponents.forEach { it.isDragging = false }
      val ph = snap(x, y)
      if (ph != null) {
        // TODO: Makes Notch works when dragging from other layouts to Constraint Layout.
        if (ToggleAutoConnectAction.isAutoconnectOn()) {
          targetSnapper.applyNotches(
            draggedComponents[0].authoritativeNlComponent.startAttributeTransaction()
          )
        }
        applyPlaceholder(ph)
      } else {
        draggedComponents.forEachIndexed { index, sceneComponent ->
          sceneComponent.setPosition(
            firstMouse.x - offsets[index].x,
            firstMouse.y - offsets[index].y,
          )
        }
        if (myComponent.scene.isLiveRenderingEnabled) {
          draggedComponents.forEach {
            if (it.authoritativeNlComponent.startAttributeTransaction().rollback()) {
              it.authoritativeNlComponent.fireLiveChangeEvent()
            }
          }
        }
      }
      newSelectedComponents = draggedComponents
    }
    handleRemainingComponentsOnRelease()
    draggedComponents = emptyList()
    currentSnappedPlaceholder = null
    placeholderHosts = emptySet()
  }

  /**
   * Apply the given [Placeholder]. Returns true if succeed, false otherwise. write to file
   * directly.
   */
  @VisibleForTesting
  fun applyPlaceholder(placeholder: Placeholder) {
    val parent = placeholder.host.authoritativeNlComponent
    val primaryNlComponent = myComponent.authoritativeNlComponent
    val model = primaryNlComponent.model
    val componentsToAdd = draggedComponents.map { it.authoritativeNlComponent }
    val anchor = placeholder.findNextSibling(myComponent, placeholder.host)?.nlComponent

    val attributesTransactions =
      draggedComponents.map {
        val modification = ComponentModification(it.authoritativeNlComponent, "Drag component")
        if (!isPlaceholderLiveUpdatable(placeholder) || (placeholder is MotionLayoutPlaceholder)) {
          placeholder.updateAttribute(it, modification)
        }
        modification
      }

    model.addComponentsAndSelectedIfCreated(
      componentsToAdd,
      parent,
      anchor,
      insertType,
      myComponent.scene.designSurface.selectionModel,
    ) {
      attributesTransactions.forEach { it.commit() }
      myComponent.scene.markNeedsLayout(Scene.IMMEDIATE_LAYOUT)
    }
  }

  /** Function to check if the attribute is updated during dragging. */
  private fun isPlaceholderLiveUpdatable(placeholder: Placeholder?) =
    placeholder != null &&
      placeholder.isLiveUpdatable &&
      placeholder.host == myComponent.parent &&
      myComponent !is TemporarySceneComponent

  /** Apply any pending transactions on mouse released. */
  private fun handleRemainingComponentsOnRelease() {
    val components =
      draggedComponents.mapNotNull { draggedComponent ->
        // We only need to apply changes if there are any pending.
        val nlComponent = draggedComponent.authoritativeNlComponent
        if (nlComponent.attributeTransaction?.hasPendingChanges() == true) nlComponent else null
      }
    if (components.isNotEmpty()) {
      NlWriteCommandActionUtil.run(components, "Drag component") {
        for (component in components) {
          component.attributeTransaction?.commit()
        }
      }
    }
  }

  /** Reset the status when the dragging is canceled. */
  override fun mouseCancel() {
    draggedComponents.forEach { it.isDragging = false }
    placeholderHosts = emptySet()
    currentSnappedPlaceholder = null
    val liveRendered = myComponent.scene.isLiveRenderingEnabled
    draggedComponents.forEachIndexed { index, component ->
      component.isDragging = false
      // Rollback the transaction. Some attributes may be changed due to live rendering.
      val nlComponent = component.authoritativeNlComponent
      if (nlComponent.startAttributeTransaction().rollback()) {
        // Has pending value means it has live change, fire live change event since it is changed
        // back.
        nlComponent.fireLiveChangeEvent()
      }
    }
    newSelectedComponents = draggedComponents
    draggedComponents.forEach { it.authoritativeNlComponent.clearTransaction() }
    draggedComponents = emptyList()
    myComponent.scene.markNeedsLayout(Scene.ANIMATED_LAYOUT)
  }

  override fun newSelection(): List<SceneComponent> = newSelectedComponents

  override fun getMouseCursor(@JdkConstants.InputEventMask modifiersEx: Int): Cursor? {
    return if ((modifiersEx and InputEvent.ALT_DOWN_MASK) != 0 && myComponent.isSelected) {
      AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.MOVE)
    } else {
      Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }
  }

  override fun isHittable() =
    if (myComponent.isSelected) myComponent.canShowBaseline() || !myComponent.isDragging else true
}

private abstract class BasePlaceholderDrawRegion(
  @AndroidDpCoordinate private val x1: Int,
  @AndroidDpCoordinate private val y1: Int,
  @AndroidDpCoordinate private val x2: Int,
  @AndroidDpCoordinate private val y2: Int,
) : DrawRegion() {

  final override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val defColor = g.color
    val defStroke = g.stroke
    val defClip = g.clip

    g.clip = sceneContext.renderableBounds

    setBounds(
      sceneContext.getSwingXDip(x1.toFloat()),
      sceneContext.getSwingYDip(y1.toFloat()),
      sceneContext.getSwingDimensionDip((x2 - x1).toFloat()),
      sceneContext.getSwingDimensionDip((y2 - y1).toFloat()),
    )

    getBackgroundColor(sceneContext.colorSet)?.let {
      g.color = it
      g.fill(this)
    }

    g.stroke = getBorderStroke(sceneContext.colorSet)
    g.color = getBorderColor(sceneContext.colorSet)
    g.draw(this)

    g.color = defColor
    g.stroke = defStroke
    g.clip = defClip
  }

  abstract fun getBackgroundColor(colorSet: ColorSet): Color?

  abstract fun getBorderColor(colorSet: ColorSet): Color

  abstract fun getBorderStroke(colorSet: ColorSet): Stroke
}

private class DrawSnappedPlaceholder(
  @AndroidDpCoordinate x1: Int,
  @AndroidDpCoordinate y1: Int,
  @AndroidDpCoordinate x2: Int,
  @AndroidDpCoordinate y2: Int,
) : BasePlaceholderDrawRegion(x1, y1, x2, y2) {

  override fun getBackgroundColor(colorSet: ColorSet): Color? = colorSet.dragReceiverBackground

  override fun getBorderColor(colorSet: ColorSet): Color = colorSet.dragReceiverFrames

  override fun getBorderStroke(colorSet: ColorSet): Stroke = colorSet.dragReceiverStroke
}

private class DrawSiblingsOfSnappedPlaceholder(
  @AndroidDpCoordinate x1: Int,
  @AndroidDpCoordinate y1: Int,
  @AndroidDpCoordinate x2: Int,
  @AndroidDpCoordinate y2: Int,
) : BasePlaceholderDrawRegion(x1, y1, x2, y2) {

  override fun getBackgroundColor(colorSet: ColorSet): Color? =
    colorSet.dragReceiverSiblingBackground

  override fun getBorderColor(colorSet: ColorSet): Color = colorSet.dragReceiverSiblingBackground

  override fun getBorderStroke(colorSet: ColorSet): Stroke = colorSet.dragReceiverSiblingStroke
}

private class DrawHoveredHost(
  @AndroidDpCoordinate x1: Int,
  @AndroidDpCoordinate y1: Int,
  @AndroidDpCoordinate x2: Int,
  @AndroidDpCoordinate y2: Int,
) : BasePlaceholderDrawRegion(x1, y1, x2, y2) {

  override fun getBackgroundColor(colorSet: ColorSet): Color? = null

  override fun getBorderColor(colorSet: ColorSet): Color = colorSet.dragReceiverFrames

  override fun getBorderStroke(colorSet: ColorSet): Stroke = colorSet.dragReceiverStroke
}

private class DrawNonHoveredHost(
  @AndroidDpCoordinate x1: Int,
  @AndroidDpCoordinate y1: Int,
  @AndroidDpCoordinate x2: Int,
  @AndroidDpCoordinate y2: Int,
) : BasePlaceholderDrawRegion(x1, y1, x2, y2) {

  override fun getBackgroundColor(colorSet: ColorSet): Color? = null

  override fun getBorderColor(colorSet: ColorSet): Color = colorSet.dragOtherReceiversFrame

  override fun getBorderStroke(colorSet: ColorSet): Stroke = colorSet.dragReceiverSiblingStroke
}
