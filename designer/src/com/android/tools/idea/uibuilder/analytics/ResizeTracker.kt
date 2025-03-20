/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.analytics

import com.android.tools.idea.common.scene.SceneManager
import com.intellij.openapi.extensions.ExtensionPointName

/** Interface for tracking resize events. */
interface ResizeTracker {
  companion object {
    val EP_NAME =
      ExtensionPointName.create<ResizeTracker>(
        "com.android.tools.idea.uibuilder.analytics.resizeTracker"
      )

    @JvmStatic
    fun getTracker(sceneManager: SceneManager): ResizeTracker? {
      return EP_NAME.findFirstSafe { it.isApplicable(sceneManager) }
    }
  }

  /**
   * Returns true if this tracker is applicable to the given [SceneManager].
   *
   * @param sceneManager The [SceneManager] to check.
   */
  fun isApplicable(sceneManager: SceneManager): Boolean

  /**
   * Reports when the user stops resizing the preview.
   *
   * @param sceneManager The [SceneManager] that is being resized.
   * @param stoppedDeviceWidthDp The width when the user stopped resizing.
   * @param stoppedDeviceHeightDp The height when the user stopped resizing.
   */
  fun reportResizeStopped(
    sceneManager: SceneManager,
    stoppedDeviceWidthDp: Int,
    stoppedDeviceHeightDp: Int,
  )
}
