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
package com.android.tools.idea.compose.annotator.check.device

import com.android.sdklib.devices.Device
import com.android.tools.idea.compose.preview.pickers.properties.AvailableDevicesKey
import com.android.tools.idea.compose.preview.pickers.properties.DeviceConfig
import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.android.tools.idea.compose.preview.pickers.properties.Shape
import com.android.tools.idea.compose.preview.pickers.properties.utils.createDeviceInstance
import com.intellij.openapi.actionSystem.DataProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class DeviceIdParameterRuleTest {

  lateinit var dataProvider: DataProvider

  @Before
  fun setup() {
    dataProvider = DataProvider {
      when (it) {
        AvailableDevicesKey.name -> buildMockDevices()
        else -> null
      }
    }
  }

  @Test
  fun checkDeviceId() {
    val rule = DeviceIdParameterRule(name = "foo")
    assertTrue(rule.checkValue("id0", dataProvider))
    assertTrue(rule.checkValue("id1", dataProvider))
    assertFalse(rule.checkValue("bar", dataProvider))
    assertFalse(rule.checkValue("id0", DataProvider { null }))
  }
}

/**
 * Returns a list of different devices with incremental IDs described by: `id0, id1, id2, ...,
 * idN-1`
 */
private fun buildMockDevices(): List<Device> {
  var idCount = 0
  return listOf(
      DeviceConfig(
        width = 1080f,
        height = 1920f,
        dimUnit = DimUnit.px,
        dpi = 320,
        shape = Shape.Normal
      ),
      DeviceConfig(
        width = 1080f,
        height = 1920f,
        dimUnit = DimUnit.px,
        dpi = 480,
        shape = Shape.Normal
      ),
      DeviceConfig(
        width = 1080f,
        height = 2280f,
        dimUnit = DimUnit.px,
        dpi = 480,
        shape = Shape.Normal
      ),
      DeviceConfig(
        width = 600f,
        height = 600f,
        dimUnit = DimUnit.px,
        dpi = 480,
        shape = Shape.Round
      )
    )
    .map {
      Device.Builder(it.createDeviceInstance())
        .also { builder -> builder.setId("id${idCount++}") }
        .build()
    }
}
