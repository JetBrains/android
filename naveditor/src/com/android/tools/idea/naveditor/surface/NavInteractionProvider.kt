/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.naveditor.surface

import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionProvider

class NavInteractionProvider(private val surface: DesignSurface): InteractionProvider {

  override fun createInteractionOnClick(mouseX: Int, mouseY: Int): Interaction? {
    val sceneView = surface.getSceneView(mouseX, mouseY) ?: return null
    return SceneInteraction(sceneView);
  }

  override fun createInteractionOnDrag(draggedSceneComponent: SceneComponent, primarySceneComponent: SceneComponent?): Interaction? = null
}
