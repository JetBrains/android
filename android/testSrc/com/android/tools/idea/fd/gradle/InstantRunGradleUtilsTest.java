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
package com.android.tools.idea.fd.gradle;

import com.android.SdkConstants;
import org.jetbrains.android.AndroidTestCase;

import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ENABLED;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_HAS_CODE;

public class InstantRunGradleUtilsTest extends AndroidTestCase {
  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  public void testAppHasCodeDefaultTrue() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_ENABLED + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertTrue(InstantRunGradleUtils.appHasCode(myFacet));
  }

  public void testAppHasCodeTrue() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_HAS_CODE + "/AndroidManifestHasCodeTrue.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertTrue(InstantRunGradleUtils.appHasCode(myFacet));
  }

  public void testAppHasCodeFalse() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_HAS_CODE + "/AndroidManifestHasCodeFalse.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertFalse(InstantRunGradleUtils.appHasCode(myFacet));
  }

  public void testAppManifestNoApplicationTag() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_HAS_CODE + "/AndroidManifestNoApplicationTag.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertTrue(InstantRunGradleUtils.appHasCode(myFacet));
  }
}