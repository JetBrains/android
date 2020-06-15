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
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import org.junit.Before;

public class InstantAppsTest extends AndroidGradleTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testFindBaseFeatureWithInstantApp() throws Exception {
    // Use a plugin version that supports instant app
    loadProject(INSTANT_APP, "instant-app", null, "3.5.0");
    assertEquals(myModules.getModule("feature"), findBaseFeature(myAndroidFacet));
  }

  public void testFindBaseFeatureWithoutInstantApp() throws Exception {
    loadProject(SIMPLE_APPLICATION, "app");
    assertNull(findBaseFeature(myAndroidFacet));
  }

  public void testGetDefaultInstantAppUrlWithInstantApp() throws Exception {
    // Use a plugin version that supports instant app
    loadProject(INSTANT_APP, "instant-app", null, "3.5.0");
    assertEquals("http://example.com/example", getDefaultInstantAppUrl(myAndroidFacet));
  }

  public void testGetDefaultInstantAppUrlWithoutInstantApp() throws Exception {
    loadProject(SIMPLE_APPLICATION, "app");
    assertEquals("", getDefaultInstantAppUrl(myAndroidFacet));
  }
}