/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run;

import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ACTIVITY;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_MULTIPROJECT;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;

/**
 * Tests for {@link GradleApplicationIdProvider}.
 */
public class GradleApplicationIdProviderTest extends GradleApplicationIdProviderTestCase {
  public void testGetPackageName() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    // See testData/Projects/runConfig/activity/build.gradle
    assertEquals("from.gradle.debug", provider.getPackageName());
    // Without a specific test package name from the Gradle file, we just get a test prefix.
    assertEquals("from.gradle.debug.test", provider.getTestPackageName());
  }

  public void testGetPackageNameForTest() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    // See testData/Projects/runConfig/activity/build.gradle
    assertEquals("from.gradle.debug", provider.getPackageName());
    // Without a specific test package name from the Gradle file, we just get a test prefix.
    assertEquals("from.gradle.debug.test", provider.getTestPackageName());
  }

  public void testGetPackageNameForTestOnlyModule() throws Exception {
    loadProject(TEST_ONLY_MODULE, "test");
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    assertEquals("com.example.android.app", provider.getPackageName());
    assertEquals("com.example.android.app.testmodule", provider.getTestPackageName());
  }

  public void testGetPackageNameForDynamicFeatureModule() throws Exception {
    loadProject(DYNAMIC_APP, "feature1");
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    assertEquals("google.simpleapplication", provider.getPackageName());
    assertEquals("com.example.feature1.test", provider.getTestPackageName());
  }

  public void testGetPackageNameForLibraryModule() throws Exception {
    loadProject(TEST_ARTIFACTS_MULTIPROJECT, "module2");
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    // Note that Android library module uses self-instrumenting APK meaning there is only an instrumentation APK.
    // So both getPackageName() and getTestPackageName() should return library's package name suffixed with ".test".
    assertEquals("com.example.test.multiproject.module2.test", provider.getPackageName());
    assertEquals("com.example.test.multiproject.module2.test", provider.getTestPackageName());
  }
}
