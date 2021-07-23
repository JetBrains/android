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

import static com.android.tools.idea.instantapp.InstantApps.findBaseFeature;
import static com.android.tools.idea.instantapp.InstantApps.getDefaultInstantAppUrl;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;

import com.android.testutils.junit4.OldAgpTest;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;

@OldAgpTest(agpVersions = "3.5.0", gradleVersions = "5.5")
public class InstantAppsTest extends AndroidGradleTestCase {

  public void testFindBaseFeatureWithInstantApp() throws Exception {
    loadProject(INSTANT_APP, "instant-app", "5.5", "3.5.0");
    assertEquals(TestModuleUtil.findModule(getProject(), "feature"), findBaseFeature(myAndroidFacet));
  }

  public void testGetDefaultInstantAppUrlWithInstantApp() throws Exception {
    // Use a plugin version that supports instant app
    loadProject(INSTANT_APP, "instant-app", "5.5", "3.5.0");
    assertEquals("http://example.com/example", getDefaultInstantAppUrl(myAndroidFacet));
  }
}