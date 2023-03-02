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

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Tests for {@link AssetRepositoryImpl}.
 */
public class  AssetRepositoryImplTest extends AndroidGradleTestCase {
  private AssetRepositoryImpl myAppRepo;
  private AssetRepositoryImpl myLibRepo;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // The testAarAsset project needs a different project configuration
    loadProject("aarAsset".equals(getTestName(true)) ?
                TestProjectPaths.LOCAL_AARS_AS_MODULES :
                TestProjectPaths.DEPENDENT_MODULES);
    AndroidFacet facet = AndroidFacet.getInstance(ModuleSystemUtil.getMainModule(getModule("app")));
    assertNotNull(facet);
    myAppRepo = new AssetRepositoryImpl(facet);


    List<AndroidFacet> dependentFacets = AndroidDependenciesCache.getAllAndroidDependencies(facet.getModule(), false);
    if (dependentFacets.isEmpty()) {
      myLibRepo = null;
      return;
    }

    // The DEPENDENT_MODULES project, it only contains 1 dependent module called lib.
    assertEquals(1, dependentFacets.size());
    AndroidFacet libFacet = dependentFacets.get(0);
    assertNotNull(libFacet);

    myLibRepo = new AssetRepositoryImpl(libFacet);
  }

  @SuppressWarnings("ConstantConditions")
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
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myAppRepo.openAsset("app.asset.txt", 0)))) {
      String assetContent = br.readLine();
      assertEquals(appContentInAppModule, assetContent);
    }

    // test opening lib.asset.txt in app module, should find the asset.
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myAppRepo.openAsset("lib.asset.txt", 0)))) {
      String assetContent = br.readLine();
      assertEquals(libContentInLibModule, assetContent);
    }

    // test opening raw.asset.txt, the content should be the same as the one of app module
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myAppRepo.openAsset("raw.asset.txt", 0)))) {
      String assetContent = br.readLine();
      assertEquals(rawContentInAppModule, assetContent);
    }

    // test opening raw.asset.txt in lib, the content should be the same as in the lib module
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myLibRepo.openAsset("raw.asset.txt", 0)))) {
      String assetContent = br.readLine();
      assertEquals(rawContentInLibModule, assetContent);
    }

    // test opening app.asset.txt in lib, should not find the file.
    try (InputStream stream = myLibRepo.openAsset("app.asset.txt", 0)) {
      assertNull(stream);
    }

    // test opening non-exist file
    try (InputStream stream = myAppRepo.openAsset("missing.txt", 0)) {
      assertNull(stream);
    }
  }

  public void testOpenNonAsset() throws IOException {
    File imageFileInApp = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/drawable/app.png"));
    File imageFileInLib = new File(getProjectFolderPath(), toSystemDependentName("lib/src/main/res/drawable/lib.png"));
    File nonAssetFileInApp = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/assets/app_asset.txt"));
    File nonAssetFileInLib = new File(getProjectFolderPath(), toSystemDependentName("lib/src/main/res/assets/lib_asset.txt"));
    File nonExistingFile = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/drawable/non_existing.png"));
    File sampleDataPng = new File(getProjectFolderPath(), toSystemDependentName("app/sampledata/test/sample.png"));

    assertTrue(imageFileInApp.isFile());
    assertTrue(imageFileInLib.isFile());
    assertTrue(nonAssetFileInApp.isFile());
    assertTrue(nonAssetFileInLib.isFile());
    assertFalse(nonExistingFile.isFile());
    assertTrue(sampleDataPng.isFile());

    // check can find app.png in app module
    try (InputStream stream = myAppRepo.openNonAsset(0, imageFileInApp.getAbsolutePath(), 0)) {
      assertNotNull(stream);
    }

    // check can find lib.png in app module
    try (InputStream stream = myAppRepo.openNonAsset(0, imageFileInLib.getAbsolutePath(), 0)) {
      assertNotNull(stream);
    }

    // check cannot find app.png in lib module
    try (InputStream stream = myLibRepo.openNonAsset(0, imageFileInApp.getAbsolutePath(), 0)) {
      assertNull(stream);
    }

    // check can find app_asset.txt in app module
    try (InputStream stream = myAppRepo.openNonAsset(0, nonAssetFileInApp.getAbsolutePath(), 0)) {
      assertNotNull(stream);
    }

    // check can find lib_asset.png in app module
    try (InputStream stream = myAppRepo.openNonAsset(0, nonAssetFileInLib.getAbsolutePath(), 0)) {
      assertNotNull(stream);
    }

    // check cannot find app_asset.png in lib module
    try (InputStream stream = myLibRepo.openNonAsset(0, nonAssetFileInApp.getAbsolutePath(), 0)) {
      assertNull(stream);
    }

    // check cannot find nonExistingFile in both module
    try (InputStream stream = myAppRepo.openNonAsset(0, nonExistingFile.getAbsolutePath(), 0)) {
      assertNull(stream);
    }

    // check can find sample data in app module
    try (InputStream stream = myAppRepo.openNonAsset(0, sampleDataPng.getAbsolutePath(), 0)) {
      assertNotNull(stream);
    }
  }

  public void testAarAsset() throws IOException {
    try (InputStream stream = myAppRepo.openAsset("raw.txt", 0)) {
      assertNotNull(stream);
    }
  }
}
