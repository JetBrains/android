/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage

import com.android.tools.idea.gradle.util.BuildMode
import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import org.junit.Test

class ExportSignedPackageWizardUnitTest {
  @Test
  fun testApkLocationCorrect() { // This test guarantees user is taken to the folder with the selected build type outputs
    Truth.assertThat(ExportSignedPackageWizard.getApkLocation("path/to/folder", "release").toString()).isEqualTo(
      FileUtil.toSystemDependentName("path/to/folder/release"))
  }

  @Test
  fun testGetTaskNamesFromSelectedVariantWithNoFlavors() {
    val tasks = ExportSignedPackageWizard.getTaskNamesFromSelectedVariant(listOf("release"), "debug", ":assembleDebug")
    Truth.assertThat(tasks).containsExactly(":assembleRelease")
  }

  @Test
  fun testGetTaskNamesFromSelectedVariantWithFlavors() {
    val tasks = ExportSignedPackageWizard.getTaskNamesFromSelectedVariant(mutableListOf("proX86Release", "freeArmRelease"), "proX86Debug",
                                                                          ":assembleProX86Debug")
    Truth.assertThat(tasks).containsExactly(":assembleProX86Release", ":assembleFreeArmRelease")
  }

  @Test
  fun testGetTaskNamesFromSelectedVariantWithBundleNoFlavors() {
    val tasks = ExportSignedPackageWizard.getTaskNamesFromSelectedVariant(listOf("release"), "debug", ":bundleDebug")
    Truth.assertThat(tasks).containsExactly(":bundleRelease")
  }

  @Test
  fun testGetTaskNamesFromSelectedVariantWithBundleFlavors() {
    val tasks = ExportSignedPackageWizard.getTaskNamesFromSelectedVariant(mutableListOf("proX86Release", "freeArmRelease"), "proX86Debug",
                                                                          ":bundleProX86Debug")
    Truth.assertThat(tasks).containsExactly(":bundleProX86Release", ":bundleFreeArmRelease")
  }

  @Test
  fun testReplaceVariantFromTask() {
    Truth.assertThat(ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantName", "oldVariantName", "flavorBuildType")).isEqualTo(
      ":flavorBuildType")
  }

  @Test
  fun testReplaceVariantFromTaskPre() {
    Truth.assertThat(ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantName", "oldVariantName", "flavorBuildType")).isEqualTo(
      ":prefixFlavorBuildType")
  }

  @Test
  fun testReplaceVariantFromTaskSuf() {
    Truth.assertThat(ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantNameSuffix", "oldVariantName", "flavorBuildType")).isEqualTo(
      ":flavorBuildTypeSuffix")
  }

  @Test
  fun testReplaceVariantFromTaskPreSuf() {
    Truth.assertThat(
      ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantNameSuffix", "oldVariantName", "flavorBuildType")).isEqualTo(
      ":prefixFlavorBuildTypeSuffix")
  }

  @Test
  fun testReplaceVariantFromTaskMissing() {
    Truth.assertThat(ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantName", "NonVariantName", "flavorBuildType")).isNull()
  }

  @Test
  fun testReplaceVariantFromTaskMissingPre() {
    Truth.assertThat(ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantName", "NonVariantName", "flavorBuildType")).isNull()
  }

  @Test
  fun testReplaceVariantFromTaskMissingSuf() {
    Truth.assertThat(ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantNameSuffix", "NonVariantName", "flavorBuildType")).isNull()
  }

  @Test
  fun testReplaceVariantFromTaskMissingPreSuf() {
    Truth.assertThat(
      ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantNameSuffix", "NonVariantName", "flavorBuildType")).isNull()
  }

  @Test
  fun testBuildModeFromApkTargetType() {
    Truth.assertThat(ExportSignedPackageWizard.getBuildModeFromTarget(ExportSignedPackageWizard.APK)).isEqualTo(BuildMode.ASSEMBLE)
  }

  @Test
  fun testBuildModeFromBundleTargetType() {
    Truth.assertThat(ExportSignedPackageWizard.getBuildModeFromTarget(ExportSignedPackageWizard.BUNDLE)).isEqualTo(BuildMode.BUNDLE)
  }
}