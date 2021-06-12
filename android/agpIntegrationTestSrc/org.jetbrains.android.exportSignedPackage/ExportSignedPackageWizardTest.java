/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage;

import static com.android.tools.idea.testing.TestProjectPaths.SIGNAPK_MULTIFLAVOR;
import static com.android.tools.idea.testing.TestProjectPaths.SIGNAPK_NO_FLAVORS;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.getTaskNamesFromSelectedVariant;

import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;

public class ExportSignedPackageWizardTest extends AndroidGradleTestCase {
  public void testNoFlavors() throws Exception {
    loadProject(SIGNAPK_NO_FLAVORS);
    IdeAndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // debug and release
    assertEquals(2, androidProject.getVariantNames().size());

    List<String> assembleTasks =
      ExportSignedPackageWizard.getGradleTasks("", getModel(), singletonList("release"), ExportSignedPackageWizard.APK);
    assertEquals(1, assembleTasks.size());
    assertEquals(":assembleRelease", assembleTasks.get(0));
  }

  public void testFlavors() throws Exception {
    loadProject(SIGNAPK_MULTIFLAVOR);
    IdeAndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // (free,pro) x (arm,x86) x (debug,release) = 8
    assertEquals(8, androidProject.getVariantNames().size());

    Set<String> assembleTasks = Sets.newHashSet(
      ExportSignedPackageWizard.getGradleTasks("", getModel(), asList("proX86Release", "freeArmRelease"), ExportSignedPackageWizard.APK));
    assertEquals(2, assembleTasks.size());
    assertTrue(assembleTasks.contains(":assembleProX86Release"));
    assertTrue(assembleTasks.contains(":assembleFreeArmRelease"));
  }

  public void testApkLocationCorrect() {
    // This test guarantees user is taken to the folder with the selected build type outputs
    assertEquals(toSystemDependentName("path/to/folder/release"),
                 ExportSignedPackageWizard.getApkLocation("path/to/folder", "release").toString());
  }

  public void testBundleNoFlavors() throws Exception {
    loadProject(SIGNAPK_NO_FLAVORS);
    IdeAndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // debug and release
    assertEquals(2, androidProject.getVariantNames().size());

    List<String> assembleTasks =
      ExportSignedPackageWizard.getGradleTasks("", getModel(), singletonList("release"), ExportSignedPackageWizard.BUNDLE);
    assertEquals(1, assembleTasks.size());
    assertEquals(":bundleRelease", assembleTasks.get(0));
  }

  public void testBundleFlavors() throws Exception {
    loadProject(SIGNAPK_MULTIFLAVOR);
    IdeAndroidProject androidProject = getModel().getAndroidProject();
    assertNotNull(androidProject);

    // (free,pro) x (arm,x86) x (debug,release) = 8
    assertEquals(8, androidProject.getVariantNames().size());

    Set<String> assembleTasks = Sets.newHashSet(ExportSignedPackageWizard
                                                  .getGradleTasks("", getModel(), asList("proX86Release", "freeArmRelease"),
                                                                  ExportSignedPackageWizard.BUNDLE));
    assertEquals(2, assembleTasks.size());
    assertTrue(assembleTasks.contains(":bundleProX86Release"));
    assertTrue(assembleTasks.contains(":bundleFreeArmRelease"));
  }

  public void testGetTaskNamesFromSelectedVariantWithNoFlavors() {
    List<String> tasks = getTaskNamesFromSelectedVariant(singletonList("release"), "debug", ":assembleDebug");
    assertEquals(1, tasks.size());
    assertEquals(":assembleRelease", tasks.get(0));
  }

  public void testGetTaskNamesFromSelectedVariantWithFlavors() {
    List<String> tasks = getTaskNamesFromSelectedVariant(asList("proX86Release", "freeArmRelease"), "proX86Debug", ":assembleProX86Debug");
    assertEquals(2, tasks.size());
    assertTrue(tasks.contains(":assembleProX86Release"));
    assertTrue(tasks.contains(":assembleFreeArmRelease"));
  }

  public void testGetTaskNamesFromSelectedVariantWithBundleNoFlavors() {
    List<String> tasks = getTaskNamesFromSelectedVariant(singletonList("release"), "debug", ":bundleDebug");
    assertEquals(1, tasks.size());
    assertEquals(":bundleRelease", tasks.get(0));
  }

  public void testGetTaskNamesFromSelectedVariantWithBundleFlavors() {
    List<String> tasks = getTaskNamesFromSelectedVariant(asList("proX86Release", "freeArmRelease"), "proX86Debug", ":bundleProX86Debug");
    assertEquals(2, tasks.size());
    assertTrue(tasks.contains(":bundleProX86Release"));
    assertTrue(tasks.contains(":bundleFreeArmRelease"));
  }

  public void testReplaceVariantFromTask() {
    assertEquals(":flavorBuildType",
                 ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantName", "oldVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskPre() {
    assertEquals(":prefixFlavorBuildType",
                 ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantName", "oldVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskSuf() {
    assertEquals(":flavorBuildTypeSuffix",
                 ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantNameSuffix", "oldVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskPreSuf() {
    assertEquals(":prefixFlavorBuildTypeSuffix",
                 ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantNameSuffix", "oldVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskMissing() {
    assertNull(ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantName", "NonVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskMissingPre() {
    assertNull(ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantName", "NonVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskMissingSuf() {
    assertNull(ExportSignedPackageWizard.replaceVariantFromTask(":oldVariantNameSuffix", "NonVariantName", "flavorBuildType"));
  }

  public void testReplaceVariantFromTaskMissingPreSuf() {
    assertNull(ExportSignedPackageWizard.replaceVariantFromTask(":prefixOldVariantNameSuffix", "NonVariantName", "flavorBuildType"));
  }
}
