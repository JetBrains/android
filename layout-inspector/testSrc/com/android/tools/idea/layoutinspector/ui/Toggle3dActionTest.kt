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
package com.android.tools.idea.layoutinspector.ui

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.view
import com.android.tools.idea.layoutinspector.window
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent.PayloadType.PNG_AS_REQUESTED
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent.PayloadType.PNG_SKP_TOO_LARGE
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import icons.StudioIcons
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class Toggle3dActionTest {

  private val inspectorModel = model {
    view(1) {
      view(2) {
        image()
      }
    }
  }

  private val viewModel = DeviceViewPanelModel(inspectorModel)

  private val event: AnActionEvent = mock()
  private val presentation: Presentation = mock()

  @Before
  fun setUp() {
    `when`(event.getData(DEVICE_VIEW_MODEL_KEY)).thenReturn(viewModel)
    `when`(event.presentation).thenReturn(presentation)
  }

  @Test
  fun testUnrotated() {
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = true
    verify(presentation).text = "Rotate View"
    verify(presentation).icon = StudioIcons.LayoutInspector.MODE_3D
  }

  @Test
  fun testRotated() {
    viewModel.xOff = 1.0
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = true
    verify(presentation).text = "Reset View"
    verify(presentation).icon = StudioIcons.LayoutInspector.RESET_VIEW
  }

  @Test
  fun testOverlay() {
    viewModel.overlay = mock()
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = false
    verify(presentation).text = "Rotation not available when overlay is active"
  }

  @Test
  fun testRootImageOnly() {
    val root = view(3) {
      image()
      view(2)
    }
    inspectorModel.update(AndroidWindow(root, 3), listOf(3), 0)
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = false
    verify(presentation).text = "Rotation not available for devices below API 29"
  }

  @Test
  fun testNoRendererFallback() {
    val window = window(3, 1, imageType = PNG_AS_REQUESTED) {
      image()
      view(2)
    }
    inspectorModel.update(window, listOf(3), 0)
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = false
    verify(presentation).text = "No compatible renderer found for device image, rotation not available"
  }

  @Test
  fun testSkpTooLargeFallback() {
    val window = window(3, 1, imageType = PNG_SKP_TOO_LARGE) {
      image()
      view(2)
    }
    inspectorModel.update(window, listOf(3), 0)
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = false
    verify(presentation).text = "Device image too large, rotation not available"
  }
}