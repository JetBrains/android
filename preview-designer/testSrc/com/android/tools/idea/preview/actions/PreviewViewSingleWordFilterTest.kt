/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.model.DisplaySettings
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.preview.PreviewViewSingleWordFilter
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PreviewViewSingleWordFilterTest {

  @Rule @JvmField val rule = AndroidProjectRule.Companion.inMemory()

  @Test
  fun testOnlyShowMatchedViews() {
    val myView1 = createTestSceneView("MyView1")
    val myView2 = createTestSceneView("MyView2")
    val yourView1 = createTestSceneView("YourView1")
    val yourView2 = createTestSceneView("YourView2")

    val surface = createTestSurface(myView1, myView2, yourView1, yourView2)
    val dataContext = SimpleDataContext.getSimpleContext(DESIGN_SURFACE, surface)
    val filter = PreviewViewSingleWordFilter()
    filter.filter("My", dataContext)
    Mockito.verify(myView1, Mockito.times(1)).isVisible = true
    Mockito.verify(myView2, Mockito.times(1)).isVisible = true
    Mockito.verify(yourView1, Mockito.times(1)).isVisible = false
    Mockito.verify(yourView2, Mockito.times(1)).isVisible = false

    filter.filter("Your", dataContext)
    Mockito.verify(myView1, Mockito.times(1)).isVisible = false
    Mockito.verify(myView2, Mockito.times(1)).isVisible = false
    Mockito.verify(yourView1, Mockito.times(1)).isVisible = true
    Mockito.verify(yourView2, Mockito.times(1)).isVisible = true

    filter.filter("View", dataContext)
    Mockito.verify(myView1, Mockito.times(2)).isVisible = true
    Mockito.verify(myView2, Mockito.times(2)).isVisible = true
    Mockito.verify(yourView1, Mockito.times(2)).isVisible = true
    Mockito.verify(yourView2, Mockito.times(2)).isVisible = true

    filter.filter("XXX", dataContext)
    Mockito.verify(myView1, Mockito.times(2)).isVisible = false
    Mockito.verify(myView2, Mockito.times(2)).isVisible = false
    Mockito.verify(yourView1, Mockito.times(2)).isVisible = false
    Mockito.verify(yourView2, Mockito.times(2)).isVisible = false
  }

  @Test
  fun testShowingAllViewsWhenQueryTextIsEmptyOrBlank() {
    val myView1 = createTestSceneView("MyView1")
    val myView2 = createTestSceneView("MyView2")
    val yourView1 = createTestSceneView("YourView1")
    val yourView2 = createTestSceneView("YourView2")

    val surface = createTestSurface(myView1, myView2, yourView1, yourView2)
    val dataContext = SimpleDataContext.getSimpleContext(DESIGN_SURFACE, surface)
    val filter = PreviewViewSingleWordFilter()
    filter.filter("", dataContext)
    Mockito.verify(myView1, Mockito.times(1)).isVisible = true
    Mockito.verify(myView2, Mockito.times(1)).isVisible = true
    Mockito.verify(yourView1, Mockito.times(1)).isVisible = true
    Mockito.verify(yourView2, Mockito.times(1)).isVisible = true

    filter.filter("  ", dataContext)
    Mockito.verify(myView1, Mockito.times(2)).isVisible = true
    Mockito.verify(myView2, Mockito.times(2)).isVisible = true
    Mockito.verify(yourView1, Mockito.times(2)).isVisible = true
    Mockito.verify(yourView2, Mockito.times(2)).isVisible = true

    filter.filter(null, dataContext)
    Mockito.verify(myView1, Mockito.times(3)).isVisible = true
    Mockito.verify(myView2, Mockito.times(3)).isVisible = true
    Mockito.verify(yourView1, Mockito.times(3)).isVisible = true
    Mockito.verify(yourView2, Mockito.times(3)).isVisible = true
  }

  @Test
  fun testTrimWord() {
    val view1 = createTestSceneView("View1")
    val view2 = createTestSceneView("View2")

    val surface = createTestSurface(view1, view2)
    val dataContext = SimpleDataContext.getSimpleContext(DESIGN_SURFACE, surface)
    val filter = PreviewViewSingleWordFilter()
    filter.filter("  View1  ", dataContext)

    Mockito.verify(view1, Mockito.times(1)).isVisible = true
    Mockito.verify(view2, Mockito.times(1)).isVisible = false
  }

  private fun createTestSurface(vararg views: SceneView): DesignSurface<*> {
    val surface = mock<DesignSurface<*>>()
    val managers = views.map { it.sceneManager }
    whenever(surface.sceneManagers).thenReturn(managers.toList())
    return surface
  }

  private fun createTestSceneView(name: String): SceneView {
    val model = mock<NlModel>()
    val sceneManager = mock<SceneManager>()
    val view = mock<SceneView>()

    whenever(model.displaySettings).thenReturn(DisplaySettings().apply { setDisplayName(name) })
    whenever(sceneManager.sceneViews).thenReturn(listOf(view))
    whenever(sceneManager.model).thenReturn(model)
    whenever(view.sceneManager).thenReturn(sceneManager)
    return view
  }
}
