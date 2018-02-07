/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link FrameworkResourceRepository}.
 */
public class FrameworkResourceRepositoryTest extends AndroidTestCase {
  /** Enables printing of repository statistics. */
  private static final boolean PRINT_STATS = false;

  private File myResourceFolder;

  private void deleteRepositoryCache() {
    for (boolean withLocaleResources : new boolean[] {false, true}) {
      //noinspection ResultOfMethodCallIgnored
      FrameworkResourceRepository.getCacheFile(myResourceFolder, withLocaleResources).delete();
    }
  }

  /**
   * Returns the resource folder of the Android framework resources used by LayoutLib.
   */
  @NotNull
  private File getSdkResFolder() {
    IAndroidTarget androidTarget = getLayoutLibTarget(myModule);
    assertNotNull(androidTarget);
    String sdkPlatformPath = Files.simplifyPath(androidTarget.getLocation());
    return new File(sdkPlatformPath + "/data/res");
  }

  @NotNull
  private static IAndroidTarget getLayoutLibTarget(@NotNull Module module) {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(module);

    for (IAndroidTarget target : manager.getTargets()) {
      if (ConfigurationManager.isLayoutLibTarget(target)) {
        manager.setTarget(target);
        break;
      }
    }
    FolderConfiguration folderConfig = new FolderConfiguration();
    Configuration configuration = Configuration.create(manager, null, folderConfig);
    return configuration.getTarget();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myResourceFolder = getSdkResFolder();
    deleteRepositoryCache();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      //noinspection ResultOfMethodCallIgnored
      deleteRepositoryCache();
    } finally {
      super.tearDown();
    }
  }

  public void testLoading() throws Exception {
    File resFolder = getSdkResFolder();
    for (boolean withLocaleResources : new boolean[] {true, false}) {
      // Test loading without cache.
      long start = System.currentTimeMillis();
      if (PRINT_STATS) {
        FrameworkResourceRepository.create(resFolder, withLocaleResources, false);
      }
      long loadTimeWithoutCache = System.currentTimeMillis() - start;
      FrameworkResourceRepository repository = FrameworkResourceRepository.create(resFolder, withLocaleResources, true);
      assertFalse(repository.isLoadedFromCache());
      List<ResourceItem> resourceItems = repository.getAllResourceItems();
      Collections.sort(resourceItems);
      assertTrue("Too few resources: " + resourceItems.size(), resourceItems.size() >= 10000);
      for (ResourceItem item : resourceItems) {
        assertEquals(ResourceNamespace.ANDROID, item.getNamespace());
      }
      ImmutableMap<ResourceType, Integer> expectations = ImmutableMap.of(
          ResourceType.STYLE, 700,
          ResourceType.ATTR, 1200,
          ResourceType.DRAWABLE, 600,
          ResourceType.ID, 60,
          ResourceType.LAYOUT, 20
      );
      for (ResourceType type : ResourceType.values()) {
        Collection<ResourceItem> publicResources = repository.getPublicResourcesOfType(type);
        Integer minExpected = expectations.get(type);
        if (minExpected != null) {
          assertTrue("Too few public resources of type " + type.getName(), publicResources.size() >= minExpected);
        }
      }

      // Test loading from cache.
      start = System.currentTimeMillis();
      FrameworkResourceRepository repository2 = FrameworkResourceRepository.create(resFolder, withLocaleResources, true);
      long loadTimeWithCache = System.currentTimeMillis() - start;
      assertTrue(repository2.isLoadedFromCache());
      if (PRINT_STATS) {
        String type = withLocaleResources ? "Load time" : "Load time without locale resources";
        System.out.println(type + " without cache: " + loadTimeWithoutCache / 1000. + " sec, with cache " + loadTimeWithCache / 1000.
                           + " sec");
      }
      List<ResourceItem> resourceItems2 = repository2.getAllResourceItems();
      Collections.sort(resourceItems2);
      assertEquals(resourceItems.size(), resourceItems2.size());
      for (int i = 0; i < resourceItems.size(); i++) {
        ResourceItem withoutCache = resourceItems.get(i);
        ResourceItem withCache = resourceItems2.get(i);
        assertEquals("Different ResourceItem at position " + i, withoutCache, withCache);
        assertEquals("Different FolderConfiguration at position " + i, withoutCache.getConfiguration(), withCache.getConfiguration());
        assertEquals("Different ResourceValue at position " + i,
                     withoutCache.getResourceValue(), withCache.getResourceValue());
      }

      for (ResourceType type : ResourceType.values()) {
        List<ResourceItem> publicResources = new ArrayList<>(repository.getPublicResourcesOfType(type));
        List<ResourceItem> publicResources2 = new ArrayList<>(repository2.getPublicResourcesOfType(type));
        assertEquals("Number of public resources doesn't match for type " + type.getName(),
                     publicResources.size(), publicResources2.size());
        for (int i = 0; i < publicResources.size(); i++) {
          ResourceItem withoutCache = publicResources.get(i);
          ResourceItem withCache = publicResources2.get(i);
          assertEquals("Public resource difference at position " + i + " for type " + type.getName(), withoutCache, withCache);
        }
      }
    }
  }
}
