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
package com.android.tools.idea.configurations;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

public class VaryingConfigurationTest extends AndroidTestCase {
  // The specific file doesn't matter; we're only using the destination folder
  private static final String TEST_FILE = "xmlpull/layout.xml";

  public void test() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(TEST_FILE, "res/layout/layout1.xml");
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule));

    Configuration parent = Configuration.create(manager, file, new FolderConfiguration());
    assertNotNull(parent);

    parent.startBulkEditing();
    parent.setDisplayName("myconfig");
    parent.setTheme("@style/Theme1");
    parent.setNightMode(NightMode.NIGHT);
    parent.setActivity("tes.tpkg.MyActivity1");
    parent.setUiMode(UiMode.TELEVISION);
    IAndroidTarget target = parent.getConfigurationManager().getTarget();
    Device device = parent.getConfigurationManager().getDefaultDevice();
    State deviceState = device != null ? device.getState("Portrait") : null;
    if (target != null) {
      parent.setTarget(target);
    }
    if (device != null) {
      parent.setDevice(device, false);
      assertNotNull(deviceState);
      parent.setDeviceState(deviceState);
    }
    parent.setLocale(Locale.create("en-rUS"));
    parent.finishBulkEditing();

    VaryingConfiguration configuration = VaryingConfiguration.create(parent);
    configuration.setAlternateUiMode(true);
    assertEquals(UiMode.TELEVISION, parent.getUiMode());
    assertEquals(UiMode.APPLIANCE, configuration.getUiMode());
    parent.setUiMode(UiMode.APPLIANCE);
    assertEquals(UiMode.WATCH, configuration.getUiMode());

    assertNotNull(device);
    State portrait = device.getState("Portrait");
    State landscape = device.getState("Landscape");
    assertNotNull(portrait);
    assertNotNull(landscape);
    configuration.setAlternateDeviceState(true);
    assertTrue(configuration.isAlternatingDeviceState());
    assertEquals(ScreenOrientation.LANDSCAPE, configuration.getFullConfig().getScreenOrientationQualifier().getValue());
    parent.setDeviceState(landscape);
    assertEquals(ScreenOrientation.PORTRAIT, configuration.getFullConfig().getScreenOrientationQualifier().getValue());
    assertEquals(portrait, configuration.getDeviceState());
    parent.setDeviceState(portrait);
    assertEquals(ScreenOrientation.LANDSCAPE, configuration.getFullConfig().getScreenOrientationQualifier().getValue());
    assertEquals(landscape, configuration.getDeviceState());
  }
}
