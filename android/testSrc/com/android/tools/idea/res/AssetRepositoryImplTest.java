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

import com.android.ide.common.rendering.api.AssetRepository;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class AssetRepositoryImplTest extends AndroidGradleTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // Using the navigator project because it has assets
    loadProject(TestProjectPaths.NAVIGATOR_PACKAGEVIEW_SIMPLE);
    assertNotNull(myAndroidFacet);
  }

  public void testOpenAsset() throws IOException {
    AssetRepository repo = new AssetRepositoryImpl(myAndroidFacet);
    InputStream is = null;
    try {
      is = repo.openAsset("raw.asset.txt", 0);
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(is);
    is.close();

    is = null;
    try {
      is = repo.openAsset("missing.txt", 0);
    }
    catch (IOException ignored) {
    }
    assertNull(is);
  }

  public void testOpenNonAsset() throws IOException {
    File nonAssetFile = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/drawable-mdpi/ic_launcher.png"));
    File assetFile = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/assets/raw.asset.txt"));
    File nonExistingFile = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/drawable-mdpi/ic_launcher_invented.png"));
    assertTrue(nonAssetFile.isFile());
    assertFalse(nonExistingFile.isFile());

    AssetRepository repo = new AssetRepositoryImpl(myAndroidFacet);
    InputStream is = null;
    try {
      is = repo.openNonAsset(0, nonAssetFile.getAbsolutePath(), 0);
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(is);
    is.close();

    is = null;
    try {
      is = repo.openNonAsset(0, nonExistingFile.getAbsolutePath(), 0);
    }
    catch (IOException ignored) {
    }
    assertNull(is);

    try {
      // Opening assets should be done through the openAsset call. openNonAsset will fail for assets.
      is = repo.openNonAsset(0, assetFile.getAbsolutePath(), 0);
    }
    catch (IOException ignored) {
    }
    assertNull(is);
  }

}