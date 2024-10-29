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
package com.android.tools.adtui.compose

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import org.junit.Rule
import org.junit.Test

/**
 * Verifies that DeviceScreenDiagram doesn't crash in various configurations. Appearance is not
 * verified.
 */
class DeviceScreenDiagramTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun normal() {
    composeTestRule.setContent { DeviceScreenDiagram(800, 1200, Modifier.size(200.dp), "6.2") }
  }

  @Test
  fun round() {
    composeTestRule.setContent { DeviceScreenDiagram(1200, 1200, Modifier.size(200.dp), "6.2") }
  }

  @Test
  fun tiny() {
    composeTestRule.setContent { DeviceScreenDiagram(800, 1200, Modifier.size(20.dp), "6.2") }
  }

  @Test
  fun wide() {
    composeTestRule.setContent { DeviceScreenDiagram(8000, 1, Modifier.size(200.dp), "6.2") }
  }

  @Test
  fun tall() {
    composeTestRule.setContent { DeviceScreenDiagram(1, 8000, Modifier.size(200.dp), "6.2") }
  }
}
