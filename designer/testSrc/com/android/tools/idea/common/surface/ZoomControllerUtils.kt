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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurfaceZoomController
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.mockito.Mockito

fun createDesignerAnalyticsManagerFake(trackZoom: (ZoomType) -> Unit): DesignerAnalyticsManager =
  object : DesignerAnalyticsManager(Mockito.mock()) {
    override fun trackZoom(type: ZoomType) {
      trackZoom(type)
    }
  }

fun createDesignSurfaceZoomControllerFake(
  project: Project,
  disposable: Disposable,
  minScale: Double = 0.0,
  maxScale: Double = 10.0,
  trackZoom: ((ZoomType) -> Unit)? = null,
): DesignSurfaceZoomController {
  val designerAnalyticsManager =
    trackZoom?.let {
      object : DesignerAnalyticsManager(TestDesignSurface(project, disposable)) {
        override fun trackZoom(type: ZoomType) {
          trackZoom(type)
        }
      }
    }
  return object :
    DesignSurfaceZoomController(
      designerAnalyticsManager = designerAnalyticsManager,
      selectionModel = null,
      scenesOwner = null,
    ) {
    override val minScale: Double
      get() = minScale

    override val maxScale: Double
      get() = maxScale

    override fun getFitScale() = 1.0
  }
}

fun createNlDesignSurfaceZoomController(
  trackZoom: (ZoomType?) -> Unit = {},
  fitScaleProvider: () -> Double = { 1.0 },
): DesignSurfaceZoomController {
  return NlDesignSurfaceZoomController(
    fitScaleProvider = fitScaleProvider,
    designerAnalyticsManager = createDesignerAnalyticsManagerFake(trackZoom),
    selectionModel = null,
    scenesOwner = null,
  )
}
