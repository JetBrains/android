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
package com.android.tools.idea.model;

import static com.android.tools.idea.gradle.adtimport.GradleImport.CURRENT_COMPILE_VERSION;
import static com.android.tools.idea.testing.TestProjectPaths.MODULE_INFO_BOTH;
import static com.android.tools.idea.testing.TestProjectPaths.MODULE_INFO_FLAVORS;
import static com.android.tools.idea.testing.TestProjectPaths.MODULE_INFO_GRADLE_ONLY;
import static com.android.tools.idea.testing.TestProjectPaths.MODULE_INFO_MANIFEST_ONLY;

import com.android.tools.idea.testing.AndroidGradleTestCase;

public class StudioAndroidModuleInfoTest extends AndroidGradleTestCase {
  public void testManifestOnly() throws Exception {
    loadProject(MODULE_INFO_MANIFEST_ONLY);
    assertNotNull(myAndroidFacet);
    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(myAndroidFacet);
    assertEquals(1, androidModuleInfo.getMinSdkVersion().getApiLevel());
    assertEquals(18, androidModuleInfo.getTargetSdkVersion().getApiLevel());
    assertEquals("com.example.unittest", androidModuleInfo.getPackageName());
  }

  public void testGradleOnly() throws Exception {
    loadProject(MODULE_INFO_GRADLE_ONLY);
    assertNotNull(myAndroidFacet);
    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(myAndroidFacet);
    assertEquals(17, androidModuleInfo.getMinSdkVersion().getApiLevel());
    assertEquals(CURRENT_COMPILE_VERSION, androidModuleInfo.getTargetSdkVersion().getApiLevel());
    assertEquals("from.gradle", androidModuleInfo.getPackageName());
  }

  public void testBoth() throws Exception {
    loadProject(MODULE_INFO_BOTH);
    assertNotNull(myAndroidFacet);
    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(myAndroidFacet);
    assertEquals(17, androidModuleInfo.getMinSdkVersion().getApiLevel());
    assertEquals(CURRENT_COMPILE_VERSION, androidModuleInfo.getTargetSdkVersion().getApiLevel());
        assertEquals("from.gradle", androidModuleInfo.getPackageName());
  }

  public void testFlavors() throws Exception {
    loadProject(MODULE_INFO_FLAVORS);
    assertNotNull(myAndroidFacet);

    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(myAndroidFacet);
    assertEquals(14, androidModuleInfo.getMinSdkVersion().getApiLevel());
    assertEquals(CURRENT_COMPILE_VERSION, androidModuleInfo.getTargetSdkVersion().getApiLevel());
    assertEquals("com.example.free.debug", androidModuleInfo.getPackageName());
  }
}
