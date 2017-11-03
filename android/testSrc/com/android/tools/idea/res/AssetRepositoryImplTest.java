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

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import junit.framework.TestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;

import java.io.*;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class AssetRepositoryImplTest extends AndroidGradleTestCase {

  private AssetRepositoryImpl myAppRepo;
  private AssetRepositoryImpl myLibRepo;

  private static <T extends Closeable> void withCloseable(T closeable, ConsumerWithIOException<T> consumer) throws IOException {
    consumer.accept(closeable);

    if (closeable != null) {
      closeable.close();
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // The testAarAsset project needs a different project configuration
    loadProject("aarAsset".equals(getTestName(true)) ?
                TestProjectPaths.LOCAL_AARS_AS_MODULES :
                TestProjectPaths.DEPENDENT_MODULES);
    assertNotNull(myAndroidFacet);
    myAppRepo = new AssetRepositoryImpl(myAndroidFacet);


    List<AndroidFacet> depedentFacets = AndroidUtils.getAllAndroidDependencies(myAndroidFacet.getModule(), false);
    if (depedentFacets.isEmpty()) {
      myLibRepo = null;
      return;
    }

    // The DEPENDENT_MODULES project, it only contains 1 dependent module called lib.
    assertEquals(1, depedentFacets.size());
    AndroidFacet libFacet = depedentFacets.get(0);
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
    withCloseable(new BufferedReader(new InputStreamReader(myAppRepo.openAsset("app.asset.txt", 0))), br -> {
      String assetContent = br.readLine();
      assertEquals(appContentInAppModule, assetContent);
    });

    // test opening lib.asset.txt in app module, should find the asset.
    withCloseable(new BufferedReader(new InputStreamReader(myAppRepo.openAsset("lib.asset.txt", 0))), br -> {
      String assetContent = br.readLine();
      assertEquals(libContentInLibModule, assetContent);
    });

    // test opening raw.asset.txt, the content should be the same as the one of app module
    withCloseable(new BufferedReader(new InputStreamReader(myAppRepo.openAsset("raw.asset.txt", 0))), br -> {
      String assetContent = br.readLine();
      assertEquals(rawContentInAppModule, assetContent);
    });

    // test opening raw.asset.txt in lib, the content should be the same as in the lib module
    withCloseable(new BufferedReader(new InputStreamReader(myLibRepo.openAsset("raw.asset.txt", 0))), br -> {
      String assetContent = br.readLine();
      assertEquals(rawContentInLibModule, assetContent);
    });

    // test opening app.asset.txt in lib, should not find the file.
    withCloseable(myLibRepo.openAsset("app.asset.txt", 0), TestCase::assertNull);

    // test opening non-exist file
    withCloseable(myAppRepo.openAsset("missing.txt", 0), TestCase::assertNull);
  }

  public void testOpenNonAsset() throws IOException {
    File imageFileInApp = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/drawable/app.png"));
    File imageFileInLib = new File(getProjectFolderPath(), toSystemDependentName("lib/src/main/res/drawable/lib.png"));
    File nonAssetFileInApp = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/assets/app_asset.txt"));
    File nonAssetFileInLib = new File(getProjectFolderPath(), toSystemDependentName("lib/src/main/res/assets/lib_asset.txt"));
    File nonExistingFile = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/drawable/non_existing.png"));

    assertTrue(imageFileInApp.isFile());
    assertTrue(imageFileInLib.isFile());
    assertTrue(nonAssetFileInApp.isFile());
    assertTrue(nonAssetFileInLib.isFile());
    assertFalse(nonExistingFile.isFile());

    // check can find app.png in app module
    withCloseable(myAppRepo.openNonAsset(0, imageFileInApp.getAbsolutePath(), 0),
                  TestCase::assertNotNull);

    // check can find lib.png in app module
    withCloseable(myAppRepo.openNonAsset(0, imageFileInLib.getAbsolutePath(), 0),
                  TestCase::assertNotNull);

    // check cannot find app.png in lib module
    withCloseable(myLibRepo.openNonAsset(0, imageFileInApp.getAbsolutePath(), 0),
                  TestCase::assertNull);

    // check can find app_asset.txt in app module
    withCloseable(myAppRepo.openNonAsset(0, nonAssetFileInApp.getAbsolutePath(), 0),
                  TestCase::assertNotNull);

    // check can find lib_asset.png in app module
    withCloseable(myAppRepo.openNonAsset(0, nonAssetFileInLib.getAbsolutePath(), 0),
                  TestCase::assertNotNull);

    // check cannot find app_asset.png in lib module
    withCloseable(myLibRepo.openNonAsset(0, nonAssetFileInApp.getAbsolutePath(), 0),
                  TestCase::assertNull);

    // check cannot find nonExistingFile in both module
    withCloseable(myAppRepo.openNonAsset(0, nonExistingFile.getAbsolutePath(), 0),
                  TestCase::assertNull);
  }

  public void testAarAsset() throws IOException {
    withCloseable(myAppRepo.openAsset("raw.txt", 0),
                  TestCase::assertNotNull);
  }

  @SuppressWarnings("NonExceptionNameEndsWithException")
  @FunctionalInterface
  interface ConsumerWithIOException<T> {
    void accept(T t) throws IOException;
  }
}
