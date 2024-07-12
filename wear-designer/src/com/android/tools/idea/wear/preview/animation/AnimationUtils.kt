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
package com.android.tools.idea.wear.preview.animation

import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.wear.preview.WearTilePreviewElement

/**
 * Detects animations within an inflated Wear Tile view.
 *
 * It updates the associated [WearTilePreviewElement] with information about the detected animations
 * and the current instance of [TileServiceViewAdapter].
 */
fun detectAnimations(sceneManager: SceneManager) {
  if (!StudioFlags.WEAR_TILE_ANIMATION_INSPECTOR.get()) return
  val previewElementInstance =
    sceneManager.model.dataContext.getData(PREVIEW_ELEMENT_INSTANCE) as? WearTilePreviewElement<*>
      ?: return
  val tileServiceViewAdapter = sceneManager.scene.root?.nlComponent?.viewInfo?.viewObject
  previewElementInstance.tileServiceViewAdapter.value = tileServiceViewAdapter
  previewElementInstance.hasAnimations = StudioFlags.WEAR_TILE_ANIMATION_INSPECTOR.get()
}
