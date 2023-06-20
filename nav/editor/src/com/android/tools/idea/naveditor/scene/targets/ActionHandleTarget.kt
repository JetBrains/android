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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.adtui.common.SwingPoint
import com.android.tools.adtui.common.SwingX
import com.android.tools.adtui.common.SwingY
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.common.model.AndroidLength
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.times
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.inlineScale
import com.android.tools.idea.common.scene.target.BaseTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.model.createAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isFragment
import com.android.tools.idea.naveditor.scene.ACTION_HANDLE_OFFSET
import com.android.tools.idea.naveditor.scene.INNER_RADIUS_LARGE
import com.android.tools.idea.naveditor.scene.INNER_RADIUS_SMALL
import com.android.tools.idea.naveditor.scene.NavColors.HIGHLIGHTED_FRAME
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import com.android.tools.idea.naveditor.scene.OUTER_RADIUS_LARGE
import com.android.tools.idea.naveditor.scene.OUTER_RADIUS_SMALL
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandle
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandleDrag
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Computable
import org.intellij.lang.annotations.JdkConstants
import java.awt.Cursor
import kotlin.math.absoluteValue

private const val DURATION = 200
private const val DRAG_CREATE_IN_PROGRESS = "DRAG_CREATE_IN_PROGRESS"

fun isDragCreateInProgress(component: NlComponent) = component.parent?.getClientProperty(DRAG_CREATE_IN_PROGRESS) != null

/**
 * [ActionHandleTarget] is a target for handling drag-creation of actions.
 * It appears as a circular grab handle on the right side of the navigation screen.
 */
class ActionHandleTarget(component: SceneComponent) : BaseTarget() {

  private var handleState: HandleState = HandleState.INVISIBLE
  private var isDragging = false

  private enum class HandleState(val innerRadius: AndroidLength, val outerRadius: AndroidLength) {
    INVISIBLE(AndroidLength(0f), AndroidLength(0f)),
    SMALL(INNER_RADIUS_SMALL, OUTER_RADIUS_SMALL),
    LARGE(INNER_RADIUS_LARGE, OUTER_RADIUS_LARGE)
  }

  init {
    setComponent(component)
    handleState = calculateState()
  }

  override fun getPreferenceLevel() = Target.ANCHOR_LEVEL

  override fun layout(sceneTransform: SceneContext,
                      @NavCoordinate l: Int,
                      @NavCoordinate t: Int,
                      @NavCoordinate r: Int,
                      @NavCoordinate b: Int): Boolean {
    @NavCoordinate var centerX = r
    if (component.nlComponent.isFragment) {
      centerX += ACTION_HANDLE_OFFSET.value.toInt()
    }
    @NavCoordinate val centerY = t + (b - t) / 2
    @NavCoordinate val radius = handleState.outerRadius.value.toInt()

    myLeft = (centerX - radius).toFloat()
    myTop = (centerY - radius).toFloat()
    myRight = (centerX + radius).toFloat()
    myBottom = (centerY + radius).toFloat()

    return false
  }

  override fun mouseDown(@NavCoordinate x: Int, @NavCoordinate y: Int) {
    val scene = myComponent.scene
    scene.designSurface.selectionModel.setSelection(listOf(component.nlComponent))
    isDragging = true
    scene.needsRebuildList()
    myComponent.parent?.nlComponent?.putClientProperty(DRAG_CREATE_IN_PROGRESS, true)
    component.isDragging = true
    scene.repaint()
  }

  override fun mouseRelease(@NavCoordinate x: Int,
                            @NavCoordinate y: Int,
                            closestTargets: List<Target>) {
    isDragging = false
    val scene = myComponent.scene
    myComponent.parent?.nlComponent?.removeClientProperty(DRAG_CREATE_IN_PROGRESS)
    component.isDragging = false
    scene.findComponent(SceneContext.get(component.scene.sceneManager.sceneView), x, y)?.let { closestComponent ->
      if (closestComponent !== component.scene.root && !closestComponent.id.isNullOrEmpty()) {
        createAction(closestComponent)?.let { action ->
          NavUsageTracker.getInstance(action.model).createEvent(NavEditorEvent.NavEditorEventType.CREATE_ACTION)
            .withActionInfo(action)
            .withSource(NavEditorEvent.Source.DESIGN_SURFACE)
            .log()
          component.scene.designSurface.selectionModel.setSelection(listOf(action))
        }
      }
    }

    scene.needsRebuildList()
  }

  /* When true, this causes Scene.mouseRelease to change the selection to the associated SceneComponent.
  We don't have SceneComponents for actions so we need to return false here and set the selection to the
  correct NlComponent in Scene.mouseRelease */
  override fun canChangeSelection() = false

  private fun createAction(destination: SceneComponent): NlComponent? {
    if (mIsOver) {
      return null
    }

    val destinationNlComponent = destination.nlComponent

    if (!destinationNlComponent.isDestination) {
      return null
    }

    val nlComponent = component.nlComponent
    return WriteCommandAction.runWriteCommandAction(
      nlComponent.model.project,
      Computable<NlComponent?> {
        nlComponent.createAction(destinationNlComponent.id)
      })
  }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    val newState = calculateState()

    if (newState == HandleState.INVISIBLE && handleState == HandleState.INVISIBLE) {
      // we can return early if we know we're never going to draw anything
      return
    }

    val view = myComponent.scene.designSurface.focusedSceneView ?: return

    val centerX = SwingX(Coordinates.getSwingXDip(view, centerX))
    val centerY = SwingY(Coordinates.getSwingYDip(view, centerY))
    val center = SwingPoint(centerX, centerY)

    val scale = sceneContext.inlineScale
    val initialOuterRadius = handleState.outerRadius * scale
    val finalOuterRadius = newState.outerRadius * scale
    val initialInnerRadius = handleState.innerRadius * scale
    val finalInnerRadius = newState.innerRadius * scale

    val duration = (DURATION * (handleState.outerRadius - newState.outerRadius) / OUTER_RADIUS_LARGE).absoluteValue.toInt()

    val outerColor = primaryPanelBackground
    val innerColor = if (component.isSelected) SELECTED else HIGHLIGHTED_FRAME

    if (isDragging) {
      list.add(DrawActionHandleDrag(center, initialOuterRadius, finalOuterRadius,
                                    finalInnerRadius, duration))
    }
    else {
      list.add(DrawActionHandle(center, initialOuterRadius, finalOuterRadius,
                                initialInnerRadius, finalInnerRadius, duration, innerColor, outerColor))
    }

    handleState = newState
  }

  override fun addHit(transform: SceneContext,
                      picker: ScenePicker,
                      @JdkConstants.InputEventMask modifiersEx: Int) {
    @SwingCoordinate val centerX = transform.getSwingX(centerX.toInt())
    @SwingCoordinate val centerY = transform.getSwingY(centerY.toInt())
    picker.addCircle(this, 0, centerX, centerY, transform.getSwingDimension(OUTER_RADIUS_LARGE.toInt()))
  }

  override fun getMouseCursor(@JdkConstants.InputEventMask modifiersEx: Int) = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

  private fun calculateState(): HandleState {
    return when {
      isDragging -> HandleState.SMALL
      myComponent.scene.designSurface.guiInputHandler.isInteractionInProgress -> HandleState.INVISIBLE
      mIsOver -> HandleState.LARGE
      component.drawState == SceneComponent.DrawState.HOVER -> HandleState.SMALL
      component.isSelected && myComponent.scene.selection.size == 1 -> HandleState.SMALL
      else -> HandleState.INVISIBLE
    }
  }
}
