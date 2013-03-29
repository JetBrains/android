/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.utils.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import junit.framework.TestCase;
import org.easymock.EasyMock;

import java.util.List;

import static com.android.ddmlib.IDevice.*;

public class DevicePanelTest extends TestCase {
  public void testAppNameRendering() {
    verifyAppNameRendering("com.android.settings", "com.android.", "settings");
    verifyAppNameRendering("com.android.", "com.android.");
    verifyAppNameRendering("system_process", "system_process");
    verifyAppNameRendering("com.google.chrome:sandbox", "com.google.", "chrome:sandbox");
  }

  private void verifyAppNameRendering(String name, String... components) {
    List<Pair<String, SimpleTextAttributes>> c = DevicePanel.renderAppName(name);

    for (int i = 0; i < components.length; i++) {
      assertEquals(components[i], c.get(i).getFirst());
    }
  }

  public void testDeviceNameRendering1() throws Exception {
    String serial = "123";
    IDevice d = createDevice(false, null, "google", "nexus 4", "4.2", "17", serial,
                             DeviceState.ONLINE);
    List<Pair<String, SimpleTextAttributes>> c = DevicePanel.renderDeviceName(d);

    StringBuilder sb = new StringBuilder(100);
    for (int i = 0; i < c.size(); i++) {
      sb.append(c.get(i).getFirst());
    }

    String name = sb.toString();
    assertTrue(StringUtil.containsIgnoreCase(name, "Nexus 4"));
    // status should be shown only if !online
    assertFalse(StringUtil.containsIgnoreCase(name, DeviceState.ONLINE.toString()));
    // serial should be shown only if !online
    assertFalse(StringUtil.containsIgnoreCase(name, serial));
    assertTrue(StringUtil.containsIgnoreCase(name, "API 17"));
  }

  public void testDeviceNameRendering2() throws Exception {
    String serial = "123";
    IDevice d = createDevice(true, "Avdname", "google", "nexus 4", "4.2", "17", serial,
                             DeviceState.BOOTLOADER);
    List<Pair<String, SimpleTextAttributes>> c = DevicePanel.renderDeviceName(d);

    StringBuilder sb = new StringBuilder(100);
    for (int i = 0; i < c.size(); i++) {
      sb.append(c.get(i).getFirst());
    }

    String name = sb.toString();
    assertFalse(StringUtil.containsIgnoreCase(name, "Nexus 4"));
    assertTrue(StringUtil.containsIgnoreCase(name, "Avdname"));
    assertTrue(StringUtil.containsIgnoreCase(name, DeviceState.BOOTLOADER.toString()));
    assertTrue(StringUtil.containsIgnoreCase(name, serial));
  }

  private static IDevice createDevice(boolean isEmulator, String avdName,
                                      String manufacturer, String model,
                                      String buildVersion, String apiLevel,
                                      String serial, DeviceState state) throws Exception {
    IDevice d = EasyMock.createMock(IDevice.class);
    EasyMock.expect(d.isEmulator()).andStubReturn(isEmulator);
    if (isEmulator) {
      EasyMock.expect(d.getAvdName()).andStubReturn(avdName);
    } else {
      EasyMock.expect(d.getPropertyCacheOrSync(PROP_DEVICE_MANUFACTURER)).andStubReturn(manufacturer);
      EasyMock.expect(d.getPropertyCacheOrSync(PROP_DEVICE_MODEL)).andStubReturn(model);
    }
    EasyMock.expect(d.getPropertyCacheOrSync(PROP_BUILD_VERSION)).andStubReturn(buildVersion);
    EasyMock.expect(d.getPropertyCacheOrSync(PROP_BUILD_API_LEVEL)).andStubReturn(apiLevel);
    EasyMock.expect(d.getSerialNumber()).andStubReturn(serial);
    EasyMock.expect(d.getState()).andStubReturn(state);
    EasyMock.replay(d);
    return d;
  }
}
