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
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.scene.*
import com.android.tools.idea.naveditor.model.isFragment

class NavDestinationHitProvider : DefaultHitProvider() {
  override fun addHit(component: SceneComponent, sceneTransform: SceneContext, picker: ScenePicker) {
    super.addHit(component, sceneTransform, picker)

    @AndroidDpCoordinate val rect = component.fillRect(null)

    @SwingCoordinate var x = sceneTransform.getSwingXDip((rect.x + rect.width).toFloat())
    if (component.nlComponent.isFragment) {
      x += sceneTransform.getSwingDimension(ACTION_HANDLE_OFFSET)
    }

    @SwingCoordinate val y = sceneTransform.getSwingYDip(rect.y + rect.height / 2f)
    @SwingCoordinate val r = sceneTransform.getSwingDimensionDip(OUTER_RADIUS_LARGE.toFloat())
    picker.addCircle(component, 0, x, y, r)
  }
}
