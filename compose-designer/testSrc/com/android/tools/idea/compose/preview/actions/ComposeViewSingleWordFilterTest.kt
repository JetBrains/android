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
package com.android.tools.idea.compose.preview.actions

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.model.DisplaySettings
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.collect.ImmutableList
import com.intellij.openapi.actionSystem.DataContext
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@Suppress("UsePropertyAccessSyntax")
class ComposeViewSingleWordFilterTest {

  @Rule @JvmField val rule = AndroidProjectRule.inMemory()

  @Test
  fun testOnlyShowMatchedViews() {
    val myView1 = createTestSceneView("MyView1")
    val myView2 = createTestSceneView("MyView2")
    val yourView1 = createTestSceneView("YourView1")
    val yourView2 = createTestSceneView("YourView2")

    val surface = createTestSurface(myView1, myView2, yourView1, yourView2)
    val dataContext = DataContext { if (DESIGN_SURFACE.`is`(it)) surface else null }
    val filter = ComposeViewSingleWordFilter()
    filter.filter("My", dataContext)
    verify(myView1, times(1)).isVisible = true
    verify(myView2, times(1)).isVisible = true
    verify(yourView1, times(1)).isVisible = false
    verify(yourView2, times(1)).isVisible = false

    filter.filter("Your", dataContext)
    verify(myView1, times(1)).isVisible = false
    verify(myView2, times(1)).isVisible = false
    verify(yourView1, times(1)).isVisible = true
    verify(yourView2, times(1)).isVisible = true

    filter.filter("View", dataContext)
    verify(myView1, times(2)).isVisible = true
    verify(myView2, times(2)).isVisible = true
    verify(yourView1, times(2)).isVisible = true
    verify(yourView2, times(2)).isVisible = true

    filter.filter("XXX", dataContext)
    verify(myView1, times(2)).isVisible = false
    verify(myView2, times(2)).isVisible = false
    verify(yourView1, times(2)).isVisible = false
    verify(yourView2, times(2)).isVisible = false
  }

  @Test
  fun testShowingAllViewsWhenQueryTextIsEmptyOrBlank() {
    val myView1 = createTestSceneView("MyView1")
    val myView2 = createTestSceneView("MyView2")
    val yourView1 = createTestSceneView("YourView1")
    val yourView2 = createTestSceneView("YourView2")

    val surface = createTestSurface(myView1, myView2, yourView1, yourView2)
    val dataContext = DataContext { if (DESIGN_SURFACE.`is`(it)) surface else null }
    val filter = ComposeViewSingleWordFilter()
    filter.filter("", dataContext)
    verify(myView1, times(1)).isVisible = true
    verify(myView2, times(1)).isVisible = true
    verify(yourView1, times(1)).isVisible = true
    verify(yourView2, times(1)).isVisible = true

    filter.filter("  ", dataContext)
    verify(myView1, times(2)).isVisible = true
    verify(myView2, times(2)).isVisible = true
    verify(yourView1, times(2)).isVisible = true
    verify(yourView2, times(2)).isVisible = true

    filter.filter(null, dataContext)
    verify(myView1, times(3)).isVisible = true
    verify(myView2, times(3)).isVisible = true
    verify(yourView1, times(3)).isVisible = true
    verify(yourView2, times(3)).isVisible = true
  }

  @Test
  fun testTrimWord() {
    val view1 = createTestSceneView("View1")
    val view2 = createTestSceneView("View2")

    val surface = createTestSurface(view1, view2)
    val dataContext = DataContext { if (DESIGN_SURFACE.`is`(it)) surface else null }
    val filter = ComposeViewSingleWordFilter()
    filter.filter("  View1  ", dataContext)

    verify(view1, times(1)).isVisible = true
    verify(view2, times(1)).isVisible = false
  }

  private fun createTestSurface(vararg views: SceneView): DesignSurface<*> {
    val surface = mock<DesignSurface<*>>()
    val managers = views.map { it.sceneManager }
    whenever(surface.sceneManagers).thenReturn(ImmutableList.copyOf(managers))
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
