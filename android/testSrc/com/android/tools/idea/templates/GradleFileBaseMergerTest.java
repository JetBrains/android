/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * This is a base class for 2 tests that test methods.
 * Each test will supply its own version of {@link #mergeGradleFile}.
 */
@SuppressWarnings("unused")
public abstract class GradleFileBaseMergerTest extends AndroidTestCase {

  /**
   * This method is implemented in the 2 actual tests: {@link GradleFilePsiMergerTest} and {@link GradleFileSimpleMergerTest}.
   */
  public abstract String mergeGradleFile(@NotNull String source, @NotNull String dest, @Nullable Project project,
                                         @Nullable final String supportLibVersionFilter);

  public void testProjectDisposal() throws Exception {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    checkFileMerge("templates/Base.gradle", "templates/NewFlavor.gradle", "templates/MergedNewFlavor.gradle");
    Project[] postMergeOpenProjects = ProjectManager.getInstance().getOpenProjects();
    assertFalse(postMergeOpenProjects.length > openProjects.length);
    for (Project p : postMergeOpenProjects) {
      if (p.getName().equals("MergingOnly")) {
        fail();
      }
    }
  }

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
    checkFileMerge("templates/Base.gradle", "templates/NewDependencies.gradle", "templates/MergedNewDependencies.gradle");
  }

  public void testMergeCloudDependencies() throws Exception {
    checkFileMerge("templates/Base.gradle", "templates/CloudDependencies.gradle", "templates/MergedCloudDependencies.gradle");
  }

  public void testMergeCloudDependenciesDuplicate() throws Exception {
    checkFileMerge("templates/Base.gradle", "templates/CloudDependenciesDuplicate.gradle",
                   "templates/MergedCloudDependenciesDuplicate.gradle");
  }

  public void testMergeCloudDependenciesExclude() throws Exception {
    checkFileMerge("templates/BaseExclude.gradle", "templates/CloudDependencies.gradle", "templates/MergedCloudDependenciesExclude.gradle");
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

  private void checkFileMerge(@Nullable String destPath, @Nullable String srcPath, @Nullable String goldenPath) throws Exception {
    String source = "";
    String dest = "";
    String golden = "";
    if (destPath != null) {
      File destFile = new File(getTestDataPath(), FileUtil.toSystemDependentName(destPath));
      dest = TemplateUtils.readTextFromDisk(destFile);
      assertNotNull(dest);
    }

    if (srcPath != null) {
      File srcFile = new File(getTestDataPath(), FileUtil.toSystemDependentName(srcPath));
      source = TemplateUtils.readTextFromDisk(srcFile);
      assertNotNull(source);
    }

    if (goldenPath != null) {
      File goldenFile = new File(getTestDataPath(), FileUtil.toSystemDependentName(goldenPath));
      golden = TemplateUtils.readTextFromDisk(goldenFile);
      assertNotNull(golden);
    }

    String result = mergeGradleFile(source, dest, getProject(), null);

    assertEquals(golden.replaceAll("\\s+","\n"), result.replaceAll("\\s+", "\n"));
  }
}
