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
package com.android.tools.idea.compose.preview.util

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private fun buildDevice(name: String,
                        id: String = name,
                        manufacturer: String = "Google",
                        software: List<Software> = listOf(Software()),
                        states: List<State> = listOf(State().apply { isDefaultState = true })): Device =
  Device.Builder().apply {
    setId(id)
    setName(name)
    setManufacturer(manufacturer)
    addAllSoftware(software)
    addAllState(states)
  }.build()

private val defaultDevice = buildDevice("default", "DEFAULT")
private val pixel4Device = buildDevice("Pixel 4", "pixel_4")
private val nexus7Device = buildDevice("Nexus 7", "Nexus 7")
private val nexus10Device = buildDevice("Nexus 10", "Nexus 10")

private val deviceProvider: (Configuration) -> Collection<Device> = {
  listOf(pixel4Device, nexus7Device, nexus10Device)
}

/**
 * Tests checking [PreviewElement] being applied to a [Configuration].
 */
@RunWith(Parameterized::class)
class PreviewElementConfigurationTest(previewAnnotationPackage: String, composableAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview {1}.Composable")
    val namespaces = namespaceVariations
  }

  @get:Rule
  val projectRule = ComposeProjectRule(previewAnnotationPackage = previewAnnotationPackage,
                                       composableAnnotationPackage = composableAnnotationPackage)
  private val fixture get() = projectRule.fixture

  private fun assertDeviceMatches(expectedDevice: Device?, deviceSpec: String) {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    Configuration.create(configManager, null, FolderConfiguration.createDefault()).also {
      val previewConfiguration = PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null, deviceSpec)
      previewConfiguration.applyConfigurationForTest(it,
                                                     highestApiTarget = { null },
                                                     devicesProvider = deviceProvider,
                                                     defaultDeviceProvider = { defaultDevice })
      assertEquals(expectedDevice, it.device)
    }
  }

  @Test
  fun `set device by id and name successfully`() {
    // Find by id
    assertDeviceMatches(nexus7Device, "id:Nexus 7")
    // Find by name
    assertDeviceMatches(pixel4Device, "name:Pixel 4")
    // Device not found
    assertDeviceMatches(defaultDevice, "id:not found")
    assertDeviceMatches(defaultDevice, "name:not found")
    assertDeviceMatches(defaultDevice, "invalid:pixel_4")
  }
}