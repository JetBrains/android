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

import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.scene.draw.ColorSet
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.SQUARE_SHAPE_POLICY
import com.android.tools.idea.common.surface.SceneView
import com.google.common.collect.ImmutableList
import java.awt.Dimension
import org.mockito.Mockito

class TestSceneView(private val width: Int, private val height: Int, sceneManager: SceneManager) :
  SceneView(Mockito.mock(DesignSurface::class.java), sceneManager, SQUARE_SHAPE_POLICY) {

  override fun createLayers(): ImmutableList<Layer> = ImmutableList.of()

  override fun getContentSize(dimension: Dimension?): Dimension {
    val dim = dimension ?: Dimension()
    dim.setSize(width, height)
    return dim
  }

  override val colorSet: ColorSet = ColorSet()

  override val scale: Double = 1.0
}
