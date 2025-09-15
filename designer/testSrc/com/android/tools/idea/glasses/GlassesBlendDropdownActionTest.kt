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
package com.android.tools.idea.glasses

import com.android.flags.junit.FlagRule
import com.android.sdklib.SystemImageTags.XR_GLASSES_TAG
import com.android.sdklib.SystemImageTags.XR_HEADSET_TAG
import com.android.sdklib.devices.Device
import com.android.tools.configurations.Configuration
import com.android.tools.idea.actions.SCENE_VIEW
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class GlassesBlendDropdownActionTest {

  @get:Rule val applicationRule = ApplicationRule()

  @get:Rule val flagRule = FlagRule(StudioFlags.COMPOSE_PREVIEW_XR_GLASSES_PREVIEW, true)

  @Test
  fun `action is not visible when device is not glasses`() {
    val event = setUpDeviceAndActionVisibility(XR_HEADSET_TAG.id)
    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun `action is visible when device is glasses`() {
    val event = setUpDeviceAndActionVisibility(XR_GLASSES_TAG.id)
    assertThat(event.presentation.isEnabledAndVisible).isTrue()
  }

  /**
   * Sets up a data context containing a screen view that has a device passed as an argument. Then,
   * create an [AnActionEvent] and calls `GlassesBlendDropdownAction#update()` on it. Returns the
   * event so it can be used to check the action visibility.
   */
  private fun setUpDeviceAndActionVisibility(deviceId: String): AnActionEvent {
    val sceneView = mock<SceneView>()
    val configuration = mock<Configuration>()
    whenever(sceneView.configuration).thenReturn(configuration)

    val device = mock<Device>()
    whenever(device.tagId).thenReturn(deviceId)
    whenever(configuration.device).thenReturn(device)

    val dataContext = SimpleDataContext.builder().add(SCENE_VIEW, sceneView).build()

    val action = GlassesBlendDropdownAction()
    return TestActionEvent.createTestEvent(action, dataContext).also { action.update(it) }
  }
}
