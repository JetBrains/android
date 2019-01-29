/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.activity;

import com.android.SdkConstants;
import com.android.ddmlib.IDevice;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.manifest.Manifest;

import static com.android.tools.idea.testing.TestProjectPaths.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultActivityLocator}.
 */
public class DefaultActivityLocatorTest extends AndroidTestCase {

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  public void testActivity() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("com.example.unittest.Launcher", DefaultActivityLocator.computeDefaultActivity(myFacet, null));
  }

  public void testActivityAlias() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("LauncherAlias", DefaultActivityLocator.computeDefaultActivity(myFacet, null));
  }

  // tests that when there are multiple activities that with action MAIN and category LAUNCHER, then give
  // preference to the one that also has category DEFAULT
  public void testPreferDefaultCategoryActivity() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_DEFAULT + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("com.example.unittest.LauncherAlias", DefaultActivityLocator.computeDefaultActivity(myFacet, null));
  }

  // tests that when there are multiple launcher activities, then we pick the leanback launcher for a TV device
  public void testLeanbackLauncher() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_TV + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_TV + "/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    myFixture.copyFileToProject(RUN_CONFIG_TV + "/DefaultLauncher.java",
                                "src/com/example/unittest/DefaultLauncher.java");
    myFixture.copyFileToProject(RUN_CONFIG_TV + "/TvLauncher.java",
                                "src/com/example/unittest/TvLauncher.java");

    IDevice tv = mock(IDevice.class);
    when(tv.supportsFeature(IDevice.HardwareFeature.TV)).thenReturn(true);
    assertEquals("com.example.unittest.TvLauncher", DefaultActivityLocator.computeDefaultActivity(myFacet, tv));

    IDevice device = mock(IDevice.class);
    when(tv.supportsFeature(IDevice.HardwareFeature.TV)).thenReturn(false);
    assertEquals("com.example.unittest.DefaultLauncher", DefaultActivityLocator.computeDefaultActivity(myFacet, device));
  }

  // tests that when there are multiple launcher activities, we exclude the ones with android:enabled="false"
  public void testEnabledActivities() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_ENABLED + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("LaunchActivity", DefaultActivityLocator.computeDefaultActivity(myFacet, null));

    // make sure that the dom based approach to getting values works as well
    final Manifest manifest = myFacet.getManifest();
    assertEquals("LaunchActivity", DefaultActivityLocator.getDefaultLauncherActivityName(myFacet.getModule().getProject(), manifest));
  }

  public void testLauncherActivityIntent() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_MANIFESTS + "/InvalidCategory.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertNull("No launchable activity registration is present in the manifest, but one was detected",
               DefaultActivityLocator.computeDefaultActivity(myFacet, null));
  }
}
