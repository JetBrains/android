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
package com.android.tools.idea.naveditor.scene

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.DefaultHitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker

/*
  Augments the hit region for destinations to include the header above the destination
 */
open class NavDestinationHitProvider : DefaultHitProvider() {
  override fun addHit(component: SceneComponent, sceneTransform: SceneContext, picker: ScenePicker) {
    super.addHit(component, sceneTransform, picker)

    val sceneView = sceneTransform.surface?.currentSceneView ?: return
    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneView, component.fillDrawRect2D(0, null))

    val headerRect = getHeaderRect(sceneView, drawRectangle)

    val x1 = headerRect.x
    val x2 = x1 + headerRect.width
    val y1 = headerRect.y
    val y2 = y1 + headerRect.height

    picker.addRect(component, 0, x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
  }
}
