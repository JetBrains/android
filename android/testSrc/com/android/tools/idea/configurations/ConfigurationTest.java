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

import com.android.ide.common.resources.configuration.*;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.configurations.ConfigurationListener.CFG_ACTIVITY;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_NIGHT_MODE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_THEME;

@SuppressWarnings("ConstantConditions")
public class ConfigurationTest extends AndroidTestCase {
  // The specific file doesn't matter; we're only using the destination folder
  private static final String TEST_FILE = "xmlpull/layout.xml";

  private int myChangeCount;
  private int myFlags;

  public void test() throws Exception {
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = facet.getConfigurationManager();
    assertNotNull(manager);
    assertSame(manager, facet.getConfigurationManager());

    Configuration configuration = Configuration.create(manager, null, new FolderConfiguration());
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
      assertSame(target, configuration.getRealTarget());
    }
    if (device != null) {
      assertSame(device, configuration.getDevice());
      assertNotNull(deviceState);
      assertSame(deviceState, configuration.getDeviceState());
    }

    FolderConfiguration fullConfig = configuration.getFullConfig();
    LocaleQualifier languageQualifier = fullConfig.getLocaleQualifier();
    String configDisplayString = fullConfig.toDisplayString();
    assertNotNull(configDisplayString, languageQualifier);
    assertEquals("en", languageQualifier.getLanguage());
    String region = fullConfig.getLocaleQualifier().getRegion();
    assertNotNull(configDisplayString, region);
    assertEquals("US", region);
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

    Configuration configuration = Configuration.create(manager, null, new FolderConfiguration());
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

  public void testLocaleSpecificFolder() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(TEST_FILE, "res/layout/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(TEST_FILE, "res/layout-no-rNO/layout1.xml");
    VirtualFile file3 = myFixture.copyFileToProject(TEST_FILE, "res/layout-no/layout1.xml");
    VirtualFile file4 = myFixture.copyFileToProject(TEST_FILE, "res/layout-xlarge-land/layout1.xml");
    myFixture.copyFileToProject(TEST_FILE, "res/layout-se/layout2.xml");

    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = facet.getConfigurationManager();
    assertNotNull(manager);
    assertSame(manager, facet.getConfigurationManager());

    // Default locale in the project: se
    manager.setLocale(Locale.create("se"));

    Configuration configuration1 = manager.getConfiguration(file1);
    /** This is not yet working; we need to sync all getLocale calls to getProject unless in a locale folder
    assertEquals(Locale.create("se"), configuration1.getLocale());
     */

    Configuration configuration2 = manager.getConfiguration(file2);
    assertEquals(Locale.create("no-rNO"), configuration2.getLocale());

    Configuration configuration3 = manager.getConfiguration(file3);
    assertEquals(Locale.create("no"), configuration3.getLocale());

    Configuration configuration4 = manager.getConfiguration(file4);
    assertEquals("Portrait", configuration1.getDeviceState().getName());
    assertEquals("Landscape", configuration4.getDeviceState().getName());
    assertEquals(ScreenSize.XLARGE, configuration4.getDevice().getDefaultHardware().getScreen().getSize());
  }

  public void testCreateSimilar() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(TEST_FILE, "res/layout/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(TEST_FILE, "res/layout-no-rNO/layout1.xml");
    VirtualFile file3 = myFixture.copyFileToProject(TEST_FILE, "res/layout-xlarge-land/layout1.xml");
    myFixture.copyFileToProject(TEST_FILE, "res/layout-en/layout2.xml");

    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = facet.getConfigurationManager();
    assertNotNull(manager);
    assertSame(manager, facet.getConfigurationManager());

    Configuration configuration1 = manager.getConfiguration(file1);
    configuration1.getConfigurationManager().setLocale(Locale.create("en"));
    configuration1.setTheme("Theme.Dialog");
    Device device = manager.getDevices().get(manager.getDevices().size() / 2 - 2);
    State state = device.getAllStates().get(device.getAllStates().size() - 1);
    configuration1.getConfigurationManager().selectDevice(device);

    configuration1.setDeviceStateName(state.getName());
    configuration1.save();

    Configuration configuration2 = manager.createSimilar(file2, file1);
    assertEquals(configuration1.getTheme(), configuration2.getTheme());
    Device device2 = configuration2.getDevice();
    assertEquals(configuration1.getDevice(), device2);
    assertEquals(Locale.create("no-rNO"), configuration2.getLocale());
    assertEquals(Locale.create("en"), configuration1.getLocale());

    State portrait = device.getState("Portrait");
    assertNotNull(portrait);
    configuration1.setDeviceState(portrait);

    Configuration configuration3 = manager.createSimilar(file3, file1);
    assertEquals(configuration1.getTheme(), configuration3.getTheme());
    assertNotSame(configuration3.getDeviceState(), portrait);
    assertEquals("Landscape", configuration3.getDeviceState().getName());
    assertEquals(ScreenSize.XLARGE, configuration3.getDevice().getDefaultHardware().getScreen().getSize());
    assertEquals(configuration1.getLocale(), configuration3.getLocale());
    // Ensure project-wide location switching works: both locales should update
    configuration1.getConfigurationManager().setLocale(Locale.create("no"));
    assertEquals(Locale.create("no"), configuration1.getLocale());
    assertEquals(configuration1.getLocale(), configuration3.getLocale());
  }

  public void testTargetSpecificFolder() throws Exception {
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = facet.getConfigurationManager();
    assertNotNull(manager);
    assertSame(manager, facet.getConfigurationManager());

    for (IAndroidTarget target : manager.getTargets()) {
      if (ConfigurationManager.isLayoutLibTarget(target)) {
        manager.setTarget(target);
        break;
      }
    }
    FolderConfiguration folderConfig = new FolderConfiguration();
    folderConfig.setVersionQualifier(new VersionQualifier(11));
    Configuration configuration = Configuration.create(manager, null, folderConfig);
    assertNotNull(configuration);
    IAndroidTarget target = configuration.getTarget();
    assertNotNull(target);
    assertTrue(target.getVersion().getFeatureLevel() >= 11);
  }
}
