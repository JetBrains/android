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
package com.android.tools.idea.compose.preview.scene

import com.android.flags.ifEnabled
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.SceneLayer
import com.android.tools.idea.common.surface.SceneView.DEVICE_CONFIGURATION_SHAPE_POLICY
import com.android.tools.idea.common.surface.SceneView.SQUARE_SHAPE_POLICY
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.BorderLayer
import com.android.tools.idea.uibuilder.surface.ClassLoadingDebugLayer
import com.android.tools.idea.uibuilder.surface.DiagnosticsLayer
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.LayoutEditorState

internal val COMPOSE_SCREEN_VIEW_PROVIDER =
  object : ScreenViewProvider {
    override val displayName: String = "Compose"

    override fun createPrimarySceneView(
      surface: NlDesignSurface,
      manager: LayoutlibSceneManager
    ): ScreenView =
      ScreenView.newBuilder(surface, manager)
        .withLayersProvider {
          ImmutableList.builder<Layer>()
            .apply {
              if (it.hasBorderLayer()) {
                add(BorderLayer(it, true))
              }
              add(ScreenViewLayer(it))
              add(SceneLayer(it.surface, it, false).apply { isShowOnHover = true })
              StudioFlags.NELE_CLASS_PRELOADING_DIAGNOSTICS.ifEnabled {
                add(ClassLoadingDebugLayer(surface.models.first().facet.module))
              }
              StudioFlags.NELE_RENDER_DIAGNOSTICS.ifEnabled { add(DiagnosticsLayer(surface)) }
            }
            .build()
        }
        .withShapePolicy {
          (if (COMPOSE_PREVIEW_ELEMENT_INSTANCE.getData(manager.model.dataContext)
                ?.displaySettings
                ?.showDecoration == true
            )
              DEVICE_CONFIGURATION_SHAPE_POLICY
            else SQUARE_SHAPE_POLICY)
            .getShape(it)
        }
        .decorateContentSizePolicy { policy -> ScreenView.ImageContentSizePolicy(policy) }
        .build()

    override val surfaceType: LayoutEditorState.Surfaces = LayoutEditorState.Surfaces.SCREEN_SURFACE
  }
