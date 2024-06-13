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

import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.surface.DesignSurfaceZoomController
import com.android.tools.idea.common.surface.ScenesOwner
import com.android.tools.idea.common.surface.SurfaceScale
import com.android.tools.idea.flags.StudioFlags

/**
 * [DesignSurfaceZoomController] for the [NlDesignSurface]. It contains all the zooming logic of
 * [NlDesignSurface].
 *
 * @param fitScaleProvider The provider of the scale that makes the content fit in the surface.
 * @param designerAnalyticsManager Analytics tracker responsible to track the zoom changes.
 * @param selectionModel The collection of [NlComponent]s of [DesignSurface].
 * @param scenesOwner the scene owner of this [ZoomController].
 * @param maxZoomToFitLevel The maximum zoom level allowed for ZoomType#FIT.
 */
class NlDesignSurfaceZoomController(
  private val fitScaleProvider: () -> Double,
  designerAnalyticsManager: DesignerAnalyticsManager?,
  selectionModel: SelectionModel?,
  scenesOwner: ScenesOwner?,
  maxZoomToFitLevel: Double = Double.MAX_VALUE,
) :
  DesignSurfaceZoomController(
    designerAnalyticsManager,
    selectionModel,
    scenesOwner,
    maxZoomToFitLevel,
  ) {

  override var minScale: Double = super.minScale

  override var maxScale: Double = super.maxScale

  override fun getFitScale(): Double = minOf(maxZoomToFitScale, fitScaleProvider())

  override val shouldShowZoomAnimation: Boolean = StudioFlags.PREVIEW_ZOOM_ANIMATION.get()

  override fun canZoomToActual(): Boolean {
    @SurfaceScale val scaleOfActual = 1.0 / screenScalingFactor
    return (scale > scaleOfActual && canZoomOut()) || (scale < scaleOfActual && canZoomIn())
  }
}
