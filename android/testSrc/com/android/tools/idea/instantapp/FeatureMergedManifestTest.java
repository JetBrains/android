/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp;

import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP_LIBRARY_DEPENDENCY;

public class FeatureMergedManifestTest extends AndroidGradleTestCase {

  public void testLibraryManifestMergedOnFeature() throws Exception {
    loadProject(INSTANT_APP_LIBRARY_DEPENDENCY);
    Module featureModule = getModule("feature");
    MergedManifest mergedManifest = MergedManifest.get(featureModule);
    assertSize(1, mergedManifest.getActivities());
  }

  public void testCanFindURL() throws Exception {
    loadProject(INSTANT_APP_LIBRARY_DEPENDENCY);
    Module bundleModule = getModule("instantapp");
    AndroidFacet facet = AndroidFacet.getInstance(bundleModule);
    assertEquals("https://android.example.com/example", InstantApps.getDefaultInstantAppUrl(facet));
  }
}
