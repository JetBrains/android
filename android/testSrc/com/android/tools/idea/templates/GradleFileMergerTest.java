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
package com.android.tools.idea.templates;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;

/**
 * Test gradle merging by PSI tree implementation
 */
public class GradleFileMergerTest extends AndroidTestCase {


  public void testInsertFlavor() throws Exception {
    checkFileMerge("templates/Base.gradle",
                   "templates/NewFlavor.gradle",
                   "templates/MergedNewFlavor.gradle");
  }

  public void testInsertBuildType() throws Exception {
    checkFileMerge("templates/Base.gradle",
                   "templates/NewBuildType.gradle",
                   "templates/MergedNewBuildType.gradle");
  }

  public void testMergeDependencies() throws Exception {
    checkFileMerge("templates/Base.gradle",
                   "templates/NewDependencies.gradle",
                   "templates/MergedNewDependencies.gradle");
  }

  public void testRemapFlavorAssetDir() throws Exception {
    checkFileMerge("templates/Base.gradle",
                   "templates/RemapFlavorAssetDir.gradle",
                   "templates/MergedRemapFlavorAssetDir.gradle");
  }

  public void testRenameAndroidManifestFile() throws Exception {
    checkFileMerge("templates/Base.gradle",
                   "templates/RenameManifest.gradle",
                   "templates/MergedRenameManifest.gradle");
  }

  private void checkFileMerge(String destPath, String srcPath, String goldenPath) throws Exception {
    File destFile = new File(getTestDataPath(), FileUtil.toSystemDependentName(destPath));
    File srcFile = new File(getTestDataPath(), FileUtil.toSystemDependentName(srcPath));
    File goldenFile = new File(getTestDataPath(), FileUtil.toSystemDependentName(goldenPath));
    String source = TemplateUtils.readTextFile(srcFile);
    String dest = TemplateUtils.readTextFile(destFile);
    String golden = TemplateUtils.readTextFile(goldenFile);
    assertNotNull(source);
    assertNotNull(dest);
    assertNotNull(golden);
    assertEquals(golden.replaceAll("\\s+","\n"),
                 GradleFileMerger.mergeGradleFiles(source, dest, getProject()).replaceAll("\\s+", "\n"));
  }
}
