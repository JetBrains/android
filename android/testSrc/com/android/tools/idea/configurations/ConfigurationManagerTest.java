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

import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import java.util.Arrays;
import java.util.List;

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

    List<Locale> locales = manager.getLocales();
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
    assertSame(myModule, manager.getModule());

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

    try {
      PlatformTestUtil.tryGcSoftlyReachableObjects();
    } catch (Throwable t) {
      // The above method can throw java.lang.OutOfMemoryError; that's fine for this test
    }
    System.gc();
    assertFalse(manager.hasCachedConfiguration(file1));
    assertFalse(manager.hasCachedConfiguration(file2));
  }
}
