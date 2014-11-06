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
package com.android.tools.idea.sdk;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.AndroidTestCaseHelper;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Tests for {@link DefaultSdks}.
 */
public class DefaultSdksTest extends IdeaTestCase {
  private File myAndroidSdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AndroidTestCaseHelper.removeExistingAndroidSdks();
    myAndroidSdkPath = AndroidTestCaseHelper.getAndroidSdkPath();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        FacetManager facetManager = FacetManager.getInstance(myModule);
        ModifiableFacetModel model = facetManager.createModifiableModel();
        try {
          model.addFacet(facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null));
        } finally {
          model.commit();
        }
      }
    });
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    facet.getProperties().ALLOW_USER_CONFIGURATION = false;
  }

  public void testCreateAndroidSdksForAllTargets() {
    List<Sdk> sdks = DefaultSdks.createAndroidSdksForAllTargets(myAndroidSdkPath);
    assertOneSdkPerAvailableTarget(sdks);
  }

  public void testGetDefaultAndroidHome() {
    // Create default SDKs first.
    DefaultSdks.createAndroidSdksForAllTargets(myAndroidSdkPath);

    File androidHome = DefaultSdks.getDefaultAndroidHome();
    assertNotNull(androidHome);
    assertEquals(myAndroidSdkPath.getPath(), androidHome.getPath());
  }

  public void testGetEligibleAndroidSdks() {
    // Create default SDKs first.
    List<Sdk> sdks = DefaultSdks.createAndroidSdksForAllTargets(myAndroidSdkPath);

    List<Sdk> eligibleSdks = DefaultSdks.getEligibleAndroidSdks();
    assertEquals(sdks.size(), eligibleSdks.size());
  }

  public void testSetDefaultAndroidHomeUpdatingLocalPropertiesFile() throws IOException {
    LocalProperties localProperties = new LocalProperties(myProject);
    localProperties.setAndroidSdkPath("");
    localProperties.save();

    List<Sdk> sdks = DefaultSdks.setDefaultAndroidHome(myAndroidSdkPath, null);
    assertOneSdkPerAvailableTarget(sdks);

    localProperties = new LocalProperties(myProject);
    File androidSdkPath = localProperties.getAndroidSdkPath();
    assertNotNull(androidSdkPath);
    assertEquals(myAndroidSdkPath.getPath(), androidSdkPath.getPath());
  }

  private void assertOneSdkPerAvailableTarget(@NotNull List<Sdk> sdks) {
    List<IAndroidTarget> platformTargets = Lists.newArrayList();
    AndroidSdkData sdkData = AndroidSdkData.getSdkData(myAndroidSdkPath);
    assertNotNull(sdkData);
    for (IAndroidTarget target : sdkData.getTargets()) {
      if (target.isPlatform()) {
        platformTargets.add(target);
      }
    }

    assertEquals(platformTargets.size(), sdks.size());

    for (Sdk sdk : sdks) {
      SdkAdditionalData data = sdk.getSdkAdditionalData();
      assertNotNull(data);
      assertEquals(AndroidSdkAdditionalData.class, data.getClass());
      AndroidSdkAdditionalData androidSdkData = (AndroidSdkAdditionalData)data;
      AndroidPlatform androidPlatform = androidSdkData.getAndroidPlatform();
      assertNotNull(androidPlatform);
      IAndroidTarget target = androidPlatform.getTarget();
      platformTargets.remove(target);
    }

    assertEquals(0, platformTargets.size());
  }
}
