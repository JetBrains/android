/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.hitproviders

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.inlineDrawRect
import com.android.tools.idea.common.scene.inlineScale
import com.android.tools.idea.naveditor.model.effectiveDestinationId
import com.android.tools.idea.naveditor.scene.getCurvePoints
import com.android.tools.idea.naveditor.scene.getRegularActionIconRect
import java.awt.Rectangle

private const val STEPS = 30

object NavRegularActionHitProvider : NavActionHitProviderBase() {
  override fun addShapeHit(component: SceneComponent, sceneTransform: SceneContext, picker: ScenePicker) {
    val source = sourceRectangle(component, sceneTransform) ?: return
    val destination = destinationRectangle(component, sceneTransform) ?: return

    val (p1, p2, p3, p4) = getCurvePoints(source, destination, sceneTransform.inlineScale)
    picker.addCurveTo(component, 0, p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), p3.x.toInt(), p3.y.toInt(),
                      p4.x.toInt(), p4.y.toInt(), 10)

  }

  override fun iconRectangle(component: SceneComponent, sceneTransform: SceneContext): SwingRectangle? {
    val source = sourceRectangle(component, sceneTransform) ?: return null
    val destination = destinationRectangle(component, sceneTransform) ?: return null

    return getRegularActionIconRect(source, destination, sceneTransform.inlineScale)
  }

  private fun destinationRectangle(component: SceneComponent, sceneTransform: SceneContext): SwingRectangle? {
    val destinationId = component.nlComponent.effectiveDestinationId ?: return null
    val destination = component.scene.root?.getSceneComponent(destinationId) ?: return null
    return destination.inlineDrawRect(sceneTransform)
  }

  override fun intersects(component: SceneComponent, sceneTransform: SceneContext, @AndroidDpCoordinate rectangle: Rectangle): Boolean {
    val source = sourceRectangle(component, sceneTransform) ?: return false
    val destination = destinationRectangle(component, sceneTransform) ?: return false

    // Check whether the bounding box of the curve intersects the lasso rectangle
    val bounds = SwingRectangle(Coordinates.getSwingRectDip(sceneTransform, rectangle))
    val points = getCurvePoints(source, destination, sceneTransform.inlineScale)
    if (!points.bounds().intersects(bounds)) {
      return false
    }

    // Walk along the curve and check whether the current point is inside the lasso rectangle
    var t = 0f
    while (t <= 1f) {
      if (bounds.contains(points.curvePoint(t))) {
        return true
      }

      t += 1f / STEPS
    }

    return false
  }
}