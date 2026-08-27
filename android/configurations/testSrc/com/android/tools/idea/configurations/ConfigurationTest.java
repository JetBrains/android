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

import static com.android.tools.configurations.ConfigurationListener.CFG_ACTIVITY;
import static com.android.tools.configurations.ConfigurationListener.CFG_NIGHT_MODE;
import static com.android.tools.configurations.ConfigurationListener.CFG_THEME;

import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.Density;
import com.android.resources.LayoutDirection;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.configurations.Configuration;
import com.android.tools.configurations.ConfigurationListener;
import com.android.tools.layoutlib.AndroidTargets;
import com.android.tools.res.FrameworkOverlay;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

@SuppressWarnings("ConstantConditions")
public class ConfigurationTest extends AndroidTestCase {
  // The specific file doesn't matter; we're only using the destination folder
  private static final String TEST_FILE = "xmlpull/layout.xml";

  private int myChangeCount;
  private int myFlags;

  public void test() throws Exception {
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule));

    Configuration configuration = Configuration.create(manager, new FolderConfiguration());
    assertNotNull(configuration);

    configuration.startBulkEditing();
    configuration.setDisplayName("myconfig");
    configuration.setTheme("@style/Theme1");
    configuration.setNightMode(NightMode.NIGHT);
    configuration.setActivity("tes.tpkg.MyActivity1");
    configuration.setUiMode(UiMode.TELEVISION);
    IAndroidTarget target = configuration.getSettings().getTarget();
    Device device = configuration.getSettings().getDefaultDevice();
    State deviceState = device != null ? device.getState("Portrait") : null;
    if (target != null) {
      configuration.setTarget(target);
    }
    if (device != null) {
      configuration.setDevice(device, false);
      assertNotNull(deviceState);
      configuration.setDeviceState(deviceState);
      assertSame(deviceState, configuration.getCachedDeviceState());
    }
    configuration.setLocale(Locale.create("en-rUS"));
    configuration.finishBulkEditing();

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

    Density density = configuration.getDensity();
    assertEquals(Density.create(420), density);

    DensityQualifier qualifier = new DensityQualifier().getNullQualifier();
    configuration.getFullConfig().setDensityQualifier(qualifier);
    Density mediumDensity = configuration.getDensity();
    assertEquals(Density.MEDIUM, mediumDensity);
  }

  public void testListener() throws Exception {
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule));

    Configuration configuration = Configuration.create(manager, new FolderConfiguration());
    assertNotNull(configuration);

    ConfigurationListener listener = flags -> {
      myFlags = flags;
      myChangeCount++;
      return true;
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
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule));

    // Default locale in the project: se
    manager.setLocale(Locale.create("se"));

    Configuration configuration1 = manager.getConfiguration(file1);
    /* This is not yet working; we need to sync all getLocale calls to getProject unless in a locale folder
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

  public void _testCreateSimilar() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(TEST_FILE, "res/layout/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(TEST_FILE, "res/layout-no-rNO/layout1.xml");
    VirtualFile file3 = myFixture.copyFileToProject(TEST_FILE, "res/layout-xlarge-land/layout1.xml");
    myFixture.copyFileToProject(TEST_FILE, "res/layout-en/layout2.xml");

    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule));

    Configuration configuration1 = manager.getConfiguration(file1);
    configuration1.getSettings().setLocale(Locale.create("en"));
    configuration1.setTheme("Theme.Dialog");
    Device device = manager.getDevices().get(manager.getDevices().size() / 2 - 2);
    State state = device.getAllStates().get(device.getAllStates().size() - 1);
    configuration1.getSettings().selectDevice(device);

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
    configuration1.getSettings().setLocale(Locale.create("no"));
    assertEquals(Locale.create("no"), configuration1.getLocale());
    assertEquals(configuration1.getLocale(), configuration3.getLocale());
  }

  public void testTargetSpecificFolder() throws Exception {
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule));

    for (IAndroidTarget target : manager.getTargets()) {
      if (AndroidTargets.isLayoutLibTarget(target)) {
        manager.setTarget(target);
        break;
      }
    }
    FolderConfiguration folderConfig = new FolderConfiguration();
    folderConfig.setVersionQualifier(new VersionQualifier(11));
    Configuration configuration = Configuration.create(manager, folderConfig);
    assertNotNull(configuration);
    IAndroidTarget target = configuration.getTarget();
    assertNotNull(target);
    assertTrue(target.getVersion().getFeatureLevel() >= 11);
  }

  public void testRtlFromLocale() {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = Configuration.create(manager, new FolderConfiguration());

    LayoutDirectionQualifier layoutDirectionQualifier = configuration.getFullConfig().getLayoutDirectionQualifier();
    assertNotNull(layoutDirectionQualifier);
    assertEquals(LayoutDirection.LTR, layoutDirectionQualifier.getValue());

    configuration.setLocale(Locale.create("ar"));
    LayoutDirectionQualifier rtlQualifier = configuration.getFullConfig().getLayoutDirectionQualifier();
    assertNotNull(rtlQualifier);
    assertEquals(LayoutDirection.RTL, rtlQualifier.getValue());

    configuration.setLocale(Locale.create("fr"));
    LayoutDirectionQualifier ltrQualifier = configuration.getFullConfig().getLayoutDirectionQualifier();
    assertNotNull(ltrQualifier);
    assertEquals(LayoutDirection.LTR, ltrQualifier.getValue());
  }


  public void testConfigurationClone() {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = Configuration.create(manager, new FolderConfiguration());
    configuration.setActivity("Activity");
    configuration.setDisplayName("DisplayName");

    Configuration clone = configuration.clone();

    configuration.setActivity("Activity2");
    configuration.setDisplayName("DisplayName2");

    assertEquals("Activity2", configuration.getActivity());
    assertEquals("DisplayName2", configuration.getDisplayName());

    assertEquals("Activity", clone.getActivity());
    assertEquals("DisplayName", clone.getDisplayName());
  }

  public void testConfigurationForFileClone() {
    VirtualFile file1 = myFixture.copyFileToProject(TEST_FILE, "res/layout/layout1.xml");

    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    ConfigurationForFile configuration = manager.getConfiguration(file1);
    configuration.setActivity("Activity");
    configuration.setDisplayName("DisplayName");

    ConfigurationForFile clone = configuration.clone();

    configuration.setActivity("Activity2");
    configuration.setDisplayName("DisplayName2");

    assertEquals("Activity2", configuration.getActivity());
    assertEquals("DisplayName2", configuration.getDisplayName());
    assertEquals(file1, configuration.getFile());

    assertEquals("Activity", clone.getActivity());
    assertEquals("DisplayName", clone.getDisplayName());
    assertEquals(file1, clone.getFile());
  }

  /**
   * Check that setDevice can restore the original configuration after a custom configuration has been
   * derived and set.
   */
  public void testCustomConfiguration() {
    VirtualFile file1 = myFixture.copyFileToProject(TEST_FILE, "res/layout/layout1.xml");

    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    ConfigurationForFile configuration = manager.getConfiguration(file1);
    Device original = configuration.getDevice();
    Device.Builder builder = new Device.Builder(original);
    builder.setName("Custom");
    builder.setId(Configuration.CUSTOM_DEVICE_ID);
    Device customDevice = builder.build();
    customDevice.getAllStates().forEach(state -> {
      Screen screen = state.getHardware().getScreen();
      screen.setXDimension(100);
      screen.setYDimension(100);
    });
    configuration.setDevice(original, false);
    configuration.setEffectiveDevice(customDevice, null);

    configuration.setDevice(original, false);
    assertEquals(original, configuration.getDevice());
  }

  public void testOverlaysIntegration() {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = Configuration.create(manager, new FolderConfiguration());

    // 1. Test System UI interaction (Gesture Nav)
    assertTrue(configuration.getOverlays().contains(FrameworkOverlay.NAV_GESTURE));
    configuration.setGestureNav(false);
    assertTrue(configuration.getOverlays().contains(FrameworkOverlay.NAV_3_BUTTONS));

    // 2. Test Device -> System UI interaction (Cutout Overlay)
    Device device = manager.getDeviceById("pixel_6");
    if (device != null) {
      configuration.setDevice(device, false);
      // Verify that the configuration correctly resolved the PIXEL_6 overlay based on the device ID
      assertTrue(configuration.getOverlays().contains(FrameworkOverlay.PIXEL_6));
    }
  }

  public void testModificationCount() {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = Configuration.create(manager, new FolderConfiguration());
    long initialCount = configuration.getModificationCount();

    // Test Environment Neighborhood
    configuration.setTheme("@style/Theme.AppCompat");
    long afterThemeCount = configuration.getModificationCount();
    assertTrue(afterThemeCount > initialCount);

    // Test UI Mode Neighborhood
    configuration.setNightMode(NightMode.NIGHT);
    long afterNightModeCount = configuration.getModificationCount();
    assertTrue(afterNightModeCount > afterThemeCount);

    // Test System UI Neighborhood
    configuration.setFontScale(1.5f);
    assertTrue(configuration.getModificationCount() > afterNightModeCount);
  }

  public void testSettingsStateVersionTriggersSync() {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = Configuration.create(manager, new FolderConfiguration());

    // Access full config once to clear dirty flags
    configuration.getFullConfig();

    // Verify that getFullConfig() triggers a sync if the state version has changed.
    // We can increment it by changing a project-wide setting like locale.
    int initialVersion = manager.getStateVersion();
    manager.setLocale(Locale.create("fr"));
    assertTrue(manager.getStateVersion() > initialVersion);

    // This should trigger a sync and the new locale should be reflected in the full config
    assertEquals("fr", configuration.getFullConfig().getLocaleQualifier().getLanguage());
  }

}
