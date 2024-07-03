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
package com.android.tools.idea.actions

import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.testutils.MockitoKt
import com.android.test.testutils.TestUtils.resolveWorkspacePathUnchecked
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.uibuilder.actions.DrawableBackgroundLayer
import com.android.tools.idea.uibuilder.actions.DrawableBackgroundType
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.surface.sizepolicy.ContentSizePolicy
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.util.Disposer
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class DrawableBackgroundLayerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private suspend fun renderLayerForBackgroundType(type: DrawableBackgroundType): BufferedImage =
    withContext(AndroidDispatchers.uiThread) {
      val drawablePsiFile =
        projectRule.fixture.loadNewFile("res/drawable/icon.xml", "<drawable></drawable>")
      val virtualFile = drawablePsiFile.virtualFile

      val mockDesignSurface = Mockito.mock<NlDesignSurface>()
      Disposer.register(projectRule.testRootDisposable, mockDesignSurface)

      val mockLayoutlibSceneManager = Mockito.mock<LayoutlibSceneManager>()
      val nlModel =
        NlModel.Builder(
            projectRule.testRootDisposable,
            BuildTargetReference.gradleOnly(projectRule.module.androidFacet!!),
            virtualFile,
            ConfigurationManager.getOrCreateInstance(projectRule.module)
              .getConfiguration(virtualFile),
          )
          .build()
      MockitoKt.whenever(mockLayoutlibSceneManager.model).thenReturn(nlModel)
      Disposer.register(projectRule.testRootDisposable, mockLayoutlibSceneManager)

      val screenView =
        object :
          ScreenView(
            mockDesignSurface,
            mockLayoutlibSceneManager,
            object : ContentSizePolicy {
              override fun measure(screenView: ScreenView, outDimension: Dimension) {
                outDimension.setSize(300, 200)
              }
            },
          ) {
          override val scale: Double
            get() = 1.0
        }

      val bufferedImage = BufferedImage(300, 200, BufferedImage.TYPE_INT_BGR)
      val backgroundLayer = DrawableBackgroundLayer(screenView, type)
      backgroundLayer.paint(bufferedImage.createGraphics())

      return@withContext bufferedImage
    }

  @Test
  fun `verify layer types`(): Unit = runBlocking {
    assertImageSimilar(
      resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/white.png"),
      renderLayerForBackgroundType(DrawableBackgroundType.WHITE),
      0.0,
      0,
    )

    assertImageSimilar(
      resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/black.png"),
      renderLayerForBackgroundType(DrawableBackgroundType.BLACK),
      0.0,
      0,
    )

    assertImageSimilar(
      resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/checkered.png"),
      renderLayerForBackgroundType(DrawableBackgroundType.CHECKERED),
      0.0,
      0,
    )
  }
}

private val GOLDEN_FILE_PATH =
  "tools/adt/idea/designer/testData/${DrawableBackgroundLayerTest::class.simpleName!!}/golden"
