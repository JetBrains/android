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
package com.android.tools.idea.uibuilder.scene

import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.DefaultSceneManagerHierarchyProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneComponentHierarchyProvider
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w
import com.android.tools.idea.uibuilder.model.x
import com.android.tools.idea.uibuilder.model.y

/**
 * Default [SceneComponentHierarchyProvider] for [LayoutlibSceneManager]. It provides the
 * functionality to sync the [NlComponent] hierarchy and the data from Layoutlib to
 * [SceneComponent].
 */
class LayoutlibSceneManagerHierarchyProvider : DefaultSceneManagerHierarchyProvider() {
  override fun syncFromNlComponent(sceneComponent: SceneComponent) {
    super.syncFromNlComponent(sceneComponent)
    val component = sceneComponent.nlComponent
    val animate = sceneComponent.scene.isAnimated && !sceneComponent.hasNoDimension()
    val manager = sceneComponent.scene.sceneManager
    if (animate) {
      val time = System.currentTimeMillis()
      sceneComponent.setPositionTarget(
        Coordinates.pxToDp(manager, component.x),
        Coordinates.pxToDp(manager, component.y),
        time,
      )
      sceneComponent.setSizeTarget(
        Coordinates.pxToDp(manager, component.w),
        Coordinates.pxToDp(manager, component.h),
        time,
      )
    } else {
      sceneComponent.setPosition(
        Coordinates.pxToDp(manager, component.x),
        Coordinates.pxToDp(manager, component.y),
      )
      sceneComponent.setSize(
        Coordinates.pxToDp(manager, component.w),
        Coordinates.pxToDp(manager, component.h),
      )
    }
  }
}
