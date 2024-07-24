/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wear.preview

import com.android.flags.ifEnabled
import com.android.tools.idea.common.surface.DEVICE_CONFIGURATION_SHAPE_POLICY
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.SceneLayer
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider
import com.android.tools.idea.uibuilder.surface.layer.ClassLoadingDebugLayer
import com.android.tools.idea.uibuilder.surface.layer.DiagnosticsLayer
import com.android.tools.idea.uibuilder.surface.sizepolicy.ImageContentSizePolicy
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.LayoutEditorState

/** [ScreenViewProvider] for Wear Tile preview. */
internal val WEAR_TILE_SCREEN_VIEW_PROVIDER =
  object : ScreenViewProvider {
    override val displayName: String = "Wear Tile"
    override var colorBlindFilter: ColorBlindMode = ColorBlindMode.NONE

    override fun createPrimarySceneView(
      surface: NlDesignSurface,
      manager: LayoutlibSceneManager,
    ): ScreenView =
      ScreenView.newBuilder(surface, manager)
        .withLayersProvider {
          ImmutableList.builder<Layer>()
            .apply {
              add(ScreenViewLayer(it, colorBlindFilter, surface, surface::rotateSurfaceDegree))
              add(SceneLayer(surface, it, false).apply { isShowOnHover = true })
              StudioFlags.NELE_CLASS_PRELOADING_DIAGNOSTICS.ifEnabled {
                add(ClassLoadingDebugLayer(surface.models.first().facet.module))
              }
              StudioFlags.NELE_RENDER_DIAGNOSTICS.ifEnabled {
                add(DiagnosticsLayer(surface, surface.project))
              }
            }
            .build()
        }
        .withShapePolicy(DEVICE_CONFIGURATION_SHAPE_POLICY)
        .decorateContentSizePolicy { policy -> ImageContentSizePolicy(policy) }
        .build()

    override val surfaceType: LayoutEditorState.Surfaces = LayoutEditorState.Surfaces.SCREEN_SURFACE
  }
