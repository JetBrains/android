/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.inspections;

import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.testFramework.InspectionTestCase;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

public class ResourceTypeInspectionTest extends InspectionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    //noinspection StatementWithEmptyBody
    if (getName().equals("testNotAndroid")) {
      // Don't add an Android facet here; we're testing that we're a no-op outside of Android projects
      // since the inspection is registered at the .java source type level
    } else {
      assertNotNull(myModule);
      String sdkPath = AndroidTestBase.getDefaultTestSdkPath();
      String platformDir = AndroidTestBase.getDefaultPlatformDir();
      final AndroidFacet facet = AndroidTestCase.addAndroidFacet(myModule, sdkPath, platformDir);
      assertNotNull(facet);
    }
  }

  @Override
  protected String getTestDataPath() {
    return AndroidTestBase.getTestDataPath() + "/inspections";
  }

  @Override
  protected Sdk getTestProjectSdk() {
    Sdk sdk = AndroidTestBase.createAndroidSdk(AndroidTestBase.getDefaultTestSdkPath(), AndroidTestBase.getDefaultPlatformDir());
    @SuppressWarnings("SpellCheckingInspection")
    SdkModificator sdkModificator = sdk.getSdkModificator();
    ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
    sdkModificator.commitChanges();
    return sdk;
  }

  private void doTest() throws Exception {
    doTest("resourceType/" + getTestName(true), new LocalInspectionToolWrapper(new ResourceTypeInspection()), "dummy");
  }

  public void testSimple() throws Exception {
    doTest();
  }

  public void testNotAndroid() throws Exception {
    doTest();
  }

  public void testFlow() throws Exception {
    doTest();
  }
}
