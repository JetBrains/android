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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.TargetProvider
import com.android.tools.idea.common.scene.target.LassoTarget
import com.android.tools.idea.common.scene.target.Target

/**
 * Providers targets for the current display root of the navigation editor.
 */
class NavigationTargetProvider : TargetProvider {
  override fun createTargets(sceneComponent: SceneComponent): List<Target> {
    return listOf(LassoTarget(true, false))
  }
}
