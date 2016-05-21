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
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.getEmbeddedJdkPath;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;

/**
 * Tests for {@link IdeSdks}.
 */
public class IdeSdksTest extends IdeaTestCase {
  private File myAndroidSdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AndroidTestCaseHelper.removeExistingAndroidSdks();
    myAndroidSdkPath = AndroidTestCaseHelper.getAndroidSdkPath();

    ApplicationManager.getApplication().runWriteAction(() -> {
      FacetManager facetManager = FacetManager.getInstance(myModule);

      ModifiableFacetModel model = facetManager.createModifiableModel();
      try {
        model.addFacet(facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null));
        model.addFacet(facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null));
      }
      finally {
        model.commit();
      }
    });
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    facet.getProperties().ALLOW_USER_CONFIGURATION = false;
  }

  public void testCreateAndroidSdkPerAndroidTarget() {
    List<Sdk> sdks = IdeSdks.createAndroidSdkPerAndroidTarget(myAndroidSdkPath);
    assertOneSdkPerAvailableTarget(sdks);
  }

  public void testGetAndroidSdkPath() {
    // Create default SDKs first.
    IdeSdks.createAndroidSdkPerAndroidTarget(myAndroidSdkPath);

    File androidHome = IdeSdks.getAndroidSdkPath();
    assertNotNull(androidHome);
    assertEquals(myAndroidSdkPath.getPath(), androidHome.getPath());
  }

  public void testGetEligibleAndroidSdks() {
    // Create default SDKs first.
    List<Sdk> sdks = IdeSdks.createAndroidSdkPerAndroidTarget(myAndroidSdkPath);

    List<Sdk> eligibleSdks = IdeSdks.getEligibleAndroidSdks();
    assertEquals(sdks.size(), eligibleSdks.size());
  }

  public void testSetAndroidSdkPathUpdatingLocalPropertiesFile() throws IOException {
    LocalProperties localProperties = new LocalProperties(myProject);
    localProperties.setAndroidSdkPath("");
    localProperties.save();

    List<Sdk> sdks =
      ApplicationManager.getApplication().runWriteAction((Computable<List<Sdk>>)() -> IdeSdks.setAndroidSdkPath(myAndroidSdkPath, null));
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
      AndroidPlatform androidPlatform = AndroidPlatform.getInstance(sdk);
      assertNotNull(androidPlatform);
      IAndroidTarget target = androidPlatform.getTarget();
      platformTargets.remove(target);
    }

    assertEquals(0, platformTargets.size());
  }

  public void testUseEmbeddedJdk() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      IdeSdks.shouldUseEmbeddedJdk(true);
    });

    // The path of the JDK should be the same as the embedded one.
    File jdkPath = IdeSdks.getJdkPath();
    assertNotNull(jdkPath);

    File embeddedJdkPath = getEmbeddedJdkPath();
    assertTrue(String.format("'%1$s' should be the embedded one ('%2$s')", jdkPath.getPath(), embeddedJdkPath.getPath()),
               filesEqual(jdkPath, embeddedJdkPath));
  }
}
