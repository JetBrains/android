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
package com.android.tools.idea.naveditor.surface

import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.scene.draw.ColorSet
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.SceneLayer
import com.android.tools.idea.common.surface.SceneView
import com.google.common.collect.ImmutableList
import java.awt.Dimension
import com.android.tools.idea.common.surface.SQUARE_SHAPE_POLICY

/**
 * View of a navigation editor [Scene], as part of a [NavDesignSurface].
 */
class NavView(surface: NavDesignSurface, sceneManager: SceneManager) : SceneView(surface, sceneManager, SQUARE_SHAPE_POLICY) {
  override fun createLayers(): ImmutableList<Layer> = ImmutableList.of(SceneLayer(surface, this, true))

  override val contentTranslationX: Int
    get() =  -Coordinates.getSwingDimension(this, sceneManager.scene.root?.drawX ?: 0)

  override val contentTranslationY: Int
    get() =  -Coordinates.getSwingDimension(this, sceneManager.scene.root?.drawY ?: 0)

  override fun getContentSize(dimension: Dimension?): Dimension {
    val result = dimension ?: Dimension()

    result.height = Coordinates.dpToPx(this, scene.root?.drawHeight ?: 0)
    result.width = Coordinates.dpToPx(this, sceneManager.scene.root?.drawWidth ?: 0)
    return result
  }

  /**
   * This color set does not provide the navigation colors.
   * Navigation-specific colors are defined in NavColors.
   */
  override val colorSet = ColorSet()
}
