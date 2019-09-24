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
package com.android.tools.idea.templates

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.templates.GradleFilePsiMerger.mergeGradleFiles
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.base.Charsets
import com.google.common.collect.LinkedListMultimap
import com.google.common.collect.Multimap
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import java.io.File

class GradleFileMergerTest : AndroidGradleTestCase() {
  fun testProjectDisposal() {
    val openProjects = ProjectManager.getInstance().openProjects
    checkFileMerge("Base.gradle", "NewFlavor.gradle", "MergedNewFlavor.gradle")
    val postMergeOpenProjects = ProjectManager.getInstance().openProjects
    assertThat(openProjects.size).isAtLeast(postMergeOpenProjects.size)
    if (postMergeOpenProjects.any {it.name == "MergingOnly"}) {
      fail()
    }
  }

  fun testInsertFlavor() =
    checkFileMerge("Base.gradle", "NewFlavor.gradle", "MergedNewFlavor.gradle")

  fun testInsertBuildType() =
    checkFileMerge("Base.gradle", "NewBuildType.gradle", "MergedNewBuildType.gradle")

  fun testMergeDependencies() =
    checkFileMerge("Base.gradle", "NewDependencies.gradle", "MergedNewDependencies.gradle")

  fun testMergeCloudDependencies() =
    checkFileMerge("Base.gradle", "CloudDependencies.gradle", "MergedCloudDependencies.gradle")

  fun testMergeCloudDependenciesDuplicate() =
    checkFileMerge("Base.gradle", "CloudDependenciesDuplicate.gradle", "MergedCloudDependenciesDuplicate.gradle")

  fun testMergeCloudDependenciesExclude() =
    checkFileMerge("BaseExclude.gradle", "CloudDependencies.gradle", "MergedCloudDependenciesExclude.gradle")

  fun testDifferentConfigurationDependencies() {
    checkFileMerge("Base.gradle", "TestDependencies.gradle", "MergedTestDependencies.gradle")
    checkFileMerge( "MergedTestDependencies.gradle", "NewDependencies.gradle", "MergedTestAndNewDependencies.gradle" )
  }

  fun testRemapFlavorAssetDir() =
    checkFileMerge("Base.gradle", "RemapFlavorAssetDir.gradle", "MergedRemapFlavorAssetDir.gradle")

  fun testRenameAndroidManifestFile() =
    checkFileMerge("Base.gradle", "RenameManifest.gradle", "MergedRenameManifest.gradle")

  fun testBuildscriptMerge() =
    checkFileMerge("BaseToplevel.gradle", "ToplevelInject.gradle", "MergedToplevelInject.gradle")

  fun testBuildscriptMergeIncludingSingleValueAssignment() {
    // To test the gradle files merge that include a single value assignment with the equal identifier
    checkFileMerge(
      "BaseToplevelIncludingSingleValueAssignment.gradle",
      "ToplevelInject.gradle",
      "MergedToplevelIncludingSingleValueAssignment.gradle"
    )
  }

  fun testAddNewDependenciesBlock() =
    checkFileMerge("AddDependenciesBlock.gradle", "NewDependencies.gradle", "MergedDependenciesBlock.gradle")

  fun testAddApplyPlugin() =
    checkFileMerge("Base.gradle", "AddApplyPlugin.gradle", "MergedApplyPlugin.gradle")

  fun testRemoveExistingDependencies() {
    checkDependenciesRemoved("compile", "compile")
    checkDependenciesRemoved("compile", "implementation")
    checkDependenciesRemoved("compile", "api")
    checkDependenciesRemoved("compile", "feature")
    checkDependenciesRemoved("implementation", "compile")
    checkDependenciesRemoved("implementation", "implementation")
    checkDependenciesRemoved("implementation", "api")
    checkDependenciesRemoved("implementation", "feature")
    checkDependenciesRemoved("testCompile", "testImplementation")
    checkDependenciesRemoved("testCompile", "testApi")
    checkDependenciesRemoved("androidTestCompile", "androidTestImplementation")
    checkDependenciesRemoved("androidTestCompile", "androidTestApi")
  }

  private fun checkFileMerge(destPath: String?, srcPath: String?, goldenPath: String?) {
    var source = ""
    var dest = ""
    var golden = ""
    if (destPath != null) {
      val destFile = File(getTestDataPath(), FileUtil.toSystemDependentName("templates/$destPath"))
      dest = Files.asCharSource(destFile, Charsets.UTF_8).read()
    }

    if (srcPath != null) {
      val srcFile = File(getTestDataPath(), FileUtil.toSystemDependentName("templates/$srcPath"))
      source = Files.asCharSource(srcFile, Charsets.UTF_8).read()
    }

    if (goldenPath != null) {
      val goldenFile = File(getTestDataPath(), FileUtil.toSystemDependentName("templates/$goldenPath"))
      golden = Files.asCharSource(goldenFile, Charsets.UTF_8).read()
    }

    val result = mergeGradleFiles(source, dest, project, null)

    assertEquals(golden.replace("\\s+".toRegex(), "\n"), result.replace("\\s+".toRegex(), "\n"))
  }

  private fun checkDependenciesRemoved(dstConfigName: String, srcConfigName: String) {
    val dependencyId = "com.android.support:appcompat-v7"
    val dependencyCoordinate = GradleCoordinate.parseCoordinateString("$dependencyId:23.1.1")

    val dstConfigs = LinkedListMultimap.create<String, GradleCoordinate>()
    dstConfigs.put(dependencyId, dependencyCoordinate)
    val dstAllConfigs = hashMapOf<String, Multimap<String, GradleCoordinate>>()
    dstAllConfigs[dstConfigName] = dstConfigs

    val srcConfigs = LinkedListMultimap.create<String, GradleCoordinate>()
    srcConfigs.put(dependencyId, dependencyCoordinate)
    val srcAllConfigs = hashMapOf<String, Multimap<String, GradleCoordinate>>()
    srcAllConfigs[srcConfigName] = srcConfigs

    updateExistingDependencies(srcAllConfigs, dstAllConfigs, null, null)

    assertThat(dstConfigs).hasSize(1)
    assertThat(srcConfigs).hasSize(0)
  }
}
