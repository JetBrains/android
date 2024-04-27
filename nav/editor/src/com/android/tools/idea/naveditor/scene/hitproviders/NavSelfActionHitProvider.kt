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
import com.android.tools.adtui.common.SwingPoint
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.idea.common.model.Coordinates.getSwingRectDip
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.inlineScale
import com.android.tools.idea.naveditor.scene.getSelfActionIconRect
import com.android.tools.idea.naveditor.scene.selfActionPoints
import java.awt.Rectangle

object NavSelfActionHitProvider : NavActionHitProviderBase() {
  override fun addShapeHit(component: SceneComponent, sceneTransform: SceneContext, picker: ScenePicker) {
    val source = sourceRectangle(component, sceneTransform) ?: return
    val points = selfActionPoints(source, sceneTransform.inlineScale)

    for (i in 1 until points.size) {
      picker.addLine(component, 0, points[i - 1].x.toInt(), points[i - 1].y.toInt(), points[i].x.toInt(), points[i].y.toInt(), 5)
    }
  }

  override fun iconRectangle(component: SceneComponent, sceneTransform: SceneContext): SwingRectangle? {
    val source = sourceRectangle(component, sceneTransform) ?: return null
    val scale = sceneTransform.inlineScale
    val points = selfActionPoints(source, scale)

    return getSelfActionIconRect(points[0], scale)
  }

  override fun intersects(component: SceneComponent, sceneTransform: SceneContext, @AndroidDpCoordinate rectangle: Rectangle): Boolean {
    val source = sourceRectangle(component, sceneTransform) ?: return false
    val points = selfActionPoints(source, sceneTransform.inlineScale)
    val bounds = SwingRectangle(getSwingRectDip(sceneTransform, rectangle))

    // Check whether any of the corners of the select action lie within the selection rectangle
    if (points.any { bounds.contains(it) }) {
      return true
    }

    val corners = arrayOf(SwingPoint(bounds.x, bounds.y),
                          SwingPoint(bounds.x + bounds.width, bounds.y),
                          SwingPoint(bounds.x + bounds.width, bounds.y + bounds.height),
                          SwingPoint(bounds.x, bounds.y + bounds.height),
                          SwingPoint(bounds.x, bounds.y))

    // Check whether any of the line segments making up the self action cross
    // any line segments making up the selection rectangle
    for (i in 0 until points.size - 1) {
      for (j in 0 until corners.size - 1) {
        if (intersects(points[i], points[i + 1], corners[j], corners[j + 1]))
          return true
      }
    }

    return false
  }

  /*
    Calculates whether the segment from p1 to p2 intersects the segment from p3 to p4
    Segment 1: p1 + (p2 - p1) * t1, 0 < t1 < 1
    Segment 2: p3 + (p4 - p3) * t2, 0 < t2 < 1
    Solve for t1 and and t2 and verify that each is between zero and one.
   */
  private fun intersects(p1: SwingPoint, p2: SwingPoint, p3: SwingPoint, p4: SwingPoint): Boolean {
    val a = (p2.x - p1.x).value
    val b = (p3.x - p4.x).value
    val c = (p3.x - p1.x).value
    val d = (p2.y - p1.y).value
    val e = (p3.y - p4.y).value
    val f = (p3.y - p1.y).value

    val det = a * e - b * d
    if (det < 0.001) {
      return false
    }

    return (c * e - b * f) / det in 0.0..1.0 &&
           (a * f - c * d) / det in 0.0..1.0
  }
}