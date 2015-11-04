/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ddms;

import com.android.ddmlib.IDevice;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredText;
import org.easymock.EasyMock;
import org.junit.Test;

import static com.android.ddmlib.IDevice.*;
import static org.junit.Assert.*;

public final class DeviceRendererTest {
  private static IDevice createDevice(boolean isEmulator,
                                      String avdName,
                                      String manufacturer,
                                      String model,
                                      String buildVersion,
                                      String apiLevel,
                                      String serial,
                                      IDevice.DeviceState state) throws Exception {
    IDevice d = EasyMock.createMock(IDevice.class);
    EasyMock.expect(d.isEmulator()).andStubReturn(isEmulator);
    if (isEmulator) {
      EasyMock.expect(d.getAvdName()).andStubReturn(avdName);
    }
    else {
      EasyMock.expect(d.getProperty(PROP_DEVICE_MANUFACTURER)).andStubReturn(manufacturer);
      EasyMock.expect(d.getProperty(PROP_DEVICE_MODEL)).andStubReturn(model);
    }
    EasyMock.expect(d.getProperty(PROP_BUILD_VERSION)).andStubReturn(buildVersion);
    EasyMock.expect(d.getProperty(PROP_BUILD_API_LEVEL)).andStubReturn(apiLevel);
    EasyMock.expect(d.getSerialNumber()).andStubReturn(serial);
    EasyMock.expect(d.getState()).andStubReturn(state);
    EasyMock.expect(d.getName()).andStubReturn(manufacturer + model);
    EasyMock.replay(d);
    return d;
  }

  @Test
  public void deviceNameRendering1() throws Exception {
    String serial = "123";
    IDevice d = createDevice(false, null, "google", "nexus 4", "4.2", "17", serial, IDevice.DeviceState.ONLINE);
    SimpleColoredText target = new SimpleColoredText();
    DeviceRenderer.renderDeviceName(d, target);

    String name = target.toString();

    assertEquals("Google Nexus 4 Android 4.2, API 17", name);
    // status should be shown only if !online
    assertFalse(StringUtil.containsIgnoreCase(name, IDevice.DeviceState.ONLINE.toString()));
    // serial should be shown only if !online
    assertFalse(StringUtil.containsIgnoreCase(name, serial));
  }

  @Test
  public void deviceNameRendering2() throws Exception {
    String serial = "123";
    IDevice d = createDevice(true, "Avdname", "google", "nexus 4", "4.2", "17", serial, IDevice.DeviceState.BOOTLOADER);
    SimpleColoredText target = new SimpleColoredText();
    DeviceRenderer.renderDeviceName(d, target);

    String name = target.toString();
    assertFalse(StringUtil.containsIgnoreCase(name, "Nexus 4"));
    assertTrue(StringUtil.containsIgnoreCase(name, "Avdname"));
    assertTrue(StringUtil.containsIgnoreCase(name, IDevice.DeviceState.BOOTLOADER.toString()));
    assertTrue(StringUtil.containsIgnoreCase(name, serial));
  }
}
