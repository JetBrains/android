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

import com.android.tools.adtui.common.SwingRectangle
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

  // TODO (b/148756121): Implement this
  override fun intersects(component: SceneComponent, rectangle: Rectangle) = false
}