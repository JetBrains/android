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

import static com.android.tools.idea.configurations.ConfigurationListener.CFG_LOCALE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_UI_MODE;

public class NestedConfigurationTest extends AndroidTestCase {
  // The specific file doesn't matter; we're only using the destination folder
  private static final String TEST_FILE = "xmlpull/layout.xml";

  private int myChangeCount;
  private int myFlags;

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

    NestedConfiguration configuration = NestedConfiguration.create(parent);

    // Inherit locale
    assertFalse(configuration.isOverridingLocale());
    parent.setLocale(Locale.create("en-rUS"));
    assertEquals(Locale.create("en-rUS"), configuration.getLocale());
    parent.setLocale(Locale.create("de"));
    assertEquals(Locale.create("de"), configuration.getLocale());
    // Override locale
    configuration.setOverrideLocale(true);
    assertTrue(configuration.isOverridingLocale());
    configuration.setLocale(Locale.create("no"));
    assertEquals(Locale.create("no"), configuration.getLocale());
    parent.setLocale(Locale.create("es"));
    assertEquals(Locale.create("no"), configuration.getLocale());

    // Inherit UI mode
    assertFalse(configuration.isOverridingUiMode());
    parent.setUiMode(UiMode.DESK);
    assertSame(UiMode.DESK, configuration.getUiMode());
    parent.setUiMode(UiMode.APPLIANCE);
    assertSame(UiMode.APPLIANCE, configuration.getUiMode());
    // Override UI mode
    configuration.setOverrideUiMode(true);
    assertTrue(configuration.isOverridingUiMode());
    configuration.setUiMode(UiMode.CAR);
    assertSame(UiMode.CAR, configuration.getUiMode());
    parent.setUiMode(UiMode.DESK);
    assertSame(UiMode.CAR, configuration.getUiMode());

    // Inherit orientation
    assertNotNull(device);
    State portrait = device.getState("Portrait");
    State landscape = device.getState("Landscape");
    assertNotNull(portrait);
    assertNotNull(landscape);
    assertFalse(configuration.isOverridingDeviceState());
    assertEquals(ScreenOrientation.PORTRAIT, configuration.getFullConfig().getScreenOrientationQualifier().getValue());
    parent.setDeviceState(landscape);
    assertEquals(ScreenOrientation.LANDSCAPE, configuration.getFullConfig().getScreenOrientationQualifier().getValue());
    assertEquals(landscape, configuration.getDeviceState());
    parent.setDeviceState(portrait);
    assertEquals(portrait, configuration.getDeviceState());
    // Override orientation
    configuration.setOverrideDeviceState(true);
    assertTrue(configuration.isOverridingDeviceState());
    configuration.setDeviceState(landscape);
    assertEquals(landscape, configuration.getDeviceState());
    parent.setDeviceState(landscape);
    parent.setDeviceState(portrait);
    assertEquals(landscape, configuration.getDeviceState());

    // TODO: Inherit device -- with overridden state

    // TODO: Test listener; I should NOT fire when a parent changes an attribute I don't
    // care about!!
    // Also test that calling the setters are firing events, clear resources, etc

    // In order for this to work, I would need to have listeners attach and detach... How
    // can I do this?
  }

  public void testListener() throws Exception {
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule));

    Configuration parent = Configuration.create(manager, null, new FolderConfiguration());
    assertNotNull(parent);

    NestedConfiguration configuration = NestedConfiguration.create(parent);
    assertNotNull(configuration);

    ConfigurationListener listener = flags -> {
      myFlags = flags;
      myChangeCount++;
      return true;
    };
    configuration.addListener(listener);
    myChangeCount = 0;
    myFlags = 0;
    configuration.setOverrideLocale(true);
    configuration.setLocale(Locale.create("en-rUS"));
    assertEquals(1, myChangeCount);
    assertEquals(CFG_LOCALE, myFlags);

    // No firing when only seeing overridden changes
    myFlags = 0;
    parent.setLocale(Locale.create("es"));
    assertEquals(1, myChangeCount);
    assertEquals(0, myFlags);

    myFlags = 0;
    parent.setLocale(Locale.create("de"));
    parent.setUiMode(UiMode.APPLIANCE);
    assertEquals(2, myChangeCount);
    assertEquals(CFG_UI_MODE, myFlags);
  }
}
