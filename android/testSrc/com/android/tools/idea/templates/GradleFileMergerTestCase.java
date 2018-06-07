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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.base.Charsets;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

/**
 * This is a base class for 2 tests that test methods.
 * Each test will supply its own version of {@link #mergeGradleFile}.
 */
@SuppressWarnings("unused")
public abstract class GradleFileMergerTestCase extends AndroidGradleTestCase {

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

  public void testDifferentConfigurationDependencies() throws Exception {
    checkFileMerge("templates/Base.gradle", "templates/TestDependencies.gradle", "templates/MergedTestDependencies.gradle");
    checkFileMerge("templates/MergedTestDependencies.gradle", "templates/NewDependencies.gradle", "templates/MergedTestAndNewDependencies.gradle");
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

  public void testBuildscriptMerge() throws Exception {
    checkFileMerge("templates/BaseToplevel.gradle",
                   "templates/ToplevelInject.gradle",
                   "templates/MergedToplevelInject.gradle");
  }

  public void testAddNewDependenciesBlock() throws Exception {
    checkFileMerge("templates/AddDependenciesBlock.gradle",
                   "templates/NewDependencies.gradle",
                   "templates/MergedDependenciesBlock.gradle");
  }

  public void testRemoveExistingDependencies() throws Exception {
    checkDependenciesRemoved("compile", "compile");
    checkDependenciesRemoved("compile", "implementation");
    checkDependenciesRemoved("compile", "api");
    checkDependenciesRemoved("compile", "feature");
    checkDependenciesRemoved("implementation", "compile");
    checkDependenciesRemoved("implementation", "implementation");
    checkDependenciesRemoved("implementation", "api");
    checkDependenciesRemoved("implementation", "feature");
    checkDependenciesRemoved("testCompile", "testImplementation");
    checkDependenciesRemoved("testCompile", "testApi");
    checkDependenciesRemoved("androidTestCompile", "androidTestImplementation");
    checkDependenciesRemoved("androidTestCompile", "androidTestApi");
  }

  private void checkFileMerge(@Nullable String destPath, @Nullable String srcPath, @Nullable String goldenPath) throws Exception {
    String source = "";
    String dest = "";
    String golden = "";
    if (destPath != null) {
      File destFile = new File(getTestDataPath(), FileUtil.toSystemDependentName(destPath));
      dest = Files.toString(destFile, Charsets.UTF_8);
    }

    if (srcPath != null) {
      File srcFile = new File(getTestDataPath(), FileUtil.toSystemDependentName(srcPath));
      source = Files.toString(srcFile, Charsets.UTF_8);
    }

    if (goldenPath != null) {
      File goldenFile = new File(getTestDataPath(), FileUtil.toSystemDependentName(goldenPath));
      golden = Files.toString(goldenFile, Charsets.UTF_8);
    }

    String result = mergeGradleFile(source, dest, getProject(), null);

    assertEquals(golden.replaceAll("\\s+","\n"), result.replaceAll("\\s+", "\n"));
  }

  private static void checkDependenciesRemoved(String dstConfigName, String srcConfigName) {
    String dependencyId = "com.android.support:appcompat-v7";
    GradleCoordinate dependencyCoordinate = GradleCoordinate.parseCoordinateString(dependencyId + ":23.1.1");

    Multimap<String, GradleCoordinate> dstConfigs = LinkedListMultimap.create();
    dstConfigs.put(dependencyId, dependencyCoordinate);
    Map<String, Multimap<String, GradleCoordinate>> dstAllConfigs = new HashMap<>();
    dstAllConfigs.put(dstConfigName, dstConfigs);

    Multimap<String, GradleCoordinate> srcConfigs = LinkedListMultimap.create();
    srcConfigs.put(dependencyId, dependencyCoordinate);
    Map<String, Multimap<String, GradleCoordinate>> srcAllConfigs = new HashMap<>();
    srcAllConfigs.put(srcConfigName, srcConfigs);

    GradleFileMergers.removeExistingDependencies(srcAllConfigs, dstAllConfigs);

    assertThat(dstConfigs).hasSize(1);
    assertThat(srcConfigs).hasSize(0);
  }
}
