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
package com.android.tools.idea.res;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.testFramework.UsefulTestCase.getTestName;

import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

/**
 * Tests for {@link StudioAssetFileOpener}.
 */
@RunsInEdt
public class StudioAssetFileOpenerTest {
  AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();
  @Rule
  public TestRule rule = RuleChain.outerRule(projectRule).around(new EdtRule());
  @Rule
  public TestName testName = new TestName();
  
  private StudioAssetFileOpener myAppRepo;
  private StudioAssetFileOpener myLibRepo;

  @Before
  public void setup() throws Exception {

    if ("aarAsset".equals(getTestName(testName.getMethodName(), true))) {
      // The testAarAsset project needs a different project configuration
      projectRule.loadProject(TestProjectPaths.LOCAL_AARS_AS_MODULES);
    }
    else {
      projectRule.loadProject(TestProjectPaths.DEPENDENT_MODULES);
    }
    AndroidFacet facet = projectRule.mainAndroidFacet(":app");
    assertThat(facet).isNotNull();
    myAppRepo = new StudioAssetFileOpener(facet);

    List<AndroidFacet> dependentFacets = AndroidDependenciesCache.getAllAndroidDependencies(facet.getModule(), false);
    if (dependentFacets.isEmpty()) {
      myLibRepo = null;
      return;
    }

    // The DEPENDENT_MODULES project, it only contains 1 dependent module called lib.
    assertThat(dependentFacets).hasSize(1);
    AndroidFacet libFacet = dependentFacets.get(0);
    assertThat(libFacet).isNotNull();

    myLibRepo = new StudioAssetFileOpener(libFacet);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testOpenAsset() throws IOException {
    // app/src/main/assets/app.asset.txt
    final String appContentInAppModule = "I am an asset in app module";
    // lib/src/main/assets/lib.asset.txt
    final String libContentInLibModule = "I am an asset in lib module";
    // app/src/main/assets/raw.asset.txt
    final String rawContentInAppModule = "I locate in app module";
    // lib/src/main/assets/raw.asset.txt
    final String rawContentInLibModule = "I locate in lib module";

    // test opening app.asset.txt, should find the asset
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myAppRepo.openAssetFile("app.asset.txt")))) {
      String assetContent = br.readLine();
      assertThat(assetContent).isEqualTo(appContentInAppModule);
    }

    // test opening lib.asset.txt in app module, should find the asset.
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myAppRepo.openAssetFile("lib.asset.txt")))) {
      String assetContent = br.readLine();
      assertThat(assetContent).isEqualTo(libContentInLibModule);
    }

    // test opening raw.asset.txt, the content should be the same as the one of app module
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myAppRepo.openAssetFile("raw.asset.txt")))) {
      String assetContent = br.readLine();
      assertThat(assetContent).isEqualTo(rawContentInAppModule);
    }

    // test opening raw.asset.txt in lib, the content should be the same as in the lib module
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myLibRepo.openAssetFile("raw.asset.txt")))) {
      String assetContent = br.readLine();
      assertThat(assetContent).isEqualTo(rawContentInLibModule);
    }

    // test opening app.asset.txt in lib, should not find the file.
    try (InputStream stream = myLibRepo.openAssetFile("app.asset.txt")) {
      assertThat(stream).isNull();
    }

    // test opening non-exist file
    try (InputStream stream = myAppRepo.openAssetFile("missing.txt")) {
      assertThat(stream).isNull();
    }
  }

  @Test
  public void testOpenNonAsset() throws IOException {
    File projectFolderPath = new File(projectRule.getProject().getBasePath());
    File imageFileInApp = new File(projectFolderPath, toSystemDependentName("app/src/main/res/drawable/app.png"));
    File imageFileInLib = new File(projectFolderPath, toSystemDependentName("lib/src/main/res/drawable/lib.png"));
    File nonAssetFileInApp = new File(projectFolderPath, toSystemDependentName("app/src/main/res/assets/app_asset.txt"));
    File nonAssetFileInLib = new File(projectFolderPath, toSystemDependentName("lib/src/main/res/assets/lib_asset.txt"));
    File nonExistingFile = new File(projectFolderPath, toSystemDependentName("app/src/main/res/drawable/non_existing.png"));
    File sampleDataPng = new File(projectFolderPath, toSystemDependentName("app/sampledata/test/sample.png"));

    assertThat(imageFileInApp.isFile()).isTrue();
    assertThat(imageFileInLib.isFile()).isTrue();
    assertThat(nonAssetFileInApp.isFile()).isTrue();
    assertThat(nonAssetFileInLib.isFile()).isTrue();
    assertThat(nonExistingFile.isFile()).isFalse();
    assertThat(sampleDataPng.isFile()).isTrue();

    // check can find app.png in app module
    try (InputStream stream = myAppRepo.openNonAssetFile(imageFileInApp.getAbsolutePath())) {
      assertThat(stream).isNotNull();
    }

    // check can find lib.png in app module
    try (InputStream stream = myAppRepo.openNonAssetFile(imageFileInLib.getAbsolutePath())) {
      assertThat(stream).isNotNull();
    }

    // check cannot find app.png in lib module
    try (InputStream stream = myLibRepo.openNonAssetFile(imageFileInApp.getAbsolutePath())) {
      assertThat(stream).isNull();
    }

    // check can find app_asset.txt in app module
    try (InputStream stream = myAppRepo.openNonAssetFile(nonAssetFileInApp.getAbsolutePath())) {
      assertThat(stream).isNotNull();
    }

    // check can find lib_asset.png in app module
    try (InputStream stream = myAppRepo.openNonAssetFile(nonAssetFileInLib.getAbsolutePath())) {
      assertThat(stream).isNotNull();
    }

    // check cannot find app_asset.png in lib module
    try (InputStream stream = myLibRepo.openNonAssetFile(nonAssetFileInApp.getAbsolutePath())) {
      assertThat(stream).isNull();
    }

    // check cannot find nonExistingFile in both module
    try (InputStream stream = myAppRepo.openNonAssetFile(nonExistingFile.getAbsolutePath())) {
      assertThat(stream).isNull();
    }

    // check can find sample data in app module
    try (InputStream stream = myAppRepo.openNonAssetFile(sampleDataPng.getAbsolutePath())) {
      assertThat(stream).isNotNull();
    }
  }

  @Test
  public void testAarAsset() throws IOException {
    try (InputStream stream = myAppRepo.openAssetFile("raw.txt")) {
      assertThat(stream).isNotNull();
    }
  }
}
