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

import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.TargetProvider
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.supportsActions

/**
 * [TargetProvider] for navigation screens.
 *
 * Notably, adds the actions going from this screen to others.
 */
object NavScreenTargetProvider : TargetProvider {

  override fun createTargets(sceneComponent: SceneComponent): List<Target> {
    if (!sceneComponent.nlComponent.isDestination || sceneComponent.parent == null) {
      return listOf()
    }

    val result = mutableListOf<Target>(ScreenDragTarget(sceneComponent))
    if (sceneComponent.nlComponent.supportsActions) {
      result.add(ActionHandleTarget(sceneComponent))
    }

    return result
  }
}
