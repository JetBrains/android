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
package com.android.tools.idea.naveditor.surface

import com.android.testutils.MockitoKt
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.createDesignerAnalyticsManagerFake
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport
import com.android.tools.idea.naveditor.scene.NavSceneManager
import org.mockito.Mockito
import java.awt.Component
import java.awt.Dimension
import java.awt.Point

fun createNavDesignSurfaceZoomController(
  navDesignSurface: NavDesignSurface,
  dimension: (SceneView) -> Dimension,
  trackZoom: (ZoomType) -> Unit = {},
): NavDesignSurfaceZoomController {
  return NavDesignSurfaceZoomController(
    navSelectionModel = null,
    viewPort = navDesignSurface.viewport,
    sceneManager = { navDesignSurface.model?.let { navDesignSurface.getSceneManager(it) } },
    sceneViewDimensionProvider = dimension,
    analyticsManager = createDesignerAnalyticsManagerFake(trackZoom),
    scenesOwner = navDesignSurface,
    surfaceSize = navDesignSurface.size,
  )
}

fun mockNavDesignSurface(
  focusedSceneView: SceneView?,
  surfaceSize: Dimension = Dimension(),
): NavDesignSurface {
  val width = 100
  val height = 500

  val sceneManager =
    Mockito.mock<NavSceneManager>().apply { MockitoKt.whenever(this.isEmpty).thenReturn(false) }

  val viewportMock =
    Mockito.mock<DesignSurfaceViewport>().apply {
      MockitoKt.whenever(this.viewPosition).thenReturn(Point(1, 1))
      MockitoKt.whenever(this.extentSize).thenReturn(surfaceSize)
      MockitoKt.whenever(this.viewportComponent)
        .thenReturn(
          object : Component() {
            override fun getWidth() = width

            override fun getHeight() = height
          }
        )
    }

  val model = Mockito.mock<NlModel>()

  val navDesignSurface =
    Mockito.mock<NavDesignSurface>().apply {
      MockitoKt.whenever(this.viewport).thenReturn(viewportMock)
      MockitoKt.whenever(this.size).thenReturn(Dimension(1, 1))
      MockitoKt.whenever(this.focusedSceneView).thenReturn(focusedSceneView)
      MockitoKt.whenever(this.model).thenReturn(model)
      MockitoKt.whenever(this.getSceneManager(MockitoKt.any())).thenReturn(sceneManager)
    }

  return navDesignSurface
}
