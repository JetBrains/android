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
package com.android.tools.idea.configurations;

import static com.android.tools.configurations.ConfigurationListener.CFG_DEVICE;
import static com.android.tools.configurations.ConfigurationListener.CFG_DEVICE_STATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.configurations.ConfigurationSettings;
import com.android.tools.configurations.DeviceStateResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.mockito.Mockito;

public class DeviceStateResolverTest {

  /**
   * A simple Context stub so we can test the Resolver without needing
   * the giant Configuration.java Facade!
   */
  private static class StubContext implements DeviceStateResolver.Context {
    public boolean overlayUpdated = false;
    public Device bestDevice = null;
    public FolderConfiguration config = new FolderConfiguration();

    @NotNull @Override
    public ConfigurationSettings getSettings() {
      return Mockito.mock(ConfigurationSettings.class);
    }

    @NotNull @Override
    public FolderConfiguration getEditedConfig() {
      return config;
    }

    @Override
    public void updateDeviceOverlay() {
      overlayUpdated = true;
    }

    @Nullable @Override
    public Device computeBestDevice() {
      return bestDevice;
    }
  }

  @Test
  public void testGetDeviceUsesContextFallbackForPolymorphism() {
    StubContext context = new StubContext();
    Device mockFallbackDevice = Mockito.mock(Device.class);
    context.bestDevice = mockFallbackDevice;

    DeviceStateResolver resolver = new DeviceStateResolver(context);

    // Assert that because mySpecificDevice is null, it falls back to the Context!
    Device resolvedDevice = resolver.getDevice();

    assertSame("Should fallback to the Context's computeBestDevice()", mockFallbackDevice, resolvedDevice);
    assertTrue("Should notify context to update overlays", context.overlayUpdated);
  }

  @Test
  public void testSetEffectiveDeviceReturnsCorrectDirtyFlags() {
    StubContext context = new StubContext();
    DeviceStateResolver resolver = new DeviceStateResolver(context);

    Device mockDevice = Mockito.mock(Device.class);
    State mockState = Mockito.mock(State.class);

    int flags = resolver.setEffectiveDevice(mockDevice, mockState);

    // It should return BOTH the device and device_state dirty flags
    assertEquals(CFG_DEVICE | CFG_DEVICE_STATE, flags);
    assertSame(mockDevice, resolver.getCachedDevice());
    assertSame(mockState, resolver.getCachedState());
  }

  @Test
  public void testSetDeviceStateNameReturnsDirtyFlag() {
    StubContext context = new StubContext();
    DeviceStateResolver resolver = new DeviceStateResolver(context);

    // First assignment should return the dirty flag
    int initialFlags = resolver.setDeviceStateName("Landscape");
    assertEquals(CFG_DEVICE_STATE, initialFlags);
    assertEquals("Landscape", resolver.getStateName());

    // Second identical assignment should return 0 (No change)
    int duplicateFlags = resolver.setDeviceStateName("Landscape");
    assertEquals("Should return 0 when the state name does not actually change", 0, duplicateFlags);
  }
}
