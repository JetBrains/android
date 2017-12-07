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
package org.jetbrains.android.inspections;

import com.android.SdkConstants;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

@SuppressWarnings("StatementWithEmptyBody")
public abstract class AndroidInspectionTestCase extends LightInspectionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    // Add Android facet such that API lookup becomes relevant
    if (AndroidFacet.getInstance(myModule) == null) {
      AndroidTestCase.addAndroidFacet(myModule);
      Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
      assertNotNull(sdk);
      @SuppressWarnings("SpellCheckingInspection") SdkModificator sdkModificator = sdk.getSdkModificator();
      ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
      sdkModificator.commitChanges();
    }
  }

  @Override
  protected void tearDown() throws Exception {
    AndroidFacet instance = AndroidFacet.getInstance(myModule);
    if (instance != null) {
      WriteAction.run(() -> {
        ModifiableFacetModel model = FacetManager.getInstance(myModule).createModifiableModel();
        model.removeFacet(instance);
        model.commit();
      });
    }
    super.tearDown();
  }

  protected void addManifest(int minApi) {
    myFixture.addFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML,
                               "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                               "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                               "    package=\"test.pkg.deprecation\">\n" +
                               "\n" +
                               "    <uses-sdk android:minSdkVersion=\"" + minApi + "\" />" +
                               "\n" +
                               "</manifest>\n");
  }
}
