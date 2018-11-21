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
package com.android.tools.idea.res.aar;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.annotations.NotNull;

/**
 * This test compares contents of {@link FrameworkResourceRepository} with and without
 * the {@link StudioFlags#LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR} flag. This test will be
 * removed after the {@link StudioFlags#LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR} flag is removed.
 */
public class FrameworkResourceRepositoryComparisonTest extends AndroidTestCase {
  /** Enables printing of repository loading statistics. */
  private static final boolean PRINT_STATS = false;

  private Path myResourceFolder;

  /**
   * Returns the resource folder of the Android framework resources used by LayoutLib.
   */
  @NotNull
  private Path getSdkResFolder() {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    IAndroidTarget target = manager.getHighestApiTarget();
    if (target == null) {
      fail();
    }
    CompatibilityRenderTarget compatibilityTarget = StudioEmbeddedRenderTarget.getCompatibilityTarget(target);
    return Paths.get(compatibilityTarget.getLocation(), "data", "res").normalize();
  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myResourceFolder = getSdkResFolder();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR.clearOverride();
    } finally {
      super.tearDown();
    }
  }

  public void testLoading() throws Exception {
    long loadTimeWithResourceMerger = 0;
    long loadTimeWithoutResourceMerger = 0;
    int count = PRINT_STATS ? 100 : 1;
    for (int i = 0; i < count; i++) {
      StudioFlags.LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR.override(false);
      long start = System.currentTimeMillis();
      FrameworkResourceRepository usingResourceMerger =
          FrameworkResourceRepository.create(myResourceFolder.toFile(), true, false);
      loadTimeWithResourceMerger += System.currentTimeMillis() - start;

      StudioFlags.LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR.override(true);
      start = System.currentTimeMillis();
      FrameworkResourceRepository withoutResourceMerger =
          FrameworkResourceRepository.create(myResourceFolder.toFile(), true, false);
      loadTimeWithoutResourceMerger += System.currentTimeMillis() - start;
      if (i == 0) {
        AarSourceResourceRepositoryComparisonTest.compareContents(usingResourceMerger, withoutResourceMerger);
      }
    }
    if (PRINT_STATS) {
      System.out.println("Load time with resource merger: " + loadTimeWithResourceMerger / (count * 1000.)
                         + " sec, without resource merger: " + loadTimeWithoutResourceMerger / (count * 1000.) + " sec");
    }
  }
}
