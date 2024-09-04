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
package com.android.tools.idea.wearwhs.view

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorId
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class WearHealthServicesPanelManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project

  private val panelManager
    get() = project.service<WearHealthServicesPanelManager>()

  @Test
  fun `the panel is the same for the same emulator controller`() {
    val emulatorController1 = createEmulatorController(serialPort = 5554)
    val emulatorController2 = createEmulatorController(serialPort = 5556)

    val panel1 = panelManager.getOrCreate(emulatorController1)
    val panel1Bis = panelManager.getOrCreate(emulatorController1)
    val panel2 = panelManager.getOrCreate(emulatorController2)

    assertEquals(panel1, panel1Bis)
    assertNotEquals(panel1, panel2)
  }

  @Test
  fun `the panel is recreated after the emulator controller is disposed`() {
    val emulatorController = createEmulatorController()

    val panelBeforeDisposal = panelManager.getOrCreate(emulatorController)
    Disposer.dispose(emulatorController)

    val newEmulatorController = createEmulatorController()
    val panelAfterDisposal = panelManager.getOrCreate(newEmulatorController)

    assertNotEquals(panelBeforeDisposal, panelAfterDisposal)
  }

  private fun createEmulatorController(serialPort: Int = 5554): EmulatorController {
    val emulatorConfig =
      mock<EmulatorConfiguration>().also {
        whenever(it.api).thenReturn(33)
        whenever(it.deviceType).thenReturn(DeviceType.WEAR)
      }
    return mock<EmulatorController>().also {
      whenever(it.emulatorId)
        .thenReturn(
          EmulatorId(
            0,
            null,
            null,
            "avdId",
            "avdFolder",
            Paths.get("avdPath"),
            serialPort,
            0,
            emptyList(),
            "",
          )
        )
      whenever(it.emulatorConfig).thenReturn(emulatorConfig)
      Disposer.register(projectRule.testRootDisposable, it)
    }
  }
}
