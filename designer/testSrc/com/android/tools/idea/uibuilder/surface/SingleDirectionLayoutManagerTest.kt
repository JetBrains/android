/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.google.common.collect.ImmutableList
import org.mockito.Mockito
import java.awt.Dimension

class SingleDirectionLayoutManagerTest: LayoutTestCase() {

  fun testLayoutHorizontally() {
    val paddingX = 10
    val paddingY = 20
    val screenDeltaX = 30
    val screenDeltaY = 30

    val manager = SingleDirectionLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY)

    val availableWidth = 1000
    val availableHeight = 300

    run {
      val w = 100
      val h = 150
      val sceneView1 = TestSceneView(w, h)
      manager.layout(listOf(sceneView1), availableWidth, availableHeight)
      assertEquals((availableWidth - w) / 2, sceneView1.x)
      assertEquals((availableHeight - 150) / 2, sceneView1.y)
    }

    run {
      val w = 100
      val h = 280
      val sceneView1 = TestSceneView(w, h)
      val sceneView2 = TestSceneView(w, h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals((availableWidth - w * 2 - screenDeltaX) / 2, sceneView1.x)
      assertEquals(paddingY, sceneView1.y)
      assertEquals((availableWidth - w * 2 - screenDeltaX) / 2 + w + screenDeltaX, sceneView2.x)
      assertEquals(paddingY, sceneView2.y)
    }

    run {
      val w = 500
      val h = 1000
      val sceneView1 = TestSceneView(w, h)
      val sceneView2 = TestSceneView(w, h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals(paddingX, sceneView1.x)
      assertEquals(paddingY, sceneView1.y)
      assertEquals(paddingX + w + screenDeltaX, sceneView2.x)
      assertEquals(paddingY, sceneView2.y)
    }
  }

  fun testLayoutVertically() {
    val paddingX = 10
    val paddingY = 20
    val screenDeltaX = 30
    val screenDeltaY = 30

    val manager = SingleDirectionLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY)

    val availableWidth = 300
    val availableHeight = 1000

    run {
      val w = 100
      val h = 150
      val sceneView1 = TestSceneView(w, h)
      manager.layout(listOf(sceneView1), availableWidth, availableHeight)
      assertEquals((availableWidth - w) / 2, sceneView1.x)
      assertEquals((availableHeight - h) / 2, sceneView1.y)
    }

    run {
      val w = 100
      val h = 280
      val sceneView1 = TestSceneView(w, h)
      val sceneView2 = TestSceneView(w, h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals((availableWidth - w) / 2, sceneView1.x)
      assertEquals((availableHeight - h * 2 - screenDeltaY) / 2, sceneView1.y)
      assertEquals((availableWidth - w) / 2, sceneView2.x)
      assertEquals((availableHeight - h * 2 - screenDeltaY) / 2 + h + screenDeltaY, sceneView2.y)
    }

    run {
      val w = 100
      val h = 500
      val sceneView1 = TestSceneView(w, h)
      val sceneView2 = TestSceneView(w, h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals((availableWidth - w) / 2, sceneView1.x)
      assertEquals(paddingY, sceneView1.y)
      assertEquals((availableWidth - w) / 2, sceneView2.x)
      assertEquals(paddingY + h + screenDeltaY, sceneView2.y)
    }

    run {
      val w = 500
      val h = 1000
      val sceneView1 = TestSceneView(w, h)
      val sceneView2 = TestSceneView(w, h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals(paddingX, sceneView1.x)
      assertEquals(paddingY, sceneView1.y)
      assertEquals(paddingX, sceneView2.x)
      assertEquals(paddingY + h + screenDeltaY, sceneView2.y)
    }
  }
}

private class TestSceneView(private val width: Int, private val height: Int, private val nameLabelHeight: Int = 0)
  : SceneView(Mockito.mock(DesignSurface::class.java), Mockito.mock(SceneManager::class.java)) {

  override fun createLayers(): ImmutableList<Layer> = ImmutableList.of()

  override fun getNameLabelHeight() = nameLabelHeight

  override fun getPreferredSize(dimension: Dimension?): Dimension {
    val dim = dimension ?: Dimension()
    dim.setSize(width, height)
    return dim
  }

  override fun getColorSet(): ColorSet = ColorSet()

  override fun getScale(): Double = 1.0
}
