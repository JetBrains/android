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

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.draw.DisplayList
import com.intellij.util.ui.JBUI.scale
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

@SwingCoordinate
private val SWING_GAP = scale(6)
@SwingCoordinate
private val SWING_DIMENSION = scale(25)

/**
 * [ActionGroupTarget] lays out [ActionTarget] and handle their visibility
 * depending on mouse position and the selected state of the holding [SceneComponent].
 */
class ActionGroupTarget(component: SceneComponent) : BaseTarget() {

  private val componentBounds = Rectangle()

  private val actionsBounds = Rectangle()

  private val _actionTargets = mutableListOf<ActionTarget>()
  val actionTargets: List<ActionTarget>
    get() = _actionTargets.toList()

  init {
    setComponent(component)
  }

  override fun getPreferenceLevel() = Target.ACTION_LEVEL

  override fun layout(context: SceneContext, l: Int, t: Int, r: Int, b: Int): Boolean {
    val size = getAndroidDpSize(SWING_DIMENSION)
    val gap = getAndroidDpSize(SWING_GAP)
    val sceneView = myComponent.scene.sceneManager.sceneView
    // Make sure the targets are not painted below the bottom of the scene view
    val bottom = min(b + gap + size, Coordinates.getAndroidYDip(sceneView, sceneView.y + sceneView.size.height))
    val top = bottom - size

    // Make sure the targets are not painted outside of the scene view to the left
    var left = max(l, 0)
    var right = left
    myLeft = left.toFloat()
    myTop = top.toFloat()
    myBottom = bottom.toFloat()
    actionsBounds.setLocation(context.getSwingXDip(left.toFloat()),
                              context.getSwingYDip(top.toFloat()))
    _actionTargets
      .filter { it.isVisible }
      .forEach {
        right = left + size
        it.layout(context, left, top, right, bottom)
        if (it.isVisible) {
          left += size + gap
        }
      }

    // If the targets go over to the right, redo the layout starting more to the left so that all the targets fit
    // This cannot be pre-computed as some of the targets become invisible when their layout() is called
    val rightBorder = Coordinates.getAndroidXDip(sceneView, sceneView.x + sceneView.size.width)
    if (right > rightBorder) {
      left = l - (right - rightBorder)
      right = left
      actionTargets
        .filter { it.isVisible }
        .forEach {
          right = left + size
          it.layout(context, left, top, right, bottom)
          if (it.isVisible) {
            left += size + gap
          }
        }
    }
    myRight = right.toFloat()

    actionsBounds.setSize(context.getSwingDimensionDip(myRight - myLeft),
                          context.getSwingDimensionDip(myBottom - myTop))
    componentBounds.setBounds(
      context.getSwingXDip(myComponent.getDrawX(System.currentTimeMillis()).toFloat()),
      context.getSwingYDip(myComponent.getDrawY(System.currentTimeMillis()).toFloat()),
      context.getSwingDimensionDip(myComponent.getDrawWidth(System.currentTimeMillis()).toFloat()),
      context.getSwingDimensionDip(myComponent.getDrawHeight(System.currentTimeMillis()).toFloat()))

    return false
  }

  @AndroidDpCoordinate
  private fun getAndroidDpSize(@SwingCoordinate dimension: Int) =
    Coordinates.getAndroidDimensionDip(myComponent.scene.sceneManager.sceneView, dimension)

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    if (componentBounds.contains(sceneContext.mouseX, sceneContext.mouseY)
        || actionsBounds.contains(sceneContext.mouseX, sceneContext.mouseY)) {
      _actionTargets.filter { it.isVisible }.forEach { it.render(list, sceneContext) }
    }
  }

  override fun setComponent(component: SceneComponent) {
    super.setComponent(component)
    _actionTargets.forEach { it.component = component }
  }

  override fun addHit(transform: SceneContext, picker: ScenePicker) =
    _actionTargets.filter { it.isVisible }.forEach { it.addHit(transform, picker) }


  override fun mouseRelease(x: Int, y: Int, closestTargets: MutableList<Target>) {
    _actionTargets.firstOrNull {
      x >= it.myLeft && x <= it.myRight
      && y >= it.myTop && y <= it.myBottom
    }?.also { mouseRelease(x, y, closestTargets) }
  }

  fun addAction(action: ActionTarget) {
    _actionTargets += action
  }

  fun clear() = _actionTargets.clear()
}
