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
package com.android.tools.idea.preview

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.preview.modes.CommonPreviewModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.preview.config.MutableDeviceConfig
import com.android.tools.preview.config.createDeviceInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.runInEdtAndGet
import java.awt.Cursor
import java.awt.event.KeyEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class NavigatingInteractionHandlerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  lateinit var surface: NlDesignSurface
  lateinit var previewModeManager: PreviewModeManager

  @Before
  fun setUp() {
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(projectRule, "layout", "layout.xml", ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)).build()
    }

    model.configuration.setDevice(MutableDeviceConfig().createDeviceInstance(), false)

    model.dataProvider =
      object : NlDataProvider(PreviewModeManager.KEY) {
        override fun getData(dataId: String): Any? = previewModeManager.takeIf { dataId == PreviewModeManager.KEY.name }
      }
    surface = NlSurfaceBuilder.builder(projectRule.project, projectRule.testRootDisposable).build()
    surface.addModelsWithoutRender(listOf(model))
    previewModeManager = CommonPreviewModeManager()
  }

  @Test
  fun testResizingEnabledOnlyForNormalModes() {
    val handler = NavigatingInteractionHandler(surface, mock(), true)

    val screenView = surface.sceneManagers.single().sceneViews.first() as ScreenView

    // the screen is in the right top corner
    assertThat(screenView.x).isEqualTo(0)
    assertThat(screenView.y).isEqualTo(0)

    val size = screenView.scaledContentSize

    val mouseX = Coordinates.getSwingXDip(screenView, size.width + 1)
    val mouseY = Coordinates.getSwingYDip(screenView, size.height + 1)
    val modifiersEx = 0
    handler.hoverWhenNoInteraction(mouseX, mouseY, modifiersEx)

    previewModeManager.setMode(PreviewMode.Focus(mock()))

    assertThat(handler.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx))
      .isEqualTo(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR))

    previewModeManager.setMode(PreviewMode.Interactive(mock()))

    assertThat(handler.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx)).isEqualTo(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
  }

  @Test
  fun testArrowKeyNavigationRequestsFocus() {
    val model1 = runInEdtAndGet {
      NlModelBuilderUtil.model(projectRule, "layout", "layout1.xml", ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)).build()
    }
    val model2 = runInEdtAndGet {
      NlModelBuilderUtil.model(projectRule, "layout", "layout2.xml", ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)).build()
    }
    surface.addModelsWithoutRender(listOf(model1, model2))

    val sceneView1 = surface.sceneManagers.first { it.model == model1 }.sceneViews.first()
    val sceneView2 = surface.sceneManagers.first { it.model == model2 }.sceneViews.first()

    surface.sceneManagers.first { it.model == model1 }.sceneViews.forEach { it.setLocation(0, 0) }
    surface.sceneManagers.first { it.model == model2 }.sceneViews.forEach { it.setLocation(100, 0) }
    // Move other scene views out of the way
    surface.sceneManagers
      .filter { it.model != model1 && it.model != model2 }
      .forEach { it.sceneViews.forEach { sv -> sv.setLocation(0, 200) } }

    val handler = NavigatingInteractionHandler(surface, mock(), true)

    // Select first component in first sceneView
    val component1 = sceneView1.firstComponent!!
    sceneView1.selectionModel.setSelection(listOf(component1))

    // Create a right arrow key event
    val keyEvent =
      KeyEvent(surface.interactionPane, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED)

    // This should move selection and request focus on the second preview panel.
    handler.keyPressedWithoutInteraction(keyEvent)

    assertThat(sceneView2.selectionModel.isSelected(sceneView2.firstComponent!!)).isTrue()
  }
}
