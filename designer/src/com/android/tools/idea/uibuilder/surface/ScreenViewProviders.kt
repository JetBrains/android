/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.SceneLayer
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.BlueprintColorSet
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.visual.ColorBlindModeScreenViewLayer
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.google.common.collect.ImmutableList

/**
 * Default provider that provider the [ScreenView] design surface only.
 */
internal fun defaultProvider(surface: NlDesignSurface,
                             manager: LayoutlibSceneManager,
                             @Suppress("UNUSED_PARAMETER") isSecondary: Boolean): ScreenView =
  ScreenView.newBuilder(surface, manager).resizeable().build()

internal fun blueprintProvider(surface: NlDesignSurface, manager: LayoutlibSceneManager, isSecondary: Boolean): ScreenView =
  ScreenView.newBuilder(surface, manager)
    .resizeable()
    .withColorSet(BlueprintColorSet())
    .withLayersProvider {
      ImmutableList.builder<Layer>().apply {
        if (it.hasBorderLayer()) {
          add(BorderLayer(it))
        }
        add(MockupLayer(it))
        if (!isSecondary) {
          add(CanvasResizeLayer(it.surface, it))
        }
        add(SceneLayer(it.surface, it, true))
      }.build()
    }
    .build()

/**
 * Returns a [ScreenView] for the multi-visualization.
 */
internal fun visualizationProvider(surface: NlDesignSurface,
                                   manager: LayoutlibSceneManager,
                                   @Suppress("UNUSED_PARAMETER") isSecondary: Boolean): ScreenView =
  ScreenView.newBuilder(surface, manager)
    .withLayersProvider {
      ImmutableList.builder<Layer>().apply {
        // Always has border in visualization tool.
        add(BorderLayer(it))
        add(ScreenViewLayer(it))
      }.build()
    }
    .disableBorder()
    .build()

/**
 * Returns appropriate mode based on model display name.
 */
private fun colorBlindMode(sceneManager: SceneManager): ColorBlindMode? {
  val model: NlModel = sceneManager.model
  for (mode in ColorBlindMode.values()) {
    if (mode.displayName == model.modelDisplayName) {
      return mode
    }
  }
  return null
}

/**
 * View for drawing color blind modes.
 */
internal fun colorBlindProvider(surface: NlDesignSurface,
                                manager: LayoutlibSceneManager,
                                @Suppress("UNUSED_PARAMETER") isSecondary: Boolean): ScreenView =
  ScreenView.newBuilder(surface, manager)
    .withLayersProvider {
      ImmutableList.builder<Layer>().apply {
        // Always has border in visualization tool.
        add(BorderLayer(it))
        val mode: ColorBlindMode? = colorBlindMode(manager)
        if (mode != null) {
          add(ColorBlindModeScreenViewLayer(it, mode))
        }
        else {
          // ERROR - at least show the original.
          add(ScreenViewLayer(it))
        }
      }.build()
    }
    .disableBorder()
    .build()

internal fun composeProvider(surface: NlDesignSurface,
                             manager: LayoutlibSceneManager,
                             @Suppress("UNUSED_PARAMETER") isSecondary: Boolean): ScreenView =
  ScreenView.newBuilder(surface, manager)
    .withLayersProvider {
      ImmutableList.builder<Layer>().apply {
        if (it.hasBorderLayer()) {
          add(BorderLayer(it))
        }
        add(ScreenViewLayer(it))
        add(SceneLayer(it.surface, it, false).apply {
          isShowOnHover = true
        })
      }.build()
    }
    .decorateContentSizePolicy { policy -> ScreenView.ImageContentSizePolicy(policy) }
    .build()