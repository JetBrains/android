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
package com.android.tools.idea.naveditor.scene

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.naveditor.scene.draw.DrawColor

fun frameColor(component: SceneComponent): DrawColor {
  return when (component.drawState) {
    SceneComponent.DrawState.SELECTED -> DrawColor.SELECTED_FRAMES
    SceneComponent.DrawState.HOVER, SceneComponent.DrawState.DRAG -> DrawColor.HIGHLIGHTED_FRAMES
    else -> DrawColor.FRAMES
  }
}

@SwingCoordinate
fun strokeThickness(context: SceneContext, component: SceneComponent, @AndroidCoordinate borderThickness: Int): Int {
  return when (component.drawState) {
    SceneComponent.DrawState.SELECTED, SceneComponent.DrawState.HOVER, SceneComponent.DrawState.DRAG
    -> Coordinates.getSwingDimension(context, borderThickness)
    else -> 1
  }
}