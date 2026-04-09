/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.configurations

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.configurations.ConfigurationSettings
import com.android.tools.configurations.DeviceStateResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class DeviceStateResolverTest {
  /** A simple Context stub so we can test the Resolver without needing the giant Configuration.java Facade! */
  private class StubContext : DeviceStateResolver.Context {
    var overlayUpdated: Boolean = false
    var bestDevice: Device? = null

    override val editedConfig: FolderConfiguration = FolderConfiguration()

    override val settings: ConfigurationSettings = Mockito.mock(ConfigurationSettings::class.java)

    override fun updateDeviceOverlay() {
      overlayUpdated = true
    }

    override fun computeBestDevice(): Device? {
      return bestDevice
    }
  }

  @Test
  fun testGetDeviceUsesContextFallbackForPolymorphism() {
    val context = StubContext()

    val mockFallbackDevice = Mockito.mock(Device::class.java)
    context.bestDevice = mockFallbackDevice

    val resolver = DeviceStateResolver(context)

    // Assert that because mySpecificDevice is null, it falls back to the Context!
    val resolvedDevice = resolver.getDevice()

    assertSame("Should fallback to the Context's computeBestDevice()", mockFallbackDevice, resolvedDevice)
    assertTrue("Should notify context to update overlays", context.overlayUpdated)
  }

  @Test
  fun testSetEffectiveDeviceReturnsCorrectDirtyFlags() {
    val context = StubContext()
    val resolver = DeviceStateResolver(context)

    val mockDevice = Mockito.mock(Device::class.java)
    val mockState = Mockito.mock(State::class.java)

    val flags = resolver.setEffectiveDevice(mockDevice, mockState)

    assertEquals(ConfigurationListener.CFG_DEVICE or ConfigurationListener.CFG_DEVICE_STATE, flags)
    assertSame(mockDevice, resolver.cachedDevice)
    assertSame(mockState, resolver.cachedState)
  }

  @Test
  fun testSetDeviceStateNameReturnsDirtyFlag() {
    val context = StubContext()
    val resolver = DeviceStateResolver(context)

    // First assignment should return the dirty flag
    val initialFlags = resolver.setDeviceStateName("Landscape")
    assertEquals(ConfigurationListener.CFG_DEVICE_STATE, initialFlags)
    assertEquals("Landscape", resolver.stateName)

    // Second identical assignment should return 0 (No change)
    val duplicateFlags = resolver.setDeviceStateName("Landscape")
    assertEquals("Should return 0 when the state name does not actually change", 0, duplicateFlags)
  }
}
