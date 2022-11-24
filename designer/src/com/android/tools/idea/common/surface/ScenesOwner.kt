/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneManager

/**
 * Interface for a 2-D entity that owns [SceneView]s and corresponding [Scene]s.
 */
interface ScenesOwner {
  /**
   * Returns the [SceneView] under the given ([x], [y]) position if any or the focused one otherwise. The coordinates
   * are in the viewport view coordinate space, so they will not change with scrolling.
   */
  @Deprecated("Owner does not have a single primary SceneView", ReplaceWith("getSceneViewAt"))
  fun getSceneViewAtOrPrimary(@SwingCoordinate x: Int, @SwingCoordinate y: Int): SceneView?

  /** The [Scene] of the primary [SceneManager]. */
  @get:Deprecated("Owner can have multiple scenes", ReplaceWith("sceneManager.scene"))
  val scene: Scene?

  /**
   * Returns the current focused [SceneView] that is responsible for responding to mouse and keyboard events, or null
   * if there is no focused [SceneView].
   */
  val focusedSceneView: SceneView?

  /**
   * Return the [SceneView] under the given ([x], [y]) position. The coordinates are in the viewport view coordinate
   * space, so they will not change with scrolling.
   */
  fun getSceneViewAt(@SwingCoordinate x: Int, @SwingCoordinate y: Int): SceneView?
}