/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.scene

import com.android.tools.idea.common.model.NlComponent

/** Provider mapping [NlComponent]s to [SceneComponent]/ */
interface SceneComponentHierarchyProvider {
  /**
   * Called by the [SceneManager] to create the initially [SceneComponent] hierarchy from the given
   * [NlComponent].
   */
  fun createHierarchy(manager: SceneManager, component: NlComponent): List<SceneComponent>

  /**
   * Call by the [SceneManager] to trigger a sync of the [NlComponent] to the given
   * [SceneComponent]. This allows for the SceneComponent to sync the latest data from the [NlModel]
   * and update the UI representation. The method will be called when the [SceneManager] detects
   * that there is the need to sync. This could be after a render or after a model change, for
   * example.
   */
  fun syncFromNlComponent(sceneComponent: SceneComponent)
}
