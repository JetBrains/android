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
package com.android.tools.idea.common.layout

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.layout.option.SurfaceLayoutManager
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.layout.option.SingleDirectionLayoutManager

/**
 * Wrapper class to define the options available for layout.
 *
 * @param displayName Name to be shown for this option.
 * @param layoutManager [SurfaceLayoutManager] to switch to when this option is selected.
 * @param organizationEnabled if layout has organization
 * @param sceneViewAlignment scenes alignment
 */
data class SurfaceLayoutOption(
  val displayName: String,
  val layoutManager: SurfaceLayoutManager,
  val organizationEnabled: Boolean = false,
  val sceneViewAlignment: SceneViewAlignment = SceneViewAlignment.CENTER,
) {
  companion object {

    /** Distance between blueprint screen and regular screen */
    @SwingCoordinate private val SCREEN_DELTA: Int = 48

    val DEFAULT_OPTION =
      SurfaceLayoutOption(
        "Layout",
        SingleDirectionLayoutManager(
          NlConstants.DEFAULT_SCREEN_OFFSET_X,
          NlConstants.DEFAULT_SCREEN_OFFSET_Y,
          SCREEN_DELTA,
          SCREEN_DELTA,
          SingleDirectionLayoutManager.Alignment.CENTER,
        ),
      )
  }
}
