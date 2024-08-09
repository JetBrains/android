/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.scene

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.compose.preview.NopComposePreviewManager
import com.android.tools.idea.compose.preview.PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.util.androidFacet
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.preview.config.DeviceConfig
import com.android.tools.preview.config.DimUnit
import com.android.tools.preview.config.Shape
import com.android.tools.preview.config.createDeviceInstance
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import java.awt.Rectangle
import java.awt.geom.Ellipse2D
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposeScreenViewProvidersTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `shape policy depends on the showSystemUi value`() = runBlocking {
    val model =
      withContext(uiThread) {
        NlModelBuilderUtil.model(
            AndroidBuildTargetReference.gradleOnly(projectRule.module.androidFacet!!),
            projectRule.fixture,
            SdkConstants.FD_RES_LAYOUT,
            "model.xml",
            ComponentDescriptor("LinearLayout"),
          )
          .build()
      }
    val surface = NlSurfaceBuilder.build(projectRule.project, projectRule.testRootDisposable)
    surface.addModelWithoutRender(model).await()

    // Create a device with round shape
    val deviceWithRoundFrame =
      DeviceConfig(
          width = 600f,
          height = 600f,
          dimUnit = DimUnit.px,
          dpi = 480,
          shape = Shape.Round,
        )
        .createDeviceInstance()
    model.configuration.setDevice(deviceWithRoundFrame, false)

    var previewElement =
      SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
        "TestMethod",
        displayName = "displayName",
        showDecorations = true,
      )
    model.dataContext = DataContext {
      when (it) {
        PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
        else -> null
      }
    }

    val composeScreenViewProvider = ComposeScreenViewProvider(NopComposePreviewManager())

    // When showDecorations is true, the scene view should always use the device shape. In this
    // case, round.
    assertTrue(
      composeScreenViewProvider.createPrimarySceneView(surface, surface.sceneManager!!).screenShape
        is Ellipse2D
    )

    // When showDecorations is false, the scene view should always use a square shape
    previewElement =
      SingleComposePreviewElementInstance.forTesting(
        "TestMethod",
        displayName = "displayName",
        showDecorations = false,
      )
    assertTrue(
      composeScreenViewProvider.createPrimarySceneView(surface, surface.sceneManager!!).screenShape
        is Rectangle
    )
  }
}
