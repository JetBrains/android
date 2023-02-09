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
package com.android.tools.idea.common.surface

import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito

class SceneLayerTest {

  private val designSurfaceMock = Mockito.mock(DesignSurface::class.java)
  private val sceneViewMock = Mockito.mock(SceneView::class.java)
  private val otherSceneViewMock = Mockito.mock(SceneView::class.java)

  private lateinit var sceneLayer: SceneLayer

  @Before
  fun setUp() {
    Mockito.`when`(designSurfaceMock.getSceneViewAt(anyInt(), anyInt())).then {
      when(it.arguments[0]) {
        1 -> sceneViewMock
        2 -> otherSceneViewMock
        else -> null
      }
    }
    sceneLayer = SceneLayer(designSurfaceMock, sceneViewMock, false)
  }

  @Test
  fun testOnHover() {
    sceneLayer.onHover(0, 0)
    assertFalse(sceneLayer.isShowOnHover)
    sceneLayer.onHover(1, 0)
    assertTrue(sceneLayer.isShowOnHover)
    sceneLayer.onHover(2, 0)
    assertFalse(sceneLayer.isShowOnHover)
  }

  @Test
  fun testOnHoverFilter() {
    sceneLayer.setShowOnHoverFilter { it != sceneViewMock }
    sceneLayer.onHover(0, 0)
    assertFalse(sceneLayer.isShowOnHover)
    sceneLayer.onHover(1, 0)
    assertFalse(sceneLayer.isShowOnHover)
    sceneLayer.onHover(2, 0)
    assertFalse(sceneLayer.isShowOnHover)

    sceneLayer.setShowOnHoverFilter { it == sceneViewMock }
    sceneLayer.onHover(0, 0)
    assertFalse(sceneLayer.isShowOnHover)
    sceneLayer.onHover(1, 0)
    assertTrue(sceneLayer.isShowOnHover)
    sceneLayer.onHover(2, 0)
    assertFalse(sceneLayer.isShowOnHover)
  }
}
