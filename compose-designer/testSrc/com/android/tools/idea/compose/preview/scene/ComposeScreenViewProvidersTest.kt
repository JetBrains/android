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
import com.android.tools.idea.compose.pickers.preview.property.DeviceConfig
import com.android.tools.idea.compose.pickers.preview.property.DimUnit
import com.android.tools.idea.compose.pickers.preview.property.Shape
import com.android.tools.idea.compose.pickers.preview.utils.createDeviceInstance
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import java.awt.Rectangle
import java.awt.geom.Ellipse2D
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposeScreenViewProvidersTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `shape policy depends on the showSystemUi value`() {
    val model = invokeAndWaitIfNeeded {
      NlModelBuilderUtil.model(
          projectRule.module.androidFacet!!,
          projectRule.fixture,
          SdkConstants.FD_RES_LAYOUT,
          "model.xml",
          ComponentDescriptor("LinearLayout")
        )
        .build()
    }
    val surface = NlDesignSurface.build(projectRule.project, projectRule.testRootDisposable)
    surface.model = model

    // Create a device with round shape
    val deviceWithRoundFrame =
      DeviceConfig(
          width = 600f,
          height = 600f,
          dimUnit = DimUnit.px,
          dpi = 480,
          shape = Shape.Round
        )
        .createDeviceInstance()
    model.configuration.setDevice(deviceWithRoundFrame, false)

    var previewElement =
      SingleComposePreviewElementInstance.forTesting(
        "TestMethod",
        displayName = "displayName",
        showDecorations = true
      )
    model.dataContext = DataContext {
      when (it) {
        COMPOSE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
        else -> null
      }
    }

    // When showDecorations is true, the scene view should always use a the device shape. In this
    // case, round.
    listOf(COMPOSE_SCREEN_VIEW_PROVIDER, COMPOSE_BLUEPRINT_SCREEN_VIEW_PROVIDER).forEach {
      val sceneView =
        it.createPrimarySceneView(surface, surface.sceneManager as LayoutlibSceneManager)
      assertTrue(sceneView.screenShape is Ellipse2D)
    }

    // When showDecorations is false, the scene view should always use a square shape
    previewElement =
      SingleComposePreviewElementInstance.forTesting(
        "TestMethod",
        displayName = "displayName",
        showDecorations = false
      )
    listOf(COMPOSE_SCREEN_VIEW_PROVIDER, COMPOSE_BLUEPRINT_SCREEN_VIEW_PROVIDER).forEach {
      val sceneView =
        it.createPrimarySceneView(surface, surface.sceneManager as LayoutlibSceneManager)
      assertTrue(sceneView.screenShape is Rectangle)
    }
  }
}
