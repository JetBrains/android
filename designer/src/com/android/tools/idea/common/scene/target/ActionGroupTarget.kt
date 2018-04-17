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
import java.awt.Rectangle
import java.util.stream.Stream

@SwingCoordinate
private val SWING_GAP = 6
@SwingCoordinate
private val SWING_DIMENSION = 25

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
    val top = b + gap
    val bottom = top + size
    var left = l
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
    myRight = right.toFloat()

    actionsBounds.setSize(context.getSwingDimensionDip((right - l).toFloat()),
                          context.getSwingDimensionDip((bottom - top).toFloat()))
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
