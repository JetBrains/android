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

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.resources.Locale;
import com.android.sdklib.devices.Device;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ref.GCUtil;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.UsesFeature;
import org.jetbrains.android.facet.AndroidFacet;

public class ConfigurationManagerTest extends AndroidTestCase {
  public void testGetLocales() {
    myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml");
    myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-no-rNO/layout1.xml");
    myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-no/layout1.xml");
    myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-se/layout2.xml");

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule));

    ImmutableList<Locale> locales = manager.getLocalesInProject();
    assertEquals(Arrays.asList(Locale.create("no"), Locale.create("no-rNO"), Locale.create("se")), locales);
  }

  @SuppressWarnings("UnusedAssignment") // need to null out local vars before GC
  public void testCaching() {
    VirtualFile file1 = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-no-rNO/layout1.xml");

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule));

    Configuration configuration1 = manager.getConfiguration(file1);
    Configuration configuration2 = manager.getConfiguration(file2);
    assertNotSame(configuration1, configuration2);
    assertSame(configuration1, manager.getConfiguration(file1));
    assertSame(configuration2, manager.getConfiguration(file2));
    assertSame(file1, configuration1.getFile());
    assertSame(file2, configuration2.getFile());

    // GC test: Ensure that we keep a cache through the first GC, but not if
    // we nearly run out of memory:

    assertTrue(manager.hasCachedConfiguration(file1));
    assertTrue(manager.hasCachedConfiguration(file2));

    configuration1 = null;
    configuration2 = null;
    System.gc();
    assertTrue(manager.hasCachedConfiguration(file1));
    assertTrue(manager.hasCachedConfiguration(file2));

    int iterations = 0;
    do {
      // The amount of memory this method allocates since merging 181.3263.15 is not enough to collect soft references. Since this is the
      // only Android test that uses that, we just try a couple of times in a loop.
      GCUtil.tryGcSoftlyReachableObjects();
      iterations++;
    }
    while (manager.hasCachedConfiguration(file1) && iterations < 10);

    System.gc();
    assertFalse(manager.hasCachedConfiguration(file1));
    assertFalse(manager.hasCachedConfiguration(file2));
  }

  /**
   * Check that {@link ConfigurationManager#getConfiguration(VirtualFile)} does not need the read lock and will acquire it if needed.
   *
   * Regression test for b/162537840
   */
  public void testNoReadAction() throws ExecutionException, InterruptedException {
    VirtualFile file1 = myFixture.addFileToProject(
      "res/values/values.xml",
      "<resources>" +
      " <color name=\"myColor\">#FF00FF</color>" +
      "</resources>").getVirtualFile();
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(manager);

    // Populate the
    ImmutableList<Device> devices = manager.getDevices();
    // Our list of devices is pretty big, validity check that there is at least 5 for the test.
    assertTrue("The existing device list is expected to contain at least 5 devices.", devices.size() > 5);
    manager.selectDevice(devices.get(0));
    manager.selectDevice(devices.get(1));
    manager.selectDevice(devices.get(2));

    AppExecutorUtil.getAppExecutorService().submit(() -> {
      try {
        assertNotNull(manager.getConfiguration(file1));
      } catch (Throwable t) {
        fail("No exception expected calling ConfiguraitonManager#getConfiguration");
      }
    }).get();
  }

  public void testWearProjectUsesWearDeviceByDefault() {
    Manifest manifest = Manifest.getMainManifest(myFacet);
    assertNotNull(manifest);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      UsesFeature feature = manifest.addUsesFeature();
      feature.getName().setStringValue("android.hardware.type.watch");
    });

    PsiFile file = myFixture.addFileToProject("res/layout/layout.xml", LAYOUT_FILE_TEXT);
    Configuration config = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file.getVirtualFile());
    assertTrue(HardwareConfigHelper.isWear(config.getDevice()));
  }

  @Language("xml")
  private static final String LAYOUT_FILE_TEXT = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                 "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                 "  android:layout_width=\"match_parent\"\n" +
                                                 "  android:layout_height=\"match_parent\" />";
}
