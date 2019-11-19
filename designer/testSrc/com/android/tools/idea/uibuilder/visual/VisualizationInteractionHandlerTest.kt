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
package com.android.tools.idea.uibuilder.visual

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.editor.LayoutNavigationManager
import com.android.tools.idea.uibuilder.scene.SceneTest
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.intThat
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class VisualizationInteractionHandlerTest : SceneTest() {

  override fun setUp() {
    super.setUp()
    val surface = myModel.surface
    val sceneManager = surface.sceneManager!!

    // Return SceneView when hover on it, null otherwise.
    val view = sceneManager.sceneView
    `when`(surface.getHoverSceneView(anyInt(), anyInt())).thenReturn(null)
    val xMatcher = intThat { view.x <= it && it <= view.x + view.size.width }
    val yMatcher = intThat { view.y <= it && it <= view.y + view.size.height }

    `when`(surface.getHoverSceneView(xMatcher, yMatcher)).thenReturn(view)
  }

  fun testHoverToShowToolTips() {
    val surface = myModel.surface
    val tooltips = surface.focusedSceneView!!.sceneManager.model.configuration.toTooltips()
    val interactionHandler = VisualizationInteractionHandler(surface) { PixelDeviceModelsProvider }

    val view = surface.sceneManager!!.sceneView

    interactionHandler.hoverWhenNoInteraction(view.x + view.size.width / 2, view.y + view.size.height / 2, 0)
    Mockito.verify(surface).setDesignToolTip(tooltips)

    interactionHandler.hoverWhenNoInteraction(view.x - 100, view.y - 100, 0)
    Mockito.verify(surface).setDesignToolTip(null)
  }

  fun testDoubleClickToNavigateToFileOfPreview() {
    val navigationManager = Mockito.mock(LayoutNavigationManager::class.java)
    registerProjectComponent(LayoutNavigationManager::class.java, navigationManager)
    val handler = VisualizationInteractionHandler(myModel.surface) { Mockito.mock(VisualizationModelsProvider::class.java) }
    val file = myModel.virtualFile
    val view = myModel.surface.sceneManager?.sceneView!!
    handler.doubleClick(view.x + view.size.width, view.y + view.size.height)
    Mockito.verify(navigationManager).pushFile(file, file)
  }

  override fun createModel(): ModelBuilder {
    return model("constraint.xml",
                 component(SdkConstants.CONSTRAINT_LAYOUT.newName())
                   .withBounds(0, 0, 2000, 2000)
                   .id("@id/constraint")
                   .matchParentWidth()
                   .matchParentHeight()
    )
  }
}
