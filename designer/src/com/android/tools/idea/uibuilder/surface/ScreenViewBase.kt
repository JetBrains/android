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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.ShapePolicy
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.rendering.RenderResult

/** View of a device/screen/layout. This is actually painted by [ScreenViewLayer]. */
internal abstract class ScreenViewBase
protected constructor(
  surface: NlDesignSurface,
  manager: LayoutlibSceneManager,
  shapePolicy: ShapePolicy,
) : SceneView(surface, manager, shapePolicy) {

  override val sceneManager: LayoutlibSceneManager
    get() = super.sceneManager as LayoutlibSceneManager

  override val surface: NlDesignSurface
    get() = super.surface as NlDesignSurface

  val result: RenderResult?
    get() = sceneManager.renderResult

  /**
   * True if this is second [SceneView] in the associated Scene/SceneManager, false otherwise. The
   * default value is false.
   */
  var isSecondary: Boolean = false
}
