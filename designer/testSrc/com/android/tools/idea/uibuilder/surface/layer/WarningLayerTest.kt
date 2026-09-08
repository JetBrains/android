/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface.layer

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.createFakeToolWindow
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.util.androidFacet
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WarningLayerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var surface: NlDesignSurface
  private lateinit var screenView: ScreenView

  @Before
  fun setUp() = runBlocking {
    createFakeToolWindow(projectRule.project, projectRule.testRootDisposable, ProblemsView.ID)
    val model =
      withContext(Dispatchers.EDT) {
        NlModelBuilderUtil.model(
            AndroidBuildTargetReference.gradleOnly(projectRule.module.androidFacet!!),
            projectRule.fixture,
            SdkConstants.FD_RES_LAYOUT,
            "model.xml",
            ComponentDescriptor("LinearLayout"),
          )
          .build()
      }
    surface = NlSurfaceBuilder.build(projectRule.project, projectRule.testRootDisposable, false)
    val sceneManager = surface.addModelsWithoutRender(listOf(model)).single()
    screenView = ScreenView.newBuilder(surface, sceneManager).build()
  }

  @Test
  fun testCreateAndDisposeWarningLayerOnBackgroundThread() =
    runBlocking(Dispatchers.Default) {
      val layer = WarningLayer(screenView) { true }
      assertFalse(layer.isVisible)
      Disposer.dispose(layer)
    }

  @Test
  fun testCreateAndDisposeUiCheckWarningLayerOnBackgroundThread() =
    runBlocking(Dispatchers.Default) {
      val layer = UiCheckWarningLayer(screenView) { true }
      assertFalse(layer.isVisible)
      Disposer.dispose(layer)
    }
}
