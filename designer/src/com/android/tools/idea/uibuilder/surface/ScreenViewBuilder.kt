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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.scene.draw.ColorSet
import com.android.tools.idea.common.surface.DEVICE_CONFIGURATION_SHAPE_POLICY
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.ShapePolicy
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.AndroidColorSet
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.ScreenView.DEFAULT_LAYERS_PROVIDER
import com.android.tools.idea.uibuilder.surface.sizepolicy.ContentSizePolicy
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.google.common.collect.ImmutableList
import java.util.function.Function

/** A [ScreenView] builder. */
class ScreenViewBuilder(val surface: NlDesignSurface, private val manager: LayoutlibSceneManager) {
  private var isResizeable: Boolean = false
  private var hasBorderLayer: Boolean = manager.model.type is LayoutEditorFileType
  private var colorSet: ColorSet? = null
  private var layersProvider: Function<ScreenView, ImmutableList<Layer>> = DEFAULT_LAYERS_PROVIDER
  private var contentSizePolicy: ContentSizePolicy = ScreenView.DEVICE_CONTENT_SIZE_POLICY
  private var shapePolicy: ShapePolicy = DEVICE_CONFIGURATION_SHAPE_POLICY

  /** If called, the [ScreenView] will display the resize layer. */
  fun resizeable() = this.apply { isResizeable = true }

  /** Sets a non-default [ColorSet] for the [ScreenView] */
  fun withColorSet(colorSet: ColorSet) = this.apply { this.colorSet = colorSet }

  /** Sets a new provider that will determine the [Layer]s to be used. */
  fun withLayersProvider(layersProvider: Function<ScreenView, ImmutableList<Layer>>) =
    this.apply { this.layersProvider = layersProvider }

  /** Sets a new [ContentSizePolicy]. */
  fun withContentSizePolicy(contentSizePolicy: ContentSizePolicy) =
    this.apply { this.contentSizePolicy = contentSizePolicy }

  /** Sets a new [ContentSizePolicy]. */
  fun withShapePolicy(shapePolicy: ShapePolicy) = this.apply { this.shapePolicy = shapePolicy }

  /**
   * Sets a new [ContentSizePolicy]. The method receives the current policy and returns a new one
   * that can wrap it. Use this method if you want to decorate the current policy and not simply
   * replace it.
   */
  fun decorateContentSizePolicy(
    contentSizePolicyProvider: (ContentSizePolicy) -> ContentSizePolicy
  ) = this.apply { this.contentSizePolicy = contentSizePolicyProvider(contentSizePolicy) }

  /** Disables the visible border. */
  fun disableBorder() = this.apply { hasBorderLayer = false }

  fun build(): ScreenView {
    return ScreenView(
      surface,
      manager,
      shapePolicy,
      isResizeable,
      hasBorderLayer,
      (if (colorSet == null) AndroidColorSet() else colorSet)!!,
      layersProvider,
      contentSizePolicy,
    )
  }
}
