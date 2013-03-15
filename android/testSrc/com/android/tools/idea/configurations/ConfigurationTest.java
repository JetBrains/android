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
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.rendering.Locale;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.configurations.ConfigurationListener.CFG_ACTIVITY;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_NIGHT_MODE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_THEME;

public class ConfigurationTest extends AndroidTestCase {
  private int myChangeCount;
  private int myFlags;

  public void test() throws Exception {
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = facet.getConfigurationManager();
    assertNotNull(manager);
    assertSame(manager, facet.getConfigurationManager());

    Configuration configuration = Configuration.create(manager, new FolderConfiguration());
    assertNotNull(configuration);

    configuration.startBulkEditing();
    configuration.setDisplayName("myconfig");
    configuration.setTheme("@style/Theme1");
    configuration.setNightMode(NightMode.NIGHT);
    configuration.setActivity("tes.tpkg.MyActivity1");
    configuration.setUiMode(UiMode.TELEVISION);
    IAndroidTarget target = configuration.getConfigurationManager().getTarget();
    Device device = configuration.getConfigurationManager().getDefaultDevice();
    State deviceState = device != null ? device.getState("Portrait") : null;
    if (target != null) {
      configuration.setTarget(target);
    }
    if (device != null) {
      configuration.setDevice(device, false);
      assertNotNull(deviceState);
      configuration.setDeviceState(deviceState);
    }
    configuration.setLocale(Locale.create("en-rUS"));
    configuration.finishBulkEditing();

    assertEquals("myconfig", configuration.getDisplayName());
    assertEquals("@style/Theme1", configuration.getTheme());
    assertEquals(NightMode.NIGHT, configuration.getNightMode());
    assertEquals("tes.tpkg.MyActivity1", configuration.getActivity());
    assertEquals(UiMode.TELEVISION, configuration.getUiMode());
    assertEquals(Locale.create("en-rUS"), configuration.getLocale());
    if (target != null) {
      assertSame(target, configuration.getTarget());
    }
    if (device != null) {
      assertSame(device, configuration.getDevice());
      assertNotNull(deviceState);
      assertSame(deviceState, configuration.getDeviceState());
    }

    FolderConfiguration fullConfig = configuration.getFullConfig();
    LanguageQualifier languageQualifier = fullConfig.getLanguageQualifier();
    String configDisplayString = fullConfig.toDisplayString();
    assertNotNull(configDisplayString, languageQualifier);
    assertEquals("en", languageQualifier.getValue());
    RegionQualifier regionQualifier = fullConfig.getRegionQualifier();
    assertNotNull(configDisplayString, regionQualifier);
    assertEquals("US", regionQualifier.getValue());
    assertEquals(UiMode.TELEVISION, fullConfig.getUiModeQualifier().getValue());
    assertEquals(NightMode.NIGHT, fullConfig.getNightModeQualifier().getValue());
    assertEquals(ScreenOrientation.PORTRAIT, fullConfig.getScreenOrientationQualifier().getValue());

    if (device != null) {
      State landscape = device.getState("Landscape");
      assertNotNull(landscape);
      configuration.setDeviceState(landscape);
      assertEquals(ScreenOrientation.LANDSCAPE, configuration.getFullConfig().getScreenOrientationQualifier().getValue());
    }
  }

  public void testListener() throws Exception {
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = facet.getConfigurationManager();
    assertNotNull(manager);
    assertSame(manager, facet.getConfigurationManager());

    Configuration configuration = Configuration.create(manager, new FolderConfiguration());
    assertNotNull(configuration);

    ConfigurationListener listener = new ConfigurationListener() {
      @Override
      public boolean changed(int flags) {
        myFlags = flags;
        myChangeCount++;
        return true;
      }
    };
    configuration.addListener(listener);
    myChangeCount = 0;
    myFlags = 0;
    configuration.setTheme("@style/MyTheme");
    assertEquals(1, myChangeCount);
    assertEquals(CFG_THEME, myFlags);

    // No firing when no change:
    configuration.setTheme("@style/MyTheme");
    assertEquals(1, myChangeCount);
    assertEquals(CFG_THEME, myFlags);

    myChangeCount = 0;
    myFlags = 0;
    configuration.startBulkEditing();
    configuration.setTheme("@style/MyTheme2");
    assertEquals(0, myChangeCount);
    assertEquals(0, myFlags);

    configuration.setActivity("foo.bar.MyActivity");
    assertEquals(0, myChangeCount);
    assertEquals(0, myFlags);

    configuration.finishBulkEditing();
    assertEquals(1, myChangeCount);
    assertEquals(CFG_THEME | CFG_ACTIVITY, myFlags);

    myChangeCount = 0;
    myFlags = 0;
    configuration.startBulkEditing();
    configuration.setTheme("@style/MyTheme3");
    configuration.setActivity("foo.bar.MyActivity3");
    configuration.setNightMode(NightMode.NIGHT);
    configuration.finishBulkEditing();
    assertEquals(1, myChangeCount);
    assertEquals(CFG_THEME | CFG_ACTIVITY | CFG_NIGHT_MODE, myFlags);
  }
}
