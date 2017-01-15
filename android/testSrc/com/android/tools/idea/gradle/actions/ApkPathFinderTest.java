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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.util.io.FileUtil.*;

/**
 * Tests for {@link ApkPathFinder}.
 */
public class ApkPathFinderTest extends AndroidGradleTestCase {
  private ApkPathFinder myApkPathFinder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myApkPathFinder = new ApkPathFinder();
  }

  public void testFindExistingPathWithExistingPotentialPath() throws IOException {
    Module appModule = createModule("app");
    File potentialApPath = createTempDirectory("apkPath", "");
    File apkPath = myApkPathFinder.findExistingApkPath(appModule, potentialApPath.getPath());
    assertEquals(potentialApPath, apkPath);
  }

  public void testFindExistingPathWithDefaultPath() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    File defaultApkPath = new File(getBuildFolderPath(appModule), join("outputs", "apk"));
    ensureExists(defaultApkPath);

    File apkPath = myApkPathFinder.findExistingApkPath(appModule, "nonExistingPath");
    assertEquals(defaultApkPath, apkPath);
  }

  public void testFindExistingPathWithNonExistingPotentialAndDefaultPaths() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    File buildFolderPath = getBuildFolderPath(appModule);
    delete(buildFolderPath); // Ensure default APK path does not exist.
    ensureExists(buildFolderPath);

    File apkPath = myApkPathFinder.findExistingApkPath(appModule, "nonExistingPath");
    assertEquals(buildFolderPath, apkPath);
  }

  @NotNull
  private static File getBuildFolderPath(@NotNull Module module) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    assertNotNull(androidModel);
    return androidModel.getAndroidProject().getBuildFolder();
  }
}